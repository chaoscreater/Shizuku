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
            ShizukuReceiverStarter.start(context)
    }
}