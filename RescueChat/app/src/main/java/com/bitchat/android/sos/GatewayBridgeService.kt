package com.bitchat.android.sos

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GatewayBridgeService : Service() {

    companion object {
        const val EXTRA_SOS_JSON = "sos_json"
        var SERVER_URL = "http://127.0.0.1:3001"
    }

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sosJson = intent?.getStringExtra(EXTRA_SOS_JSON) ?: return START_NOT_STICKY
        scope.launch {
            try {
                post("/sos", sosJson)
            } catch (e: Exception) {
                Log.w("GatewayBridgeService", "Could not reach server: ${e.message}")
            }
        }
        return START_NOT_STICKY
    }

    private fun post(path: String, body: String) {
        val req = Request.Builder()
            .url("$SERVER_URL$path")
            .post(body.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
