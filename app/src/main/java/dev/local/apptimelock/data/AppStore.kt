package dev.local.apptimelock.data

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.local.apptimelock.core.Session
import dev.local.apptimelock.core.SessionEngine
import dev.local.apptimelock.core.TaskIdentity
import dev.local.apptimelock.core.TimerSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private val Context.timeLockDataStore by preferencesDataStore("time_lock")

data class BadgePosition(val right: Boolean = true, val verticalFraction: Float = 0.25f)
data class AppState(
    val ready: Boolean = false,
    val timer: TimerSnapshot = TimerSnapshot(),
    val serviceConnected: Boolean = false,
    val unsupportedWindow: Boolean = false,
    val consentAccepted: Boolean = false,
    val badgePosition: BadgePosition = BadgePosition(),
    val showCountdown: Boolean = true,
    val error: String? = null,
)

class AppStore(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private var engine = SessionEngine()
    private var lastSave = 0L
    private var started = false
    private val sessionsKey = stringPreferencesKey("sessions_v1")
    private val consentKey = booleanPreferencesKey("accessibility_consent_v1")
    private val badgeRightKey = booleanPreferencesKey("badge_right")
    private val badgeYKey = floatPreferencesKey("badge_y")
    private val showCountdownKey = booleanPreferencesKey("show_countdown")

    fun initialize() {
        if (started) return
        started = true
        scope.launch {
            try {
                val preferences = context.timeLockDataStore.data.first()
                val blocked = AppCatalog.protectedPackages(context)
                val json = JSONObject(preferences[sessionsKey] ?: "{}")
                val restored = buildMap {
                    for (key in json.keys()) {
                        if (key in blocked) continue
                        val session = json.optJSONObject(key) ?: continue
                        val taskId = session.optInt("taskId", -1)
                        val boot = session.optInt("bootCount", -1)
                        put(key, Session(session.optLong("granted"), session.optLong("remaining"),
                            if (taskId >= 0 && boot >= 0) TaskIdentity(taskId, boot) else null))
                    }
                }
                engine = SessionEngine(restored)
                mutableState.value = mutableState.value.copy(
                    ready = true, timer = engine.snapshot(), consentAccepted = preferences[consentKey] ?: false,
                    badgePosition = BadgePosition(preferences[badgeRightKey] ?: true, (preferences[badgeYKey] ?: 0.25f).coerceIn(0f, 1f)),
                    showCountdown = preferences[showCountdownKey] ?: true,
                )
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(ready = true, error = "无法读取本地配置。请检查手机存储空间后重新打开。")
            } finally { initialized.complete(Unit) }
        }
    }

    private fun publish() { mutableState.value = mutableState.value.copy(timer = engine.snapshot()) }

    private suspend fun save() {
        val json = JSONObject()
        engine.snapshot().sessions.forEach { (pkg, session) ->
            json.put(pkg, JSONObject().put("granted", session.grantedMs).put("remaining", session.remainingMs)
                .put("taskId", session.task?.id ?: -1).put("bootCount", session.task?.bootCount ?: -1))
        }
        context.timeLockDataStore.edit { it[sessionsKey] = json.toString() }
        lastSave = SystemClock.elapsedRealtime()
    }

    private suspend fun saveOrReport(): Boolean = try {
        save()
        true
    } catch (_: Exception) {
        mutableState.value = mutableState.value.copy(error = "剩余时间暂时无法保存，请检查手机存储空间。")
        false
    }

    suspend fun setControlled(pkg: String, enabled: Boolean) {
        initialized.await()
        mutex.withLock {
            if (pkg in AppCatalog.protectedPackages(context)) return@withLock
            engine.setControlled(pkg, enabled, SystemClock.elapsedRealtime())
            publish()
            saveOrReport()
        }
    }

    suspend fun prunePackages(installed: Set<String>) {
        initialized.await()
        mutex.withLock {
            val stale = engine.snapshot().sessions.keys - installed
            if (stale.isEmpty()) return@withLock
            stale.forEach { engine.setControlled(it, false, SystemClock.elapsedRealtime()) }
            publish()
            saveOrReport()
        }
    }

    suspend fun updateForeground(pkg: String?, usable: Boolean, unsupported: Boolean = false, task: TaskIdentity? = null) {
        initialized.await()
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val before = engine.snapshot()
            engine.setForeground(pkg, usable, now, task)
            val after = engine.snapshot()
            mutableState.value = mutableState.value.copy(timer = after, unsupportedWindow = unsupported)
            val expired = before.sessions.any { (key, value) -> value.remainingMs > 0 && after.sessions[key]?.remainingMs == 0L }
            val taskChanged = before.sessions[pkg]?.task != after.sessions[pkg]?.task
            if (taskChanged || before.foregroundPackage != pkg || before.screenUsable != usable || expired || now - lastSave >= 5000) saveOrReport()
        }
    }

    suspend fun grant(pkg: String, minutes: Int): Boolean {
        initialized.await()
        return mutex.withLock {
            val before = engine.snapshot()
            if (!engine.grant(pkg, minutes, SystemClock.elapsedRealtime())) return@withLock false
            if (!saveOrReport()) {
                engine = SessionEngine(before.sessions)
                engine.setForeground(before.foregroundPackage, before.screenUsable, SystemClock.elapsedRealtime())
                publish()
                return@withLock false
            }
            publish()
            true
        }
    }

    suspend fun acceptConsent() {
        initialized.await()
        context.timeLockDataStore.edit { it[consentKey] = true }
        mutableState.value = mutableState.value.copy(consentAccepted = true)
    }

    suspend fun setShowCountdown(enabled: Boolean) {
        initialized.await()
        try {
            context.timeLockDataStore.edit { it[showCountdownKey] = enabled }
            mutableState.value = mutableState.value.copy(showCountdown = enabled)
        } catch (_: Exception) { reportError("侧边栏设置保存失败，请重试。") }
    }

    suspend fun saveBadgePosition(position: BadgePosition) {
        initialized.await()
        mutableState.value = mutableState.value.copy(badgePosition = position)
        context.timeLockDataStore.edit { it[badgeRightKey] = position.right; it[badgeYKey] = position.verticalFraction.coerceIn(0f, 1f) }
    }

    fun setServiceConnected(connected: Boolean) {
        mutableState.value = mutableState.value.copy(serviceConnected = connected)
        if (!connected) scope.launch { updateForeground(null, false) }
    }

    fun reportError(message: String) { mutableState.value = mutableState.value.copy(error = message) }
}
