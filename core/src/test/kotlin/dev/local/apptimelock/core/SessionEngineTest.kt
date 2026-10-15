package dev.local.apptimelock.core

import org.junit.Assert.*
import org.junit.Test

class SessionEngineTest {
    private fun engine(): SessionEngine = SessionEngine().apply {
        setControlled("a", true, 0)
        setForeground("a", true, 0)
    }

    @Test fun `permission and explicit selection are required`() {
        val e = engine()
        e.advance(80_000)
        assertEquals(0L, e.snapshot().sessions.getValue("a").remainingMs)
        assertFalse(e.grant("a", 0, 80_000))
        assertFalse(e.grant("a", 181, 80_000))
        assertTrue(e.grant("a", 10, 80_000))
        assertFalse(e.grant("a", 20, 80_001))
    }

    @Test fun `seven minutes then a long interruption leaves three minutes`() {
        val e = engine()
        e.grant("a", 10, 0)
        e.setForeground("messenger", true, 420_000)
        e.setForeground("a", true, 3_600_000)
        assertEquals(180_000L, e.snapshot().sessions.getValue("a").remainingMs)
        e.advance(3_660_000)
        assertEquals(120_000L, e.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `each controlled app uses its own allowance`() {
        val e = engine()
        e.grant("a", 10, 0)
        e.setControlled("b", true, 0)
        e.setForeground("b", true, 60_000)
        e.grant("b", 20, 60_000)
        e.setForeground("a", true, 180_000)
        assertEquals(540_000L, e.snapshot().sessions.getValue("a").remainingMs)
        assertEquals(1_080_000L, e.snapshot().sessions.getValue("b").remainingMs)
    }

    @Test fun `screen off and restart do not charge elapsed downtime`() {
        val e = engine()
        e.grant("a", 10, 0)
        e.setForeground("a", false, 60_000)
        e.advance(999_000)
        val restored = SessionEngine(e.snapshot().sessions)
        restored.advance(500)
        assertNull(restored.snapshot().runningPackage)
        restored.setForeground("a", true, 10_000)
        restored.advance(11_000)
        assertEquals(539_000L, restored.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `deadline clamps to zero and permits explicit renewal`() {
        val e = engine()
        e.grant("a", 1, 0)
        e.advance(65_000)
        assertTrue(e.snapshot().sessions.getValue("a").hasExpired)
        assertNull(e.snapshot().runningPackage)
        e.advance(200_000)
        assertEquals(0L, e.snapshot().sessions.getValue("a").remainingMs)
        assertTrue(e.grant("a", 20, 200_000))
        assertEquals(1_200_000L, e.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `duplicate window events never reset allowance`() {
        val e = engine()
        e.grant("a", 10, 0)
        repeat(60) { e.setForeground("a", true, (it + 1) * 1000L) }
        assertEquals(540_000L, e.snapshot().sessions.getValue("a").remainingMs)
    }

    @Test fun `unchecking clears session and stale grant is rejected`() {
        val e = engine()
        e.grant("a", 10, 0)
        e.setControlled("a", false, 1000)
        assertFalse(e.grant("a", 10, 1000))
        e.setControlled("a", true, 2000)
        assertEquals(Session(), e.snapshot().sessions["a"])
        e.setForeground("other", true, 2000)
        assertFalse(e.grant("a", 10, 2000))
    }

    @Test fun `display rounds up remaining partial seconds`() {
        assertEquals("00:01", formatRemaining(1))
        assertEquals("10:00", formatRemaining(600_000))
        assertEquals("180:00", formatRemaining(10_800_000))
        assertEquals("00:00", formatRemaining(-1))
    }
}
