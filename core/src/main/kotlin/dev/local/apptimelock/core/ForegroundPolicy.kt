package dev.local.apptimelock.core

enum class WindowKind { APPLICATION, INPUT_METHOD, SYSTEM, OWN_OVERLAY, OTHER_OVERLAY }
data class WindowObservation(
    val id: Int,
    val kind: WindowKind,
    val packageName: String?,
    val focused: Boolean,
    val active: Boolean,
    val pictureInPicture: Boolean = false,
    val taskId: Int? = null,
)
data class ForegroundDecision(val packageName: String? = null, val unsupported: Boolean = false, val taskId: Int? = null)

/** Pure classification of window identity metadata; no page text or node content. */
object ForegroundPolicy {
    fun decide(windows: List<WindowObservation>, previousPackage: String?, screenUsable: Boolean): ForegroundDecision {
        if (!screenUsable) return ForegroundDecision()
        val focused = windows.firstOrNull { it.focused }
        if (focused?.kind == WindowKind.SYSTEM || focused?.kind == WindowKind.OTHER_OVERLAY) return ForegroundDecision()
        val apps = windows.filter { it.kind == WindowKind.APPLICATION }
        if (apps.any { it.pictureInPicture } || apps.size > 1) return ForegroundDecision(unsupported = true)
        val chosen = apps.firstOrNull { it.focused || it.active }
            ?: apps.singleOrNull()?.takeIf {
                (focused?.kind == WindowKind.INPUT_METHOD || focused?.kind == WindowKind.OWN_OVERLAY) &&
                    it.packageName != null && it.packageName == previousPackage
            }
        return ForegroundDecision(chosen?.packageName, taskId = chosen?.taskId)
    }
}
