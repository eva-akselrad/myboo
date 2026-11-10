package com.myboo.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Alerts ("Sam arrived at Home") are queued on the server and picked up by polling, so no Firebase or
 * Google account is involved. The location service polls every ~20 s while sharing is on, and the open
 * app polls too. Notification id = alert id, so two pollers can never show the same alert twice.
 */
object Alerts {
    @SuppressLint("MissingPermission")
    suspend fun poll(ctx: Context, api: Api, prefs: Prefs) {
        if (prefs.token == null) return
        val alerts = api.alerts(prefs.lastAlertId)
        if (alerts.isEmpty()) return
        LocationService.ensureChannels(ctx)
        val canNotify = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (a in alerts) {
            if (canNotify) {
                nm.notify(
                    a.id.toInt(),
                    NotificationCompat.Builder(ctx, LocationService.CHANNEL_EVENTS)
                        .setContentTitle(a.title)
                        .setContentText(a.body)
                        .setSmallIcon(android.R.drawable.ic_dialog_map)
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build(),
                )
            }
        }
        prefs.lastAlertId = maxOf(prefs.lastAlertId, alerts.maxOf { it.id })
    }
}
