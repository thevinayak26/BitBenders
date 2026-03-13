package com.bitchat.android.sos

import android.content.Context
import android.content.Intent

object SosMessageBridge {
    const val SOS_PREFIX = "SOS::"
    const val ACK_PREFIX = "ACK::"

    const val ACTION_INCOMING_SOS = "com.bitchat.android.sos.INCOMING_SOS"
    const val ACTION_INCOMING_ACK = "com.bitchat.android.sos.INCOMING_ACK"
    const val EXTRA_JSON = "json"

    fun maybeForwardIncomingMeshContent(context: Context, content: String) {
        when {
            content.startsWith(SOS_PREFIX) -> {
                context.sendBroadcast(
                    Intent(ACTION_INCOMING_SOS).putExtra(EXTRA_JSON, content.removePrefix(SOS_PREFIX))
                )
            }
            content.startsWith(ACK_PREFIX) -> {
                context.sendBroadcast(
                    Intent(ACTION_INCOMING_ACK).putExtra(EXTRA_JSON, content.removePrefix(ACK_PREFIX))
                )
            }
        }
    }
}
