package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.worker.AdbStartWorker

class NotifAttemptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // User asked for a start attempt — lift manual-stop suppression
        ShizukuSettings.setManuallyStopped(false)
        AdbStartWorker.enqueue(context)
    }
}
