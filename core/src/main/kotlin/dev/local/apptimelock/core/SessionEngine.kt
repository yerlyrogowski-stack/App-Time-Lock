package dev.local.apptimelock.core

data class TaskIdentity(val id: Int, val bootCount: Int)

data class Session(val grantedMs: Long = 0, val remainingMs: Long = 0, val task: TaskIdentity? = null) {
    val hasExpired: Boolean get() = grantedMs > 0 && remainingMs == 0L
}

data class TimerSnapshot(
    val sessions: Map<String, Session> = emptyMap(),
    val foregroundPackage: String? = null,
    val screenUsable: Boolean = false,
) {
    val runningPackage: String? get() = foregroundPackage?.takeIf {
        screenUsable && (sessions[it]?.remainingMs ?: 0) > 0
    }
}

/** Pure state machine. Call only from the repository's serialized dispatcher. */
class SessionEngine(restored: Map<String, Session> = emptyMap()) {
    private val sessions = restored.mapValues { (_, session) ->
        session.copy(grantedMs = session.grantedMs.coerceIn(0, MAX_DURATION), remainingMs = session.remainingMs.coerceIn(0, session.grantedMs.coerceIn(0, MAX_DURATION)))
    }.toMutableMap()
    private var foreground: String? = null
    private var usable = false
    private var lastMark: Long? = null

    fun snapshot() = TimerSnapshot(sessions.toMap(), foreground, usable)

    fun advance(now: Long) {
        val previous = lastMark
        if (previous != null && usable) {
            val pkg = foreground
            val session = sessions[pkg]
            if (pkg != null && session != null && session.remainingMs > 0) {
                val delta = (now - previous).coerceAtLeast(0)
                sessions[pkg] = session.copy(remainingMs = (session.remainingMs - delta).coerceAtLeast(0))
            }
        }
        lastMark = now
    }

    fun setForeground(packageName: String?, screenUsable: Boolean, now: Long, task: TaskIdentity? = null) {
        advance(now)
        // A disappearing window, a long pause, and an unknown task are NOT evidence
        // of removal. Compare only positively identified tasks within the same boot.
        if (screenUsable && task != null && task.id >= 0 && task.bootCount >= 0) {
            val session = sessions[packageName]
            if (session != null && packageName != null) {
                val old = session.task
                sessions[packageName] = if (old != null && old.bootCount == task.bootCount && old.id != task.id) {
                    Session(task = task)
                } else session.copy(task = task)
            }
        }
        foreground = packageName
        usable = screenUsable
    }

    fun setControlled(packageName: String, enabled: Boolean, now: Long) {
        advance(now)
        if (enabled) sessions.putIfAbsent(packageName, Session()) else sessions.remove(packageName)
    }

    /** A stale button must not grant time to a background or removed app. */
    fun grant(packageName: String, minutes: Int, now: Long): Boolean {
        advance(now)
        if (minutes !in 1..180 || !usable || foreground != packageName || packageName !in sessions) return false
        if (sessions.getValue(packageName).remainingMs > 0) return false
        val duration = minutes * 60_000L
        sessions[packageName] = Session(duration, duration, sessions.getValue(packageName).task)
        return true
    }

    companion object { const val MAX_DURATION = 180 * 60_000L }
}

fun formatRemaining(milliseconds: Long): String {
    val seconds = ((milliseconds.coerceAtLeast(0) + 999) / 1000)
    return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
