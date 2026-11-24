package com.luiscoronel.jervy.system

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luiscoronel.jervy.MainActivity
import com.luiscoronel.jervy.R

/** Canal y notificación fija del servicio, con acciones Pausar/Reanudar y Cerrar. */
object JervyNotification {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "jervy_service"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_name))
                .setShowBadge(false)
                .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        status: ServiceStatus,
    ): Notification {
        val paused = status == ServiceStatus.PAUSED
        val toggleAction =
            if (paused) {
                NotificationCompat.Action(
                    R.drawable.ic_notification,
                    context.getString(R.string.action_resume),
                    servicePendingIntent(context, JervyService.ACTION_RESUME),
                )
            } else {
                NotificationCompat.Action(
                    R.drawable.ic_notification,
                    context.getString(R.string.action_pause),
                    servicePendingIntent(context, JervyService.ACTION_PAUSE),
                )
            }
        val closeAction =
            NotificationCompat.Action(
                R.drawable.ic_notification,
                context.getString(R.string.action_close),
                servicePendingIntent(context, JervyService.ACTION_STOP),
            )
        val text =
            context.getString(
                if (paused) R.string.notification_text_paused else R.string.notification_text_listening,
            )

        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(activityPendingIntent(context))
            // Android 14+ permite descartar la notificación de un foreground service: la re-publicamos.
            .setDeleteIntent(servicePendingIntent(context, JervyService.ACTION_REPOST))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Evita la demora de hasta 10 s en mostrarla (Android 12).
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggleAction)
            .addAction(closeAction)
            .build()
    }

    /** Actualiza la notificación ya publicada. Sin permiso de notificaciones no hace nada. */
    fun update(
        context: Context,
        status: ServiceStatus,
    ) {
        // POST_NOTIFICATIONS existe recién en Android 13; en 12 el permiso no se pide.
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, status))
        }
    }

    private fun servicePendingIntent(
        context: Context,
        action: String,
    ): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, JervyService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun activityPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
