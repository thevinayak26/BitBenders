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
    fun toReadableMeshMessage(): String {
        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
        return buildString {
            appendLine("EMERGENCY SOS ALERT")
            appendLine("Type: ${sosType.name}")
            appendLine("Sender: $nodeId")
            appendLine("Battery: $batteryLevel%")
            appendLine("Location: $latitude, $longitude")
            appendLine("Open in Maps: $mapsUrl")
            appendLine("Message ID: $id")
            append("Relay hops left: $ttl")
        }
    }

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
        private fun fromReadableMeshMessage(payload: String): SosPacket {
            val lines = payload.lines().map { it.trim() }

            fun valueAfter(prefix: String): String? {
                return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                    ?.substringAfter(':', "")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }

            val locationText = valueAfter("Location")
            val (lat, lon) = if (locationText != null && locationText.contains(',')) {
                val parts = locationText.split(',')
                (parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0) to
                    (parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0)
            } else {
                0.0 to 0.0
            }

            return SosPacket(
                id = valueAfter("Message ID") ?: UUID.randomUUID().toString(),
                nodeId = valueAfter("Sender") ?: "unknown",
                timestamp = System.currentTimeMillis(),
                latitude = lat,
                longitude = lon,
                batteryLevel = valueAfter("Battery")
                    ?.removeSuffix("%")
                    ?.trim()
                    ?.toIntOrNull() ?: 100,
                sosType = valueAfter("Type")
                    ?.let { runCatching { SosType.valueOf(it) }.getOrNull() } ?: SosType.MANUAL,
                ttl = lines.firstOrNull { it.startsWith("Relay hops left", ignoreCase = true) }
                    ?.substringAfter(':', "")
                    ?.trim()
                    ?.toIntOrNull() ?: 7
            )
        }

        fun fromWireFormat(payload: String): SosPacket {
            val text = payload.trim()
            if (text.startsWith("{")) return fromJson(text)
            if (text.startsWith("EMERGENCY SOS ALERT", ignoreCase = true)) {
                return fromReadableMeshMessage(text)
            }

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
