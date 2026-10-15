package dev.local.apptimelock.core

/**
 * Compatibility adapter for the task identity included in AOSP / HyperOS window
 * diagnostics. Android has no public task-id getter here. This format is not an
 * SDK contract: unknown/changed formats return null and must never reset a timer.
 * Keep only the numeric identity, never store or log the input description.
 */
object WindowTaskMetadata {
    private val identity = Regex(", taskId=(-?\\d+), type=TYPE_APPLICATION, layer=-?\\d+, region=")
    fun parse(description: String): Int? = identity.findAll(description).lastOrNull()
        ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 0 }
}
