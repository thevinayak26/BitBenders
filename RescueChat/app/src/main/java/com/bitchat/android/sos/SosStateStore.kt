package com.bitchat.android.sos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SosStateStore {
    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState

    fun setState(state: SosState) {
        val existing = _uiState.value
        _uiState.value = existing.copy(
            state = state,
            confirmedByRescuer = if (state == SosState.CONFIRMED) existing.confirmedByRescuer else null
        )
    }

    fun setGatewayMode(on: Boolean) {
        _uiState.value = _uiState.value.copy(isGatewayMode = on)
    }

    fun markConfirmed(rescuerId: String?) {
        _uiState.value = _uiState.value.copy(
            state = SosState.CONFIRMED,
            confirmedByRescuer = rescuerId
        )
    }
}