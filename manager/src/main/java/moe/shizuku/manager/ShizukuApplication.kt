package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.ShizukuStateMachine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku

lateinit var application: ShizukuApplication

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }

        lateinit var appContext: Context
            private set

    }

    private fun init(context: Context) {
        ShizukuSettings.initialize(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())

        val watchdogEnabled = ShizukuSettings.getWatchdog()
        if (watchdogEnabled) WatchdogService.start(context)
        try {
            Settings.Global.putInt(
                context.contentResolver,
                ShizukuSettings.GLOBAL_KEY_WATCHDOG,
                if (watchdogEnabled) 1 else 0
            )
        } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        appContext = applicationContext
        init(this)
    }

}
