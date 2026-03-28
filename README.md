# Shizuku

An Android app that allows other apps to use system-level APIs that require ADB/root privileges.

### Disclaimer

This is a **FORK** of thedjchi's fork of Shizuku. If you are looking for the original version, please visit the [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) repository. This is based on his latest beta version (as of this writing) - https://github.com/thedjchi/Shizuku/releases/tag/v13.6.0.r1349-thedjchi-beta.

Note that I'm not a developer. I modified it using Claude. The new features I've added are:

- There's a weird bug or quirk with Shizuku on some Chinese devices like Xiaomi, Oppo or Lenovo (probably others as well) - where Shizuku dies when your USB protocol is File Transfer and you turn off the screen. The workaround is to set your USB protocol to charge only, but that's not ideal as it means you'd have to constantly toggle between the 2 just to get Shizuku to work well, or you'd have to setup some sort of automation with Macrodroid/Tasker to fix this. This issue should now be fixed, at least on my Lenovo tablet and Oppo phone. The only caveat is you need either Wifi on, or keep Wireless Debugging on. For wireless debugging to be on, you do NOT need wifi to be on, as long as you use the workaround trick as described below.
- You can now sort the authorized apps by recently added or alphabetically. You can also search for an app now. Useful if you have 60+ apps that you need to go through for whatever reason.
- You can now toggle Watchdog service setting via intent. Useful in certain situations.
- You can use MacroDroid's receive intent trigger to detect Shizuku's on/off status. Whenever the status is changed, MacroDroid can act on this.

Action: moe.shizuku.manager.SHIZUKU_CHANGED
<br>
extra name: status 
<br>
extra value: *

<br>

- You can use MacroDroid's receive intent trigger to detect Watchdog's on/off status. Whenever the status is changed, MacroDroid can act on this.

Action: moe.shizuku.privileged.api.WATCHDOG_CHANGED 
<br>
extra name: status 
<br>
extra value: *

https://imgur.com/3ialiio
<br>

- Allows Shizuku service to start without having USB Debugging on AND without wifi connected to any network. It just needs wireless debugging on, which you can force using a workaround method (https://github.com/thedjchi/Shizuku/issues/165). What does this mean? It means on a non-rooted phone, if you restarted your phone and you can't find any Wifi SSIDs to connect to, or you just can't connect to a network for whatever reason, you can still start Shizuku AND without having to use USB Debugging, without tethering to any PC. It also means you can have USB Debugging off (for some apps, this is required in order to run), while still able to use apps that rely on Shizuku - for example Hail for enabling/disabling apps. 

This also means that if you have apps that can't run if USB Debugging is enabled (e.g. Microsoft Teams, which rely on Company Portal, which checks your device for root and whether you have USB Debugging enabled), you can now run those apps. At the same time, you can run Shizuku related apps (e.g. you can use Hail with Shizuku to enable/disable apps). Both these situations can happen concurrently, which is great.

<br>
<br>

For a demo of this, see the recording below:

https://github.com/user-attachments/assets/9a1b6cc1-d660-447b-bd36-5842e691d7fd

https://github.com/user-attachments/assets/58c64ee2-05b7-4790-a353-0135d16fb63a

<br>

### Download
You'll have to uninstall any previous Shizuku version before you install this one.
https://github.com/chaoscreater/Shizuku/blob/master/shizuku-v13.6.0.r1349-thedjchi-release.apk

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

This Shizuku fork and all of its features will always be free, and there will never be ads. If you've found any of the added features to be useful, consider [donating](https://www.buymeacoffee.com/thedjchi) to help me maintain the project!

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
