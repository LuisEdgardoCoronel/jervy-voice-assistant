package com.luiscoronel.jervy

import com.luiscoronel.jervy.system.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Inyección de dependencias manual: un único contenedor por proceso, creado en [JervyApplication]. */
class AppContainer {
    private val mutableServiceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = mutableServiceStatus.asStateFlow()

    fun updateServiceStatus(status: ServiceStatus) {
        mutableServiceStatus.value = status
    }
}
