package com.luiscoronel.jervy.system

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.luiscoronel.jervy.AppContainer
import com.luiscoronel.jervy.JervyApplication

/**
 * Foreground service de tipo micrófono (R1). En el paso 1.1 todavía no abre el micrófono:
 * solo mantiene el servicio vivo y la notificación fija.
 *
 * Pausar no detiene el servicio: lo deja vivo (y, desde el paso 1.2, suelta el micrófono),
 * porque Android 14+ restringe arrancar un servicio de micrófono desde segundo plano.
 */
class JervyService : Service() {
    private val container: AppContainer
        get() = (application as JervyApplication).container

    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")
        when (action) {
            ACTION_START -> start()
            ACTION_PAUSE -> ifStarted { setStatus(ServiceStatus.PAUSED) }
            ACTION_RESUME -> ifStarted { setStatus(ServiceStatus.ACTIVE) }
            ACTION_REPOST -> ifStarted { JervyNotification.update(this, container.serviceStatus.value) }
            ACTION_STOP -> stop()
            else -> ifStarted {}
        }
        // Sin reinicio automático: un reinicio desde segundo plano no puede volver a tomar el micrófono
        // en Android 14+. La estrategia de supervivencia se define en el paso 1.3.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        container.updateServiceStatus(ServiceStatus.STOPPED)
        Log.d(TAG, "Servicio destruido")
        super.onDestroy()
    }

    private fun start() {
        if (started) return
        JervyNotification.createChannel(this)
        ServiceCompat.startForeground(
            this,
            JervyNotification.NOTIFICATION_ID,
            JervyNotification.build(this, ServiceStatus.ACTIVE),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        started = true
        container.updateServiceStatus(ServiceStatus.ACTIVE)
        Log.d(TAG, "Servicio en primer plano")
    }

    private fun setStatus(status: ServiceStatus) {
        container.updateServiceStatus(status)
        JervyNotification.update(this, status)
        Log.d(TAG, "Estado: $status")
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Una acción que llega sin que el servicio esté en primer plano (p. ej. el proceso murió) lo cierra. */
    private inline fun ifStarted(block: () -> Unit) {
        if (started) block() else stopSelf()
    }

    companion object {
        private const val TAG = "Jervy"
        const val ACTION_START = "com.luiscoronel.jervy.action.START"
        const val ACTION_PAUSE = "com.luiscoronel.jervy.action.PAUSE"
        const val ACTION_RESUME = "com.luiscoronel.jervy.action.RESUME"
        const val ACTION_STOP = "com.luiscoronel.jervy.action.STOP"
        const val ACTION_REPOST = "com.luiscoronel.jervy.action.REPOST"
    }
}
