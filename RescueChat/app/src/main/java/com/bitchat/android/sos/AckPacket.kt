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
    fun toWireFormat(): String = listOf(
        "v=1",
        "kind=ACK",
        "id=$id",
        "for=$ackForSosId",
        "target=$targetNodeId",
        "rescuer=$rescuerId",
        "time=$timestamp",
        "ttl=$ttl"
    ).joinToString(";")

    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("ackForSosId", ackForSosId)
        put("targetNodeId", targetNodeId)
        put("rescuerId", rescuerId)
        put("timestamp", timestamp)
        put("ttl", ttl)
    }.toString()

    companion object {
        fun fromWireFormat(payload: String): AckPacket {
            val text = payload.trim()
            if (text.startsWith("{")) return fromJson(text)

            val kv = mutableMapOf<String, String>()
            text.split(';').forEach { part ->
                val idx = part.indexOf('=')
                if (idx > 0 && idx < part.length - 1) {
                    kv[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
                }
            }

            return AckPacket(
                id = kv["id"] ?: UUID.randomUUID().toString(),
                ackForSosId = kv["for"] ?: "",
                targetNodeId = kv["target"] ?: "",
                rescuerId = kv["rescuer"] ?: "Rescue-Team",
                timestamp = kv["time"]?.toLongOrNull() ?: System.currentTimeMillis(),
                ttl = kv["ttl"]?.toIntOrNull() ?: 7
            )
        }

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
