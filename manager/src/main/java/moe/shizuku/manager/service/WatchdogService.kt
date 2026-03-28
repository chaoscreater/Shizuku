package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import java.util.concurrent.atomic.AtomicBoolean

class WatchdogService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingRestart = false

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        when (it) {
            ShizukuStateMachine.State.CRASHED -> {
                showCrashNotification()
                attemptRestart()
            }
            ShizukuStateMachine.State.RUNNING -> {
                // Server is back — no longer need screen-on retry
                pendingRestart = false
            }
            else -> Unit
        }
    }

    /**
     * Screen-on receiver: when the user turns the screen on after a crash,
     * trigger a fresh restart. This avoids the problem where crash-time restart
     * attempts fail (mDNS / wireless debugging don't work with screen off) and
     * WorkManager accumulates exponential backoff, making restart indefinitely slow.
     */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT && pendingRestart) {
                Log.d(TAG, "Screen unlocked with pending restart — retrying now")
                attemptRestart()
            }
        }
    }

    private fun attemptRestart() {
        // Cancel any prior WorkManager attempt so we don't inherit exponential backoff
        WorkManager.getInstance(applicationContext).cancelUniqueWork("adb_start_worker")

        serviceScope.launch {
            try {
                val tcpPort = EnvironmentUtils.getAdbTcpPort()
                if (tcpPort > 0 && ShizukuSettings.getTcpMode()) {
                    // Direct TCP restart — fastest path, no mDNS needed
                    pendingRestart = false
                    AdbStarter.startAdb(applicationContext, tcpPort)
                    Starter.waitForBinder()
                } else {
                    // mDNS-based restart via WorkManager.
                    // Enable wireless debugging so mDNS can discover a port.
                    // Mark pending so screen-on receiver can retry if this attempt
                    // fails (e.g. screen is still off).
                    pendingRestart = true
                    ShizukuReceiverStarter.start(applicationContext, enableWirelessDebugging = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct restart failed, falling back to WorkManager", e)
                pendingRestart = true
                ShizukuReceiverStarter.start(applicationContext, enableWirelessDebugging = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        ShizukuStateMachine.addListener(stateListener)
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        sendWatchdogChangedBroadcast(applicationContext, true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVICE") {
            // User explicitly turned off watchdog via notification — persist the setting
            // Write directly to prefs+global instead of setWatchdog() to avoid
            // redundant stop() call (we're already stopping via stopSelf below).
            ShizukuSettings.getPreferences().edit()
                .putBoolean(ShizukuSettings.Keys.KEY_WATCHDOG, false).apply()
            try {
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    ShizukuSettings.GLOBAL_KEY_WATCHDOG, 0
                )
            } catch (_: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_WATCHDOG,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID_WATCHDOG,
                buildNotification()
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pendingRestart = false
        ShizukuStateMachine.removeListener(stateListener)
        runCatching { unregisterReceiver(screenOnReceiver) }
        isRunning.set(false)
        sendWatchdogChangedBroadcast(applicationContext, false)
        // Do NOT persist watchdog=false here. This is called both when the user
        // manually stops Shizuku (temporary) and when the notification stop button
        // is used (permanent). Only the notification stop button (ACTION_STOP_SERVICE)
        // and the settings toggle should persist the preference.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "shizuku_watchdog"
        val channelName = "Watchdog"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or 
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_running))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(launchPendingIntent)
            .addAction(
                R.drawable.ic_close_24,
                getString(R.string.watchdog_turn_off),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun showCrashNotification() {
        val channelId = CRASH_CHANNEL_ID
        val channelName = "Crash Reports"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val learnMoreIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse("https://github.com/thedjchi/Shizuku/wiki#shizuku-keeps-stopping-randomly"))
        }
        val learnMorePendingIntent = PendingIntent.getActivity(this, 0, learnMoreIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val disableIntent = SettingsPage.Notifications.NotificationChannel.buildIntent(applicationContext)
        val disablePendingIntent = PendingIntent.getActivity(this, 0, disableIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_shizuku_crashed_title))
            .setContentText(getString(R.string.watchdog_shizuku_crashed_text))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(learnMorePendingIntent)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.watchdog_shizuku_crashed_action_turn_off_alerts), disablePendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_CRASH, notification)
    }

    companion object {
        private const val TAG = "ShizukuWatchdog"
        private const val NOTIFICATION_ID_WATCHDOG = 1001
        private const val NOTIFICATION_ID_CRASH = 1002
        const val CRASH_CHANNEL_ID = "crash_reports"
        const val ACTION_WATCHDOG_CHANGED = "WATCHDOG_CHANGED"
        const val EXTRA_WATCHDOG_STATUS = "status"

        private val isRunning = AtomicBoolean(false)

        @JvmStatic
        fun sendWatchdogChangedBroadcast(context: Context, enabled: Boolean) {
            val intent = Intent("${context.packageName}.$ACTION_WATCHDOG_CHANGED").apply {
                putExtra(EXTRA_WATCHDOG_STATUS, if (enabled) 1 else 0)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        }

        @JvmStatic
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WatchdogService::class.java))
            } catch (e: Exception) {
                Log.e("ShizukuApplication", "Failed to start WatchdogService: ${e.message}" )
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }

        @JvmStatic
        fun isRunning(): Boolean = isRunning.get()
    }
}