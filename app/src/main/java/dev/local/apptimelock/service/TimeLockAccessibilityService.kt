package dev.local.apptimelock.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.local.apptimelock.TimeLockApplication
import dev.local.apptimelock.core.ForegroundPolicy
import dev.local.apptimelock.core.TimerSnapshot
import dev.local.apptimelock.core.WindowKind
import dev.local.apptimelock.core.WindowObservation
import dev.local.apptimelock.core.WindowTaskMetadata
import dev.local.apptimelock.core.TaskIdentity
import dev.local.apptimelock.data.AppState
import kotlinx.coroutines.*

class TimeLockAccessibilityService : AccessibilityService() {
    private val store get() = (application as TimeLockApplication).store
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlays: OverlayController
    private val packageByWindow = ConcurrentHashMap<Int, String>()
    private val refreshMutex = Mutex()
    private var windowGeneration = 0L
    private var previousDecision: String? = null
    private var lastTimer = TimerSnapshot()
    private var registered = false
    private var active = false
    private var previouslyUnsupported = false
    private var refreshJob: Job? = null
    private var lastObservations: List<WindowObservation> = emptyList()
    private var lastEventMetadata = "none"
    private val bootCount by lazy { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { scheduleRefresh() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (active) return
        active = true
        overlays = OverlayController(this,
            onStart = { pkg, minutes, result -> serviceScope.launch {
                refreshForeground()
                result(store.grant(pkg, minutes))
            } },
            onHome = {
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Keep the barrier until the Home window is actually observed.
                serviceScope.launch { delay(100); refreshForeground() }
            },
            onPosition = { position -> serviceScope.launch { runCatching { store.saveBadgePosition(position) } } },
        )
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_NOT_EXPORTED)
        registered = true
        store.setServiceConnected(true)
        serviceScope.launch {
            store.state.collect { state ->
                if (!active || !state.ready) return@collect
                try { render(state) }
                catch (_: RuntimeException) {
                    runCatching { overlays.hideAll() }
                    store.setServiceConnected(false)
                    store.reportError("提醒窗口暂时无法显示，请重新开启无障碍服务。")
                }
            }
        }
        serviceScope.launch {
            while (isActive && active) {
                refreshForeground()
                delay(250)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || event == null) return
        windowGeneration++
        lastEventMetadata = "type=${event.eventType} id=${event.windowId} package=${event.packageName}"
        // Never access event.text, contentDescription, source, or window titles.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId >= 0) {
            event.packageName?.toString()?.let { packageByWindow[event.windowId] = it }
        }
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        if (!active || refreshJob?.isActive == true) return
        refreshJob = serviceScope.launch { delay(20); refreshForeground() }
    }

    private suspend fun refreshForeground() {
        if (!active) return
        refreshMutex.withLock {
        if (!active) return@withLock
        val usable = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val ownIds = overlays.ownWindowIds
        val generation = windowGeneration
        val observations = withContext(Dispatchers.Default) { runCatching {
            windows.map { window ->
                // Some Android 16 / HyperOS builds omit package names from all
                // delivered window events. Read only root PACKAGE METADATA, with
                // prefetch disabled. Do not read text or traverse any children.
                var pkg = packageByWindow[window.id]
                if (pkg == null && window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    pkg = window.getRoot(0)?.packageName?.toString()
                    if (pkg != null) packageByWindow[window.id] = pkg
                }
                val kind = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
                    AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> if (window.id in ownIds || pkg == packageName) WindowKind.OWN_OVERLAY else WindowKind.OTHER_OVERLAY
                    else -> WindowKind.OTHER_OVERLAY
                }
                // Parse only the task number from system window metadata, never log
                // the description (which can also contain a window title).
                val taskId = if (kind == WindowKind.APPLICATION) WindowTaskMetadata.parse(window.toString()) else null
                WindowObservation(window.id, kind, pkg, window.isFocused, window.isActive, window.isInPictureInPictureMode, taskId)
            }
        }.getOrDefault(emptyList()) }
        if (generation != windowGeneration) return@withLock
        lastObservations = observations
        val decision = ForegroundPolicy.decide(observations, previousDecision, usable)
        if (decision.unsupported && !previouslyUnsupported && observations.any { it.packageName in store.state.value.timer.sessions }) {
            Toast.makeText(this, "请切回全屏使用，当前模式暂不计时", Toast.LENGTH_SHORT).show()
        }
        previouslyUnsupported = decision.unsupported
        previousDecision = decision.packageName
        store.updateForeground(decision.packageName, usable && !decision.unsupported, decision.unsupported,
            decision.taskId?.takeIf { bootCount >= 0 }?.let { TaskIdentity(it, bootCount) })
        val visibleIds = observations.map { it.id }.toSet()
        if (packageByWindow.size > 128) packageByWindow.keys.retainAll(visibleIds)
        }
    }

    private fun render(state: AppState) {
        val timer = state.timer
        val pkg = timer.foregroundPackage
        val session = timer.sessions[pkg]
        if (!state.serviceConnected || !timer.screenUsable || pkg == null || session == null) {
            overlays.hideAll()
        } else if (session.remainingMs > 0) {
            if (state.showCountdown) overlays.showBadge(pkg, session.remainingMs, state.badgePosition)
            else overlays.hideAll()
        } else {
            if (lastTimer.sessions[pkg]?.remainingMs?.let { it > 0 } == true && session.hasExpired) {
                getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            overlays.showGate(pkg, session)
        }
        lastTimer = timer
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (active) { overlays.hideAll(); render(store.state.value); scheduleRefresh() }
    }

    override fun onInterrupt() {
        if (active) { overlays.hideAll(); serviceScope.launch { store.updateForeground(null, false) } }
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.println("TimeLock service active=$active lastEvent=$lastEventMetadata")
        writer.println("Windows=$lastObservations")
        writer.println("Current=${store.state.value.timer.foregroundPackage} usable=${store.state.value.timer.screenUsable} unsupported=${store.state.value.unsupportedWindow}")
        writer.println("Controlled=${store.state.value.timer.sessions}")
        writer.println("Error=${store.state.value.error}")
        writer.println("Sidebar enabled=${store.state.value.showCountdown} ${overlays.badgeStatus}")
    }

    override fun onUnbind(intent: Intent?): Boolean { stopProtection(); return super.onUnbind(intent) }
    override fun onDestroy() { stopProtection(); super.onDestroy() }
    private fun stopProtection() {
        if (!active) return
        active = false
        if (registered) { unregisterReceiver(receiver); registered = false }
        overlays.hideAll()
        serviceScope.cancel()
        store.setServiceConnected(false)
    }
}
