package dev.local.apptimelock.core

import org.junit.Assert.*
import org.junit.Test

class TaskSessionTest {
    private val oldTask = TaskIdentity(100, 8)
    private fun started() = SessionEngine().apply {
        setControlled("a", true, 0)
        setForeground("a", true, 0, oldTask)
        grant("a", 10, 0)
    }

    @Test fun `ordinary background and return keep allowance even after long delay`() {
        val e = started()
        e.setForeground("home", true, 10_000)
        e.setForeground("a", true, 9_000_000, oldTask)
        assertEquals(590_000L, e.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `new task clears old allowance before publishing foreground`() {
        val e = started()
        e.setForeground("home", true, 10_000)
        val next = TaskIdentity(101, 8)
        e.setForeground("a", true, 11_000, next)
        assertEquals(Session(task = next), e.snapshot().sessions["a"])
        assertNull(e.snapshot().runningPackage)
        assertTrue(e.grant("a", 20, 11_000))
        assertEquals(next, e.snapshot().sessions.getValue("a").task)
    }

    @Test fun `unknown windows lock screen and same task activity changes do not reset`() {
        val e = started()
        e.setForeground(null, true, 10_000)
        e.setForeground("a", false, 20_000, TaskIdentity(999, 8))
        e.setForeground("a", true, 30_000)
        e.setForeground("a", true, 31_000, oldTask)
        assertEquals(589_000L, e.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `restart of time lock preserves task identity and detects later removal`() {
        val restored = SessionEngine(started().snapshot().sessions)
        restored.setForeground("a", true, 1_000, oldTask)
        assertEquals(600_000L, restored.snapshot().sessions.getValue("a").remainingMs)
        restored.setForeground("a", true, 2_000, TaskIdentity(200, 8))
        assertEquals(0L, restored.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `phone reboot and migration from old sessions preserve balance`() {
        val e = started()
        e.setForeground("a", true, 0, TaskIdentity(300, 9))
        assertEquals(600_000L, e.snapshot().sessions.getValue("a").remainingMs)
        val migrated = SessionEngine(mapOf("a" to Session(600_000, 50_000)))
        migrated.setForeground("a", true, 0, oldTask)
        assertEquals(50_000L, migrated.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `metadata adapter rejects missing negative and changed formats`() {
        assertEquals(100, WindowTaskMetadata.parse("AccessibilityWindowInfo[title=example, displayId=0, id=5, taskId=100, type=TYPE_APPLICATION, layer=0, region=x]"))
        assertNull(WindowTaskMetadata.parse("taskId=100"))
        assertNull(WindowTaskMetadata.parse(", taskId=-1, type=TYPE_APPLICATION, layer=0, region=x"))
        assertNull(WindowTaskMetadata.parse(", taskId=100, type=TYPE_SYSTEM, layer=0, region=x"))
        assertNull(WindowTaskMetadata.parse(", taskId=9999999999999, type=TYPE_APPLICATION, layer=0, region=x"))
    }

    @Test fun `text earlier in description cannot override actual trailing metadata`() {
        val description = "title=, taskId=999, type=TYPE_APPLICATION, layer=0, region=example, " +
            "displayId=0, id=5, taskId=100, type=TYPE_APPLICATION, layer=0, region=x]"
        assertEquals(100, WindowTaskMetadata.parse(description))
    }
}
