package com.example.data.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Turns the safety network back on after the phone restarts, if the user left it on. */
class GuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RokkhonGuardService.start(context)
        }
    }
}
