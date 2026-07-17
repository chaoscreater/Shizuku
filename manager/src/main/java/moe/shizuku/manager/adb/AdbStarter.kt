package moe.shizuku.manager.adb

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.pm.PackageManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import java.io.EOFException
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine

object AdbStarter {
    suspend fun startAdb(context: Context, port: Int, log: ((String) -> Unit)? = null) {
        suspend fun AdbClient.runCommand(cmd: String) {
            command(cmd) { log?.invoke(String(it)) }
        }

        ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
        log?.invoke("Starting with wireless adb...\n")

        withContext(Dispatchers.IO) {
            val key = runCatching { AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku") }
                .getOrElse {
                    if (it is CancellationException) throw it
                    else throw AdbKeyException(it)
                }

            var activePort = port
            val tcpMode = ShizukuSettings.getTcpMode()
            val tcpPort = ShizukuSettings.getTcpPort()
            // Classic TCP mode rides on the USB debugging toggle. Only switch adbd
            // into TCP mode when USB debugging is already on — switching during a
            // wireless-only start would tie future restarts to a toggle that's off.
            val usbDebugging = Settings.Global.getInt(
                context.contentResolver, Settings.Global.ADB_ENABLED, 0
            ) == 1
            var viaTcp = !EnvironmentUtils.isTlsSupported() ||
                    (activePort > 0 && activePort == EnvironmentUtils.getAdbTcpPort())
            if (tcpMode && usbDebugging && activePort != tcpPort) {
                log?.invoke("Connecting on port $activePort...")

                AdbClient("127.0.0.1", activePort, key).use { client ->
                    client.connect()

                    log?.invoke("Successfully connected on port $activePort...")
                    log?.invoke("\nRestarting in TCP mode port: $tcpPort")

                    activePort = tcpPort
                    viaTcp = true
                    runCatching {
                        client.command("tcpip:$activePort")
                    }.onFailure { if (it !is EOFException && it !is SocketException) throw it } // Expected when ADB restarts in TCP mode
                }
            }

            log?.invoke("Connecting on port $activePort...")

            AdbClient("127.0.0.1", activePort, key).use { client ->
                connectWithRetry(client)
                log?.invoke("Successfully connected on port $activePort...\n")
                client.runCommand("shell:${Starter.internalCommand}")
            }

            // Remember which transport launched the server so the home status
            // card can show whether it runs via wireless or USB debugging.
            ShizukuSettings.setLastAdbTransport(
                if (viaTcp) ShizukuSettings.ADB_TRANSPORT_TCP else ShizukuSettings.ADB_TRANSPORT_TLS
            )
        }
    }

    suspend fun stopTcp(context: Context, port: Int) {
        runCatching {
            val cr = context.contentResolver
            val hadUsbDebugging = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
            val canWriteSettings =
                context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
            if (canWriteSettings) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
            }

            val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
            if (adbEnabled == 0) throw IllegalStateException("ADB is not enabled")

            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
            val key = AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            withContext(Dispatchers.IO) {
                AdbClient("127.0.0.1", port, key).use { client ->
                    connectWithRetry(client)
                    client.command("usb:")
                }
            }
            // USB debugging was only borrowed to issue the command — restore it
            if (!hadUsbDebugging && canWriteSettings) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
            }
            // Resolve the STOPPING state we set above: closing the TCP port does
            // not kill an already-running server, and if nothing was running the
            // state must return to STOPPED rather than stick at STOPPING (which
            // would suppress the watchdog's dead-check).
            ShizukuStateMachine.update()
        }.onFailure {
            // Never leave the state stuck at STOPPING
            ShizukuStateMachine.update()
            if (EnvironmentUtils.getAdbTcpPort() > 0) {
                withContext(Dispatchers.Main) {
                    val errorMsg = when (it) {
                        is AdbKeyException -> context.getString(R.string.adb_error_key_store)
                        else -> it.message
                    }
                    Toast.makeText(context, context.getString(R.string.adb_error_stop_tcp) + ". ${errorMsg}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private suspend fun connectWithRetry(client: AdbClient) {
        var delayTime = 0L
        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            try {
                delay(delayTime)
                client.connect()
                break
            } catch (e: Exception) {
                if (
                    attempt == maxAttempts ||
                    e is CancellationException ||
                    e is SocketTimeoutException
                ) throw e
                delayTime += 1000
            }
        }
    }
}