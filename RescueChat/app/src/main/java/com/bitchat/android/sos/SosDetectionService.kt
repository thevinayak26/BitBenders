package com.bitchat.android.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.bitchat.android.R
import com.bitchat.android.service.MeshServiceHolder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.sqrt

class SosDetectionService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "rescuemesh_sos_detection"
        private const val NOTIFICATION_ID = 41001

        const val ACTION_MANUAL_SOS = "com.bitchat.android.sos.ACTION_MANUAL_SOS"
        const val ACTION_CANCEL_SOS = "com.bitchat.android.sos.ACTION_CANCEL_SOS"
        const val ACTION_INCOMING_SOS = "com.bitchat.android.sos.ACTION_INCOMING_SOS"
        const val ACTION_INCOMING_ACK = "com.bitchat.android.sos.ACTION_INCOMING_ACK"
        const val EXTRA_JSON = "json"

        const val IMPACT_THRESHOLD = 25f
        const val FALL_THRESHOLD = 2f
        const val COOLDOWN_MS = 30_000L

        fun start(context: Context, action: String? = null, jsonPayload: String? = null) {
            val intent = Intent(context, SosDetectionService::class.java).apply {
                this.action = action
                if (!jsonPayload.isNullOrBlank()) {
                    putExtra(EXTRA_JSON, jsonPayload)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun getTtl(type: SosType, battery: Int): Int = when {
            type == SosType.MANUAL -> 10
            type == SosType.CRASH -> 8
            battery < 20 -> 5
            else -> 7
        }
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var sosManager: SosManager
    private val httpClient = OkHttpClient()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastSosTime = 0L
    private var lastLocation: android.location.Location? = null
    private var isInFreefall = false
    private var locationCallback: LocationCallback? = null
    private var ackPollingJob: Job? = null
    private val seenServerAckIds = mutableSetOf<String>()
    private val recentMagnitudes = ArrayDeque<Float>()
    private val recentSpikesMs = ArrayDeque<Long>()

    private val nodeId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sosManager = SosManager()

        sosManager.onBroadcastSos = { packet ->
            SosStateStore.setLatestSos(packet)
            MeshServiceHolder.meshService?.sendMessage("${SosMessageBridge.SOS_PREFIX}${packet.toJson()}")
            if (SosStateStore.uiState.value.isGatewayMode) {
                startService(
                    Intent(this, GatewayBridgeService::class.java)
                        .putExtra(GatewayBridgeService.EXTRA_SOS_JSON, packet.toJson())
                )
            }
        }
        sosManager.onBroadcastAck = { ack ->
            MeshServiceHolder.meshService?.sendMessage("${SosMessageBridge.ACK_PREFIX}${ack.toJson()}")
        }
        sosManager.onSosConfirmed = { ack ->
            SosStateStore.markConfirmed(ack.rescuerId)
        }
        
        serviceScope.launch {
            sosManager.state.collect { state ->
                when (state) {
                    SosState.SENDING, SosState.WAITING_ACK -> SosStateStore.setState(SosState.WAITING_ACK)
                    SosState.CONFIRMED -> SosStateStore.setState(SosState.CONFIRMED)
                    SosState.IDLE -> SosStateStore.setState(SosState.IDLE)
                }
            }
        }

        setupWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        registerAccelerometer()
        startAckPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MANUAL_SOS -> triggerManualSos()
            ACTION_CANCEL_SOS -> sosManager.cancelSos()
            ACTION_INCOMING_SOS -> {
                val json = intent.getStringExtra(EXTRA_JSON)
                if (!json.isNullOrBlank()) {
                    runCatching { SosPacket.fromJson(json) }
                        .recoverCatching { SosPacket.fromWireFormat(json) }
                        .onSuccess {
                            SosStateStore.setLatestSos(it)
                            sosManager.onSosReceived(it, nodeId)
                        }
                }
            }
            ACTION_INCOMING_ACK -> {
                val json = intent.getStringExtra(EXTRA_JSON)
                if (!json.isNullOrBlank()) {
                    runCatching { AckPacket.fromJson(json) }
                        .recoverCatching { AckPacket.fromWireFormat(json) }
                        .onSuccess { sosManager.onAckReceived(it, nodeId) }
                }
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "RescueMesh Emergency Detection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Monitoring crash/fall events")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun setupWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RescueMesh:SosDetection")
        wakeLock.acquire()
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                lastLocation = result.lastLocation
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback as LocationCallback, mainLooper)
        } catch (_: SecurityException) {
        }
    }

    private fun registerAccelerometer() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )

        if (magnitude < FALL_THRESHOLD) isInFreefall = true
        updateDisasterPrediction(magnitude)

        if (magnitude > IMPACT_THRESHOLD) {
            val type = if (isInFreefall) SosType.FALL else SosType.CRASH
            isInFreefall = false
            triggerAutoSos(type)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    fun triggerManualSos() {
        fireSos(SosType.MANUAL)
    }

    private fun triggerAutoSos(type: SosType) {
        val now = System.currentTimeMillis()
        if (now - lastSosTime < COOLDOWN_MS) return
        lastSosTime = now
        fireSos(type)
    }

    private fun fireSos(type: SosType) {
        val lat = lastLocation?.latitude ?: 28.6139
        val lon = lastLocation?.longitude ?: 77.2090
        val battery = getBatteryLevel()
        val packet = SosPacket(
            nodeId = nodeId,
            latitude = lat,
            longitude = lon,
            batteryLevel = battery,
            sosType = type,
            ttl = getTtl(type, battery)
        )
        SosStateStore.setLatestSos(packet)
        sosManager.triggerSos(packet)
    }

    private fun updateDisasterPrediction(magnitude: Float) {
        if (recentMagnitudes.size >= 40) recentMagnitudes.removeFirst()
        recentMagnitudes.addLast(magnitude)

        val now = System.currentTimeMillis()
        if (magnitude > 18f) recentSpikesMs.addLast(now)
        while (recentSpikesMs.isNotEmpty() && now - recentSpikesMs.first() > 20_000L) {
            recentSpikesMs.removeFirst()
        }

        val sample = recentMagnitudes.toList()
        val mean = sample.average().toFloat()
        val variance = if (sample.isNotEmpty()) {
            sample.map { (it - mean).pow(2) }.average().toFloat()
        } else {
            0f
        }
        val stdDev = sqrt(variance)

        val risk = when {
            recentSpikesMs.size >= 6 || stdDev > 4.5f -> DisasterRisk.HIGH
            recentSpikesMs.size >= 3 || stdDev > 2.5f -> DisasterRisk.ELEVATED
            else -> DisasterRisk.LOW
        }
        SosStateStore.setDisasterRisk(risk)
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAckPolling() {
        ackPollingJob?.cancel()
        ackPollingJob = serviceScope.launch {
            while (isActive) {
                runCatching { fetchAndApplyServerAcks() }
                delay(3_000L)
            }
        }
    }

    private fun fetchAndApplyServerAcks() {
        val req = Request.Builder()
            .url("${GatewayBridgeService.SERVER_URL}/events")
            .get()
            .build()
        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body?.string() ?: return
            val root = JSONObject(body)
            val acks = root.optJSONArray("acks") ?: return

            for (i in 0 until acks.length()) {
                val ackObject = acks.optJSONObject(i) ?: continue
                val ack = runCatching { AckPacket.fromJson(ackObject.toString()) }.getOrNull() ?: continue
                if (!seenServerAckIds.add(ack.id)) continue
                if (ack.targetNodeId == nodeId) {
                    sosManager.onAckReceived(ack, nodeId)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let {
            runCatching { fusedLocationClient.removeLocationUpdates(it) }
        }
        sensorManager.unregisterListener(this)
        ackPollingJob?.cancel()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        sosManager.cleanup()
    }
}
