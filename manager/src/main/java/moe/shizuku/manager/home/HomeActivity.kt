package moe.shizuku.manager.home

import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.SnackbarHelper
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.databinding.HomeActivityBinding
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.utils.ShizukuStateMachine
import androidx.work.WorkManager
import java.text.DateFormat
import java.util.Date
import rikka.core.content.asActivity
import rikka.core.ktx.unsafeLazy
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppBarActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()
    private val adapter by unsafeLazy { HomeAdapter(homeModel, appsModel, lifecycleScope) }

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isRunning()) {
            checkServerStatus()
            appsModel.load()
        } else if (ShizukuStateMachine.isDead()) {
            checkServerStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = HomeActivityBinding.inflate(layoutInflater, rootView, true)

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                val status = homeModel.serviceStatus.value?.data ?: return@observe
                adapter.updateData()
                ShizukuSettings.setLastLaunchMode(if (status.uid == 0) ShizukuSettings.LaunchMethod.ROOT else ShizukuSettings.LaunchMethod.ADB)
            }
        }

        homeModel.shouldShowBatteryOptimizationSnackbar.observe(this) {
            if (it) {
                SnackbarHelper.show(
                    this,
                    binding.root,
                    msg = getString(R.string.snackbar_battery_optimization_home),
                    duration = Snackbar.LENGTH_INDEFINITE,
                    actionText = getString(R.string.snackbar_action_fix),
                    action = { SettingsHelper.requestIgnoreBatteryOptimizations(this, null) }
                )
            }
        }
        homeModel.checkBatteryOptimization()

        appsModel.grantedCount.observe(this) {
            if (it.status == Status.SUCCESS) {
                adapter.updateData()
            }
        }

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()

        val cardSpacing = resources.getDimension(R.dimen.card_spacing)
        val marginHorizontal = resources.getDimension(R.dimen.margin_horizontal)
        val marginVertical = resources.getDimension(R.dimen.margin_vertical)

        val itemSpacing = cardSpacing / 2f
        val edgeSpacingH = marginHorizontal
        val edgeSpacingV = marginVertical - itemSpacing

        recyclerView.addItemSpacing(top = itemSpacing, bottom = itemSpacing)
        recyclerView.addEdgeSpacing(top = edgeSpacingV, bottom = edgeSpacingV, left = edgeSpacingH, right = edgeSpacingH)

        ShizukuStateMachine.addListener(stateListener)

        // onNewIntent only fires when an instance is already running; handle a
        // cold start (e.g. launched fresh by an external Send Intent) here too.
        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        intent?.let {
            val showDialog = it.getBooleanExtra(HomeActivity.EXTRA_SHOW_PAIRING_DIALOG, false)
            if (showDialog) showAccessibilityDialog()

            val startWadb = it.getBooleanExtra(HomeActivity.EXTRA_START_SERVICE_VIA_WADB, false)
            if (startWadb) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(AdbPairingService.NOTIFICATION_ID)
                StartWirelessAdbViewHolder.start(this, lifecycleScope)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAppBarTitle()
        checkServerStatus()
        appsModel.load()
    }

    private var appBarTitleView: TextView? = null

    /**
     * Personalized app bar title, showing the date this build was compiled
     * (BuildConfig.BUILD_TIME), not the current date. Re-applied on every
     * resume, but the date itself only changes when a new build is installed.
     *
     * The toolbar's built-in title view can only ellipsize long text, and its
     * width changes after the menu icons are inflated. So the built-in title is
     * hidden and replaced with a custom TextView that fills the space left of
     * the menu icons and auto-sizes its text (20sp down to a 10sp floor) —
     * re-fitting automatically on every layout change.
     */
    private fun updateAppBarTitle() {
        val newTitle = "${getString(R.string.app_name)} - modified by Ricky - ${
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(BuildConfig.BUILD_TIME))
        }"
        title = newTitle

        val titleView = appBarTitleView ?: run {
            val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: return
            supportActionBar?.setDisplayShowTitleEnabled(false)
            AppCompatTextView(toolbar.context).apply {
                TextViewCompat.setTextAppearance(
                    this, R.style.TextAppearance_MaterialComponents_Headline6
                )
                maxLines = 1
                gravity = Gravity.CENTER_VERTICAL
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 10, 20, 1, TypedValue.COMPLEX_UNIT_SP
                )
                toolbar.addView(
                    this,
                    Toolbar.LayoutParams(
                        Toolbar.LayoutParams.MATCH_PARENT,
                        Toolbar.LayoutParams.MATCH_PARENT
                    )
                )
                appBarTitleView = this
            }
        }
        titleView.text = newTitle
    }

    override fun onPause() {
        super.onPause()
        SnackbarHelper.dismiss()
    }

    private fun checkServerStatus() {
        homeModel.reload()
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                val binding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
                binding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
                binding.sourceCode.text = getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://github.com/thedjchi/Shizuku\">GitHub</a></b>"
                ).toHtml()
                binding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                        this,
                        applicationInfo,
                        Process.myUid() / 100000,
                        resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
                    )
                )
                binding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName

                val dialog = MaterialAlertDialogBuilder(this)
                    .setView(binding.root)
                    .create()

                binding.btnClose.setOnClickListener {
                    dialog.dismiss()
                }
                
                dialog.show()
                true
            }
            R.id.action_stop -> {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_stop_message)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        // Stop watchdog temporarily so it doesn't immediately restart the server
                        WatchdogService.stop(this)
                        // Cancel any pending AdbStartWorker
                        WorkManager.getInstance(this).cancelUniqueWork("adb_start_worker")
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
                        // Re-start watchdog if the user had it enabled, so it monitors future starts
                        if (ShizukuSettings.getWatchdog()) {
                            WatchdogService.start(this)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_SHOW_PAIRING_DIALOG = "show_pairing_dialog"
        const val EXTRA_START_SERVICE_VIA_WADB = "start_service_via_wadb"
    }

}
