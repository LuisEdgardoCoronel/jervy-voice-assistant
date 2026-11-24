package com.luiscoronel.jervy.system

/** Estado del foreground service que ve la UI (no es la máquina de estados del pipeline). */
enum class ServiceStatus {
    STOPPED,
    ACTIVE,
    PAUSED,
}
