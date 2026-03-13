package com.bitchat.android.sos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SosManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sosLoopJob: Job? = null
    private var activeSosPacket: SosPacket? = null

    private val _state = MutableStateFlow(SosState.IDLE)
    val state: StateFlow<SosState> = _state

    private val seenSosIds = mutableSetOf<String>()
    private val seenAckIds = mutableSetOf<String>()

    var onBroadcastSos: ((SosPacket) -> Unit)? = null
    var onBroadcastAck: ((AckPacket) -> Unit)? = null
    var onSosConfirmed: ((AckPacket) -> Unit)? = null

    companion object {
        const val SOS_INTERVAL_MS = 5_000L
        const val MAX_SEND_DURATION_MS = 3_600_000L
    }

    fun triggerSos(packet: SosPacket) {
        activeSosPacket = packet
        _state.value = SosState.SENDING
        seenSosIds.add(packet.id)

        sosLoopJob?.cancel()
        sosLoopJob = scope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                if (System.currentTimeMillis() - start > MAX_SEND_DURATION_MS) {
                    _state.value = SosState.IDLE
                    break
                }
                onBroadcastSos?.invoke(packet)
                _state.value = SosState.WAITING_ACK
                delay(SOS_INTERVAL_MS)
            }
        }
    }

    fun onSosReceived(packet: SosPacket, myNodeId: String) {
        if (packet.nodeId == myNodeId) return
        if (packet.id in seenSosIds || packet.ttl <= 0) return
        seenSosIds.add(packet.id)
        onBroadcastSos?.invoke(packet.copy(ttl = packet.ttl - 1))
    }

    fun onAckReceived(ack: AckPacket, myNodeId: String) {
        if (ack.id in seenAckIds) return
        seenAckIds.add(ack.id)

        if (ack.targetNodeId == myNodeId && ack.ackForSosId == activeSosPacket?.id) {
            sosLoopJob?.cancel()
            _state.value = SosState.CONFIRMED
            onSosConfirmed?.invoke(ack)
            return
        }

        if (ack.ttl > 0) onBroadcastAck?.invoke(ack.copy(ttl = ack.ttl - 1))
    }

    fun cancelSos() {
        sosLoopJob?.cancel()
        _state.value = SosState.IDLE
        activeSosPacket = null
    }

    fun cleanup() {
        scope.coroutineContext[Job]?.cancel()
    }
}
