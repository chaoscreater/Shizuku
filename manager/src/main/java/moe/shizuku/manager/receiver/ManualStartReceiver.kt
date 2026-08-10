package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.BuildConfig

class ManualStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val applicationId = BuildConfig.APPLICATION_ID
        if (intent.action != "${applicationId}.START") return

        if (intent.getBooleanExtra("FORCE_ROOT", false))
            ShizukuReceiverStarter.rootStart(context)
        else
            // Broadcast is user-initiated (e.g. MacroDroid), same as the GUI Start
            // button — don't gate on unmetered wifi like the Watchdog's auto-restart does.
            ShizukuReceiverStarter.start(context, immediate = true)
    }
}