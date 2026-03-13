package com.bitchat.android.sos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class SosViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<SosUiState> = SosStateStore.uiState

    fun triggerManualSos() {
        SosStateStore.setState(SosState.WAITING_ACK)
        SosDetectionService.start(
            getApplication(),
            SosDetectionService.ACTION_MANUAL_SOS
        )
    }

    fun cancelSos() {
        SosStateStore.setState(SosState.IDLE)
        SosDetectionService.start(
            getApplication(),
            SosDetectionService.ACTION_CANCEL_SOS
        )
    }

    fun setGatewayMode(on: Boolean) {
        SosStateStore.setGatewayMode(on)
    }
}

data class SosUiState(
    val state: SosState = SosState.IDLE,
    val isGatewayMode: Boolean = true,
    val confirmedByRescuer: String? = null
)
