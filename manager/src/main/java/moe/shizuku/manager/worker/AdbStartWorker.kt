package moe.shizuku.manager.worker

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.asFlow
import androidx.work.*
import java.io.EOFException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.WorkerState
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.updateNotification
import moe.shizuku.manager.settings.BugReportDialogActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

class AdbStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            updateNotification(
                applicationContext,
                WorkerState.RUNNING
            )

            val cr = applicationContext.contentResolver
            val enableWirelessDebugging = inputData.getBoolean(KEY_ENABLE_WIFI, true)

            val wirelessAlreadyEnabled = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
            val usbAlreadyEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1

            if (usbAlreadyEnabled) {
                // USB is already on — reset connection time and proceed via USB path.
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            } else if (wirelessAlreadyEnabled) {
                // USB is off, wireless is already active. Writing adb_wifi_enabled=1
                // again would be a no-op (SettingsProvider won't notify on same value),
                // so adbd never reinitializes wireless and mDNS discovery finds nothing.
                // Write 0 first so the callbackFlow's re-enable is a real 0→1 change,
                // forcing adbd to restart wireless and emit a fresh mDNS announcement.
                Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
                try {
                    delay(200)
                } finally {
                    // Restore wireless unconditionally: in normal flow the callbackFlow
                    // below also writes 1 (harmless no-op); on cancellation this prevents
                    // leaving wireless disabled when the worker is stopped mid-cycle.
                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                }
            } else if (!enableWirelessDebugging) {
                // Neither is on and wireless is not requested — fall back to USB.
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }
            // else: neither is on but enableWirelessDebugging=true (e.g. Watchdog restart after
            // adb_wifi_enabled was reset by the system) — do not touch USB; let the callbackFlow
            // below write adb_wifi_enabled=1 so the restart proceeds over wireless only.

            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            if (tcpPort > 0 && !ShizukuSettings.getTcpMode()) {
                AdbStarter.stopTcp(applicationContext, tcpPort)
            }

            val port = tcpPort.takeIf { !EnvironmentUtils.isWifiRequired() } ?: callbackFlow {
                val adbMdns = AdbMdns(applicationContext, AdbMdns.TLS_CONNECT) { p ->
                    if (p > 0) trySend(p)
                }

                var awaitingAuth = false
                var timeoutJob: Job? = null
                var unlockReceiver: BroadcastReceiver? = null

                fun startDiscoveryWithTimeout() {
                    adbMdns.start()
                    timeoutJob?.cancel()
                    timeoutJob = launch {
                        delay(15_000)
                        close(TimeoutException("Timed out during mDNS port discovery"))
                    }
                }

                fun handleAuth() {
                    val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (km.isKeyguardLocked) {
                        val notification = ShizukuReceiverStarter.buildNotification(
                            applicationContext,
                            null
                        )
                        val foregroundInfo = ForegroundInfo(
                            ShizukuReceiverStarter.NOTIFICATION_ID,
                            notification
                        )
                        setForegroundAsync(foregroundInfo)

                        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                        unlockReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == Intent.ACTION_USER_PRESENT) {
                                    context.unregisterReceiver(this)
                                    unlockReceiver = null
                                    if (enableWirelessDebugging) {
                                        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                                    }
                                }
                            }
                        }
                        applicationContext.registerReceiver(unlockReceiver, filter)
                    } else awaitingAuth = true
                    timeoutJob?.cancel()
                    adbMdns.stop()
                }

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                            0 -> if (awaitingAuth) {
                                close(SecurityException("Network is not authorized for wireless debugging"))
                            } else handleAuth()
                            1 -> startDiscoveryWithTimeout()
                        }
                    }
                }

                if (enableWirelessDebugging) {
                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                }
                cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, observer)
                startDiscoveryWithTimeout()

                awaitClose {
                    adbMdns.stop()
                    timeoutJob?.cancel()
                    cr.unregisterContentObserver(observer)
                    unlockReceiver?.let { applicationContext.unregisterReceiver(it) }
                }
            }.first()
            
            AdbStarter.startAdb(applicationContext, port)
            Starter.waitForBinder()

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)

            return Result.success()
        } catch (e: CancellationException) {
            val state = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                WorkerState.AWAITING_RETRY
            } else {
                when (stopReason) {
                    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> WorkerState.AWAITING_WIFI
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> WorkerState.STOPPED
                    else -> WorkerState.AWAITING_RETRY
                }
            }
            updateNotification(applicationContext, state)

            throw e
        } catch (e: Exception) {
            val ignored = listOf(
                EOFException::class,
                SecurityException::class,
                TimeoutException::class
            )
            if (ignored.none { it.isInstance(e) }) showErrorNotification(applicationContext, e)

            if (ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING) {
                return Result.success()
            } else {
                updateNotification(
                    applicationContext,
                    WorkerState.AWAITING_RETRY
                )
                return Result.retry()
            }
        }
    }

    private fun showErrorNotification(context: Context, e: Exception) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)

        val msgNotif = "$e. ${context.getString(R.string.wadb_error_notify_dev)}"

        val intent = Intent(context, BugReportDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = nb
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_error_title))
            .setContentText(msgNotif)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msgNotif))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_ENABLE_WIFI = "enable_wifi"

        fun enqueue(context: Context, enableWirelessDebugging: Boolean = true, immediate: Boolean = false) {
            val cb = Constraints.Builder()
            // `immediate` is for user-initiated starts (manual broadcast, GUI button):
            // they shouldn't wait on unmetered wifi like the Watchdog's unattended
            // auto-restarts do — the discovery flow below works fine without any
            // network connection (e.g. wireless debugging enabled via ADB without wifi).
            if (EnvironmentUtils.isWifiRequired() && !immediate)
                cb.setRequiredNetworkType(NetworkType.UNMETERED)
            val constraints = cb.build()

            val inputData = workDataOf(KEY_ENABLE_WIFI to enableWirelessDebugging)

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "adb_start_worker",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
        const val CHANNEL_ID = "AdbStartWorker"
        const val NOTIFICATION_ID = 1448
    }
}