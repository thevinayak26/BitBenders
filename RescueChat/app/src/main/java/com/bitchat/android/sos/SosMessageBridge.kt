package com.bitchat.android.sos

import android.content.Context

object SosMessageBridge {
    const val SOS_PREFIX = "SOS::"
    const val ACK_PREFIX = "ACK::"

    fun maybeForwardIncomingMeshContent(context: Context, content: String) {
        when {
            content.startsWith(SOS_PREFIX) -> {
                SosDetectionService.start(
                    context = context,
                    action = SosDetectionService.ACTION_INCOMING_SOS,
                    jsonPayload = content.removePrefix(SOS_PREFIX)
                )
            }
            content.startsWith(ACK_PREFIX) -> {
                SosDetectionService.start(
                    context = context,
                    action = SosDetectionService.ACTION_INCOMING_ACK,
                    jsonPayload = content.removePrefix(ACK_PREFIX)
                )
            }
        }
    }
}
