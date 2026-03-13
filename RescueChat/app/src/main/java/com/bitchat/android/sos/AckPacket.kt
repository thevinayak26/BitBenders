package com.bitchat.android.sos

import org.json.JSONObject
import java.util.UUID

data class AckPacket(
    val id: String = UUID.randomUUID().toString(),
    val ackForSosId: String,
    val targetNodeId: String,
    val rescuerId: String,
    val timestamp: Long = System.currentTimeMillis(),
    var ttl: Int = 7
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("ackForSosId", ackForSosId)
        put("targetNodeId", targetNodeId)
        put("rescuerId", rescuerId)
        put("timestamp", timestamp)
        put("ttl", ttl)
    }.toString()

    companion object {
        fun fromJson(json: String): AckPacket {
            val obj = JSONObject(json)
            return AckPacket(
                id = obj.getString("id"),
                ackForSosId = obj.getString("ackForSosId"),
                targetNodeId = obj.getString("targetNodeId"),
                rescuerId = obj.getString("rescuerId"),
                timestamp = obj.getLong("timestamp"),
                ttl = obj.optInt("ttl", 7)
            )
        }
    }
}
