package com.myboo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resume location sharing after a reboot if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs(context).sharing) runCatching { LocationService.start(context) }
    }
}
