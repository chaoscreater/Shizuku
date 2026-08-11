# Shizuku

An Android app that allows other apps to use system-level APIs that require ADB/root privileges.

### Disclaimer

This is a **FORK** of thedjchi's fork of Shizuku. If you are looking for the original version, please visit the [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) repository. This is based on his latest beta version (as of this writing) - https://github.com/thedjchi/Shizuku/releases/tag/v13.6.0.r1349-thedjchi-beta.

## New features in my version

### 1. 🔧 Shizuku stability on certain Chinese devices

Fixes a strange Shizuku bug/quirk affecting some Chinese devices, including **Xiaomi, OPPO, Lenovo**, and potentially others.

On affected devices, Shizuku would die when:

* USB mode was set to **File Transfer**, and
* the device screen was turned off.

Previously, the workaround was to change the USB mode to **Charge Only**. This wasn't ideal because you'd have to constantly switch between USB modes, or create a MacroDroid/Tasker automation to do it.

This issue should now be fixed, at least on my **Lenovo tablet and OPPO phone**. However, changing between **Charge Only** and **File Transfer** mode could have an impact for another feature described below.

**Caveat:** Wireless Debugging must remain enabled.

> **Note:** Wireless Debugging does **not** require Wi-Fi to be connected to a network. The workaround described below can be used to keep Wireless Debugging enabled even when Wi-Fi isn't available.

---

### 2. 📱 Start Shizuku without USB Debugging or an active Wi-Fi connection

Shizuku can now start with:

* ✅ USB Debugging **OFF**
* ✅ No Wi-Fi network connection
* ✅ No PC or USB connection
* ✅ Wireless Debugging **ON**
* ✅ Does NOT kill Wireless Debugging (adb_wifi_enabled). Other versions of Shizuku will/may do this. Mine doesn't. However, this doesn't mean Wireless Debugging won't be killed by Android System itself, I have no control over that.

Wireless Debugging can be forced on using the workaround described in [Shizuku issue #165](https://github.com/thedjchi/Shizuku/issues/165). You can also refer to the demo video below for a better understanding of how the workaround works.

**Important caveat:** Android will kill the Shizuku process in certain scenarios for certain phones. This is outside of Shizuku's control and there isn't much I can do about it. In my case, I can leave my phone overnight and Shizuku will still be running happily using Wireless Debugging mode. My Wireless Debugging stays on and doesn't get killed. This is on Galaxy Z Fold 7 with A16 OneUI 8.0.

As an example, Shizuku will be stopped if you switch the USB connection mode from **Charge Only** to **File Transfer**. More importantly, doing so also **undoes the Wireless Debugging workaround** demonstrated in the video below.

This means that after switching to **File Transfer**:

* Shizuku will be stopped. But it will be started automatically as long as your WiFi is still connected to a SSID. It will start Shizuku using Wireless Debugging mode. OR, if you have USB Debugging enabled, it will use that to start Shizuku instead. Shizuku will prioritize starting with USB Debugging over starting with Wireless Debugging.
* The Wireless Debugging workaround will be reverted.
* If you subsequently turn off Wi-Fi, Wireless Debugging will become disabled again.
* You won't be able to re-enable Wireless Debugging unless you either:

  * Turn Wi-Fi back on and connect to a Wi-Fi network/SSID, or
  * Re-apply the workaround shown in the video.

### Recommended Setup

The easiest way to avoid this issue is to keep the USB connection mode set to **Charge Only** and use alternative methods for transferring files or accessing your device.

For example:

* **ADB** — for file transfers and remote access via `scrcpy`
* **HTTP/FTP servers** — for quick file transfers between your phone and PC
* Other network-based file-transfer solutions

There are plenty of alternatives, so you don't necessarily need to switch the USB mode to **File Transfer**.

Personally, I rarely use USB File Transfer, so this isn't much of an issue for me. And if you do need it, you can always fall back to the standard **USB Debugging** method to start Shizuku again.

#### Example use case for Starting Shizuku without USB Debugging or an active Wi-Fi connection

After restarting your phone, imagine that:

* There are no available Wi-Fi networks to connect to, or
* You can't connect to a Wi-Fi network for whatever reason.

You can still start Shizuku **without USB Debugging and without connecting to a PC**.

It also allows you to keep USB Debugging disabled when an app requires it to be off, while continuing to use apps that depend on Shizuku.

For example:

**USB Debugging OFF**
→ Apps that refuse to run when USB Debugging is enabled can work normally.

**Shizuku RUNNING**
→ Apps such as **Hail** can still use Shizuku to enable/disable apps.

Both can therefore work **simultaneously**.

This is particularly useful for apps such as **Microsoft Teams**, where Company Portal may check whether USB Debugging is enabled.

---

### 3. 🔎 Search and sort authorized apps

The authorized-apps list can now be:

* Sorted **alphabetically**
* Sorted by **recently added**
* **Searched**

This is particularly useful if you have **60+ authorized apps** and need to find or review a specific app.

---

### 4. ⚙️ Toggle Watchdog via Intent

The **Watchdog service** can now be enabled or disabled through an Intent.

This makes it possible to control Watchdog from automation apps such as MacroDroid or Tasker.

---

### 5. 🤖 Detect Shizuku status changes with MacroDroid

MacroDroid can listen for Shizuku status changes using its **Receive Intent** trigger.

Whenever Shizuku is turned on or off, MacroDroid can react accordingly.

**Intent action:**

```text
moe.shizuku.manager.SHIZUKU_CHANGED
```

**Extra name:**

```text
status
```

**Extra value:**

```text
*
```

This can be used to trigger other actions whenever Shizuku's status changes.

---

### 6. 🐶 Detect Watchdog status changes with MacroDroid

MacroDroid can also listen for Watchdog status changes.

**Intent action:**

```text
moe.shizuku.privileged.api.WATCHDOG_CHANGED
```

**Extra name:**

```text
status
```

**Extra value:**

```text
*
```

This allows automations to react whenever Watchdog is enabled or disabled.

https://imgur.com/3ialiio

---

For a demo of the wireless debugging workaround, see the recording below:

https://github.com/user-attachments/assets/019d6d2c-582b-4f82-bac0-20a9ad40d09d

<br>

### Download
You'll have to uninstall any previous Shizuku version before you install this one.
https://github.com/chaoscreater/Shizuku/blob/master/out/apk/shizuku-v13.6.0.r1349-thedjchi-release.apk

<br>
<br>
<br>

### Added Features

This version of Shizuku includes some extra features over the original version, such as:
* **More robust "start on boot":** waits for a Wi-Fi connection before starting the Shizuku service
* **TCP mode:** (i.e., the `adb tcpip` command) once Shizuku successfully starts with Wi-Fi after a reboot, you can stop/restart Shizuku without a Wi-Fi connection!
* **Watchdog service:** automatically restarts Shizuku if it stops unexpectedly, and can alert you of crashes/potential fixes
* **Start/stop intents:** toggle Shizuku on-demand using automation apps (e.g., Tasker, MacroDroid, Automate)
* **[BETA] Stealth mode:** hide Shizuku from other apps that don't work when Shizuku is installed
* **[BETA] In-app updates:** option to automatically check for new updates, and can automatically download/install the latest version from GitHub
* **Android/Google TV and VR headset support:** UI is now compatible with D-Pad remotes, all TVs are supported (including Android 14+ TVs that require pairing), and the multi-window pairing dialog is toggleable in settings for VR headsets
* **MediaTek support:** fixes a critical bug in the original v13.6.0 which prevented Shizuku from working on MediaTek devices
* And more!

### Wiki

Please read the [wiki](https://github.com/thedjchi/Shizuku/wiki) for setup and troubleshooting instructions.

### Translations

Contribute translations through the [Crowdin project](https://crowdin.com/project/shizuku).

### Donations

This Shizuku fork and all of its features will always be free, and there will never be ads. If you've found any of the added features to be useful, consider [donating](https://ko-fi.com/ricky76324) to help me maintain the project!

## Background

When developing apps that requires root, the most common method is to run some commands in the su shell. For example, there is an app that uses the `pm enable/disable` command to enable/disable components.

This method has very big disadvantages:

1. **Extremely slow** (Multiple process creation)
2. Needs to process texts (**Super unreliable**)
3. The possibility is limited to available commands
4. Even if ADB has sufficient permissions, the app requires root privileges to run

Shizuku uses a completely different way. See detailed description below.

## User guide & Download

<https://shizuku.rikka.app/>

## How does Shizuku work?

First, we need to talk about how app use system APIs. For example, if the app wants to get installed apps, we all know we should use `PackageManager#getInstalledPackages()`. This is actually an interprocess communication (IPC) process of the app process and system server process, just the Android framework did the inner works for us.

Android uses `binder` to do this type of IPC. `Binder` allows the server-side to learn the uid and pid of the client-side, so that the system server can check if the app has the permission to do the operation.

Usually, if there is a "manager" (e.g., `PackageManager`) for apps to use, there should be a "service" (e.g., `PackageManagerService`) in the system server process. We can simply think if the app holds the `binder` of the "service", it can communicate with the "service". The app process will receive binders of system services on start.

Shizuku guides users to run a process, Shizuku server, with root or ADB first. When the app starts, the `binder` to Shizuku server will also be sent to the app.

The most important feature Shizuku provides is something like be a middle man to receive requests from the app, sent them to the system server, and send back the results. You can see the `transactRemote` method in `rikka.shizuku.server.ShizukuService` class, and `moe.shizuku.api.ShizukuBinderWrapper` class for the detail.

So, we reached our goal, to use system APIs with higher permission. And to the app, it is almost identical to the use of system APIs directly.

## Developer guide

### API & sample

https://github.com/RikkaApps/Shizuku-API

### Migrating from pre-v11

> Existing applications still works, of course.

https://github.com/RikkaApps/Shizuku-API#migration-guide-for-existing-applications-use-shizuku-pre-v11

### Attention

1. ADB permissions are limited

   ADB has limited permissions and different on various system versions. You can see permissions granted to ADB [here](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml).

   Before calling the API, you can use `ShizukuService#getUid` to check if Shizuku is running user ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.

2. Hidden API limitation from Android 9

   As of Android 9, the usage of the hidden APIs is limited for normal apps. Please use other methods (such as <https://github.com/LSPosed/AndroidHiddenApiBypass>).

3. Android 8.0 & ADB

   At present, the way Shizuku service gets the app process is to combine `IActivityManager#registerProcessObserver` and `IActivityManager#registerUidObserver` (26+) to ensure that the app process will be sent when the app starts. However, on API 26, ADB lacks permissions to use `registerUidObserver`, so if you need to use Shizuku in a process that might not be started by an Activity, it is recommended to trigger the send binder by starting a transparent activity.

4. Direct use of `transactRemote` requires attention

   * The API may be different under different Android versions, please be sure to check it carefully. Also, the `android.app.IActivityManager` has the aidl form in API 26 and later, and `android.app.IActivityManager$Stub` exists only on API 26.

   * `SystemServiceHelper.getTransactionCode` may not get the correct transaction code, such as `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages` does not exist on API 25 and there is `android.content.pm.IPackageManager$Stub.TRANSACTION_getInstalledPackages_47` (this situation has been dealt with, but it is not excluded that there may be other circumstances). This problem is not encountered with the `ShizukuBinderWrapper` method.

## Developing Shizuku itself

### Build

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. Be aware that, in Android Studio, "Run/Debug configurations" - "Always install with package manager" should be checked, so that the server will use the latest code.

## License

All code files in this project are licensed under Apache 2.0

Under Apache 2.0 section 6, specifically:

* You are **FORBIDDEN** to use `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.

* You are **FORBIDDEN** to use `Shizuku` as app name or use `moe.shizuku.privileged.api` as application id or declare `moe.shizuku.manager.permission.*` permission.
