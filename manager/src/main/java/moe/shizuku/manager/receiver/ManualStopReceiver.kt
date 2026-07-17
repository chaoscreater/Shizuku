package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

class ManualStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val applicationId = BuildConfig.APPLICATION_ID
        if (intent.action != "${applicationId}.STOP") return

        // Stop watchdog first so it doesn't immediately restart the server
        WatchdogService.stop(context)
        // Cancel any pending AdbStartWorker
        WorkManager.getInstance(context).cancelUniqueWork("adb_start_worker")
        // Mark the stop as intentional so the watchdog's dead-check
        // doesn't undo it (cleared by any start request)
        ShizukuSettings.setManuallyStopped(true)
        // Stop the server if it's running
        if (ShizukuStateMachine.isRunning()) {
            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
            runCatching { Shizuku.exit() }
        } else {
            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPED)
        }
        // Re-start watchdog if the user had it enabled, so it monitors future
        // starts (same behavior as the in-app stop action)
        if (ShizukuSettings.getWatchdog()) {
            WatchdogService.start(context)
        }
    }
}