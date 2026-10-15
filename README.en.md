# App Time Lock

[简体中文](README.md) | English

Make a deliberate choice before opening an Android app: choose how long to use it, then decide whether to continue when time runs out.

Requires **Android 13 or later**. Device testing has only been performed on a Xiaomi 13 running Android 16 / HyperOS 3.0. Other devices need independent testing; continuous background operation is not guaranteed on every model.

The app interface is currently in Chinese. English translations of interface labels below are provided for reference.

## Installation and permissions

1. Download the APK from [Releases](https://github.com/yerlyrogowski-stack/App-Time-Lock/releases). If Android asks to let your browser or file manager install unknown apps, enable this only for a source you trust. You can disable that installation permission afterward.
2. Open the app, select the apps you want to control, and tap **开启使用提醒** (Enable usage reminders) on the home screen. Read the disclosure, open Android's accessibility settings, and enable **应用时间锁 · 使用提醒**. This service is required to identify the foreground app and display the duration picker and time-up reminder.
3. If Android reports **Restricted settings**, look for the option to allow restricted settings on this app's system App info page, follow the system prompts, and then enable the accessibility service. The location varies by manufacturer and system version. If device management policies prevent authorization, protection cannot operate; do not bypass those policies.
4. If your phone provides **Autostart / Background autostart**, enable it for App Time Lock. Allow background activity and remove battery restrictions for this app where those options exist. Xiaomi-specific guidance is below. Other manufacturers use different names and may not provide all of these switches.
5. If you regularly clear recent apps, you can lock **App Time Lock itself** in the recent-apps screen where supported to reduce the chance of it being cleared. This is separate from the controlled app whose allowance should reset when its task is removed.
6. Return to the home screen and check for **提醒服务已连接** (Reminder service connected). Then open a controlled app and confirm that the duration picker actually appears. Autostart does not replace accessibility authorization, and a connected status does not guarantee indefinite operation.

Regular users **do not need USB debugging, Install via USB, or USB debugging (Security settings)**. These are only used for development and testing from a computer. The app also does not require device administrator privileges, usage access, the ordinary “Display over other apps” permission, or exact alarm permission.

### Checks on a new phone

- Open a controlled app and confirm that you must choose a duration before using it.
- Set a custom duration of one minute, remain in the target app, and confirm that the time-up reminder appears.
- Go to the home screen and return normally; the remaining allowance should be preserved.
- Swipe the target app away from recent apps and reopen it; check that you are asked to choose a duration again. This depends on system task information and is not guaranteed across manufacturers.
- Check that the sidebar expands on tap and retracts automatically. Timing and time-up reminders should still work with the sidebar disabled.
- Recheck the service status and actual blocking behavior after a phone restart, system update, long standby period, or background cleanup.

If no reminder appears, check the controlled-app selection, accessibility service, autostart, and battery restrictions in that order. Re-enable the service if the system has disabled it. Force-stopping, revoking permissions, or uninstalling App Time Lock stops protection. This is a personal time-management tool, not an unbypassable lock.

## How to use

1. Install the APK and open **应用时间锁** (App Time Lock).
2. Select the apps to control on the **应用** (Apps) tab.
3. Return to the home screen, tap **开启使用提醒** (Enable usage reminders), read the disclosure, and open system settings.
4. Enable the **应用时间锁 · 使用提醒** accessibility service. Return and confirm **提醒服务已连接** (Reminder service connected). On Xiaomi phones, also complete the background settings below and verify that opening a controlled app triggers the picker.
5. Open a controlled app, select **10 / 20 / 30 minutes** or a custom duration of **1–180 minutes**, and tap **开始使用** (Start using).
6. The countdown sidebar initially retracts to the screen edge, leaving a narrow handle without digits. Tap it to reveal the countdown with an approximately 240 ms animation. It retracts automatically after five seconds while timing continues. Both the handle and expanded label can be dragged to either screen edge. Disable it under **设置 → 倒计时侧边栏** (Settings → Countdown sidebar) without affecting timing or time-up reminders.
7. When time runs out, the app vibrates briefly and displays a reminder. Choose **结束使用** (End session) to return home, or **再用一会** (Use a little longer) to select another duration.

Each controlled app has its own allowance. Saved remaining time is preserved across midnight and phone restarts. Deselecting an app clears its allowance.

## Removing a task versus switching apps (introduced in 1.1.0)

- Pressing Home, switching apps, moving an app to the background, or locking the screen pauses timing. Returning to the same task resumes the remaining allowance.
- After removing a target app from recent apps and reopening it, detecting a new task ID within the same boot clears the previous allowance and prompts for a new duration.
- Normal navigation between pages within the same task does not reset the allowance. Expanding or hiding the sidebar does not affect it either.
- This detects task recreation; it does not receive a direct notification that another app was swiped away. Finishing the entire task with Back and subsequently creating a new task can also trigger a new duration prompt. Apps using multiple document tasks are not supported.
- Android does not expose a public accessibility-window task-ID getter. The compatibility layer extracts only the task ID from the system window description and does not retain that description or its title. This format is not a stable SDK contract. When IDs are unavailable, the format changes, no baseline exists after an upgrade, or the phone restarts, the app conservatively preserves the allowance. Task removal detection cannot be guaranteed on every system.
- Implementation reference: [AOSP AccessibilityWindowInfo](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/accessibility/AccessibilityWindowInfo.java). Device test results are recorded in [TESTING.md](TESTING.md) (Chinese).

## Xiaomi 13 / HyperOS

The tested device reports Android 16 (API 36) and HyperOS 3.0. See [TESTING.md](TESTING.md) (Chinese) for the actual validation scope.

- If **Restricted settings** appears, open the app's system App info page, follow the system prompts to allow access, and then enable the accessibility service.
- Enable **Autostart / Background autostart** for this app and set its battery policy to **No restrictions**. During Xiaomi 13 testing, the service stopped responding in the background under the smart battery restriction policy; testing continued after selecting No restrictions. If you use one-tap cleanup, consider locking App Time Lock in recent apps. Menu locations may differ between system versions.
- Autostart neither replaces accessibility authorization nor guarantees that the system will never disable the service. The connection indicator reports the current connection; opening a controlled app and seeing the reminder is the practical check.
- Revoking permissions, force-stopping, or uninstalling the tool stops protection. Reopen it and check the service status.
- Only normal, single-app fullscreen use is supported. Split screen, picture-in-picture, manufacturer floating windows, app clones, and work profiles are not currently supported.
- Blocking overlays the target app's foreground interface. It does not stop the target process or background audio/video. The target app may briefly be visible as it opens.

## Privacy and required permissions

- No Internet permission, accounts, ads, cloud sync, or usage history.
- Stored data is limited to controlled package names, current allowances, recent task IDs and boot counts, disclosure acknowledgement, sidebar position, and the sidebar visibility preference.
- The accessibility service has system window access capabilities. The code only processes metadata such as window type, focus, IDs, and package names. When an event lacks a package name, it reads only the root node's package name with prefetching disabled. It does not traverse other apps' node trees, read their text, or capture screenshots.
- Vibration permission is used for the brief time-up vibration. Device administrator, ordinary overlay, usage access, and exact alarm permissions are not required.
- System settings, the launcher, phone entry points, and App Time Lock itself cannot be selected as controlled apps.

## Development and builds

Native Kotlin with Compose / Material 3. Minimum Android version: 13. Compile and target SDK: 36.

Pinned versions: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.12.01, and JDK 17.

Modules:

- `app`: main interface, local DataStore, accessibility service, sidebar, and blocking overlay.
- `core`: Android-independent timing state machine, foreground-window rules, and unit tests.
- `fixture`: a dedicated device-test app with no user content; it is not part of the main product.

On the original Windows development machine:

```powershell
.\build.ps1
```

This convenience script defaults to SDK, Gradle cache, and existing Java locations on drive D:. You can set `JAVA_HOME` and `ANDROID_HOME` to your own installation paths beforehand. When Windows has a single HTTP proxy configured, the script passes it to Gradle. Review the script's machine-specific paths before using it on another computer.

For other environments, configure the Android SDK and JDK 17, then run:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The default APK is `app/build/outputs/apk/debug/app-debug.apk`. The distributed APK uses the `personal` build type with code and resource shrinking enabled. Build it with:

```sh
./gradlew :core:test :app:assemblePersonal :app:lintDebug
```

Or, using the Windows convenience script:

```powershell
.\build.ps1 -Tasks ':core:test',':app:assemblePersonal',':app:lintDebug'
```

Its output is `app/build/outputs/apk/personal/app-personal.apk`. Both build types use the local debug signing key for personal testing. Store distribution requires separate production signing and store review. In-place updates require the same signing key; private keys are not included in the source archive. An APK you build yourself may not be able to replace the published APK directly because the signing keys differ.

## Recovery and timing accuracy

Timing uses a monotonic clock. State is saved immediately when switching or reaching the time limit, with checkpoints every five seconds during use. An unexpected process exit may leave up to approximately one checkpoint interval uncounted. Time is not charged retroactively while the service is interrupted. After reconnecting, the service first confirms the current foreground app before resuming timing.

## License

This project uses the [MIT License](LICENSE). You may use, modify, and distribute it while retaining the copyright and license notice. The software is provided as is. Third-party dependencies remain subject to their own licenses.
