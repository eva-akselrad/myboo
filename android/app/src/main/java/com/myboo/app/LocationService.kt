package com.myboo.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Foreground service that streams location fixes to the MyBoo backend. */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var api: Api
    private var poller: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch {
                runCatching {
                    api.postLocation(
                        loc.latitude, loc.longitude,
                        if (loc.hasSpeed()) loc.speed else null,
                        if (loc.hasAccuracy()) loc.accuracy else null,
                        loc.time,
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        api = Api(Prefs(this))
        ensureChannels(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setContentTitle("MyBoo is sharing your location")
            .setContentText("Your circle can see where you are.")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

        // Balanced power: ~20s cadence is enough to catch arrivals/departures and driving
        // (the backend debounces driving over 2 fast samples).
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMinUpdateDistanceMeters(15f)
            .build()
        runCatching { client.requestLocationUpdates(req, callback, mainLooper) }

        // Heartbeat: fetch alerts for me even while I'm standing still (no location fixes are sent then).
        if (poller?.isActive != true) {
            poller = scope.launch {
                val prefs = Prefs(this@LocationService)
                while (true) {
                    runCatching { Alerts.poll(this@LocationService, api, prefs) }
                    delay(20_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_TRACKING = "myboo_tracking"
        const val CHANNEL_EVENTS = "myboo_events"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TRACKING, "Location sharing", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_EVENTS, "Arrivals, departures & driving", NotificationManager.IMPORTANCE_HIGH),
            )
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
