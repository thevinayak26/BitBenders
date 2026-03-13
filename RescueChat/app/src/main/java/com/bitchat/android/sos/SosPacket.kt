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
