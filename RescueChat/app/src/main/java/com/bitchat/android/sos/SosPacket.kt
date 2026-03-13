package com.bitchat.android.sos

import org.json.JSONObject
import java.util.UUID

enum class SosType { CRASH, FALL, MANUAL, SEISMIC }
enum class SosState { IDLE, SENDING, WAITING_ACK, CONFIRMED }

data class SosPacket(
    val id: String = UUID.randomUUID().toString(),
    val nodeId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val batteryLevel: Int,
    val sosType: SosType,
    var ttl: Int = 7,
    val confirmedByNodes: MutableList<String> = mutableListOf()
) {
    fun toWireFormat(): String = listOf(
        "v=1",
        "kind=SOS",
        "id=$id",
        "node=$nodeId",
        "time=$timestamp",
        "lat=$latitude",
        "lon=$longitude",
        "battery=$batteryLevel",
        "type=${sosType.name}",
        "ttl=$ttl"
    ).joinToString(";")

    fun toHumanInstruction(): String =
        "Emergency alert from node ${nodeId.take(8)}. Type: ${sosType.name}. Battery: $batteryLevel%. Location: $latitude, $longitude. Please share this location with nearby responders and call local emergency services."

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("nodeId", nodeId)
        put("timestamp", timestamp)
        put("latitude", latitude)
        put("longitude", longitude)
        put("batteryLevel", batteryLevel)
        put("sosType", sosType.name)
        put("ttl", ttl)
    }.toString()

    companion object {
        fun fromWireFormat(payload: String): SosPacket {
            val text = payload.trim()
            if (text.startsWith("{")) return fromJson(text)

            val kv = mutableMapOf<String, String>()
            text.split(';').forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0 && idx < part.length - 1) {
                    kv[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
                }
            }

            return SosPacket(
                id = kv["id"] ?: UUID.randomUUID().toString(),
                nodeId = kv["node"] ?: "unknown",
                timestamp = kv["time"]?.toLongOrNull() ?: System.currentTimeMillis(),
                latitude = kv["lat"]?.toDoubleOrNull() ?: 0.0,
                longitude = kv["lon"]?.toDoubleOrNull() ?: 0.0,
                batteryLevel = kv["battery"]?.toIntOrNull() ?: 100,
                sosType = kv["type"]?.let { runCatching { SosType.valueOf(it) }.getOrNull() } ?: SosType.MANUAL,
                ttl = kv["ttl"]?.toIntOrNull() ?: 7
            )
        }

        fun fromJson(json: String): SosPacket {
            val obj = JSONObject(json)
            return SosPacket(
                id = obj.getString("id"),
                nodeId = obj.getString("nodeId"),
                timestamp = obj.getLong("timestamp"),
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                batteryLevel = obj.getInt("batteryLevel"),
                sosType = SosType.valueOf(obj.getString("sosType")),
                ttl = obj.optInt("ttl", 7)
            )
        }
    }
}
