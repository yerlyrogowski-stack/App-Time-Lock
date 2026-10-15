package dev.local.apptimelock.core

import org.junit.Assert.*
import org.junit.Test

class ForegroundPolicyTest {
    private val app = WindowObservation(1, WindowKind.APPLICATION, "a", false, true)
    @Test fun `keyboard and own overlay do not steal an app's session`() {
        for (kind in listOf(WindowKind.INPUT_METHOD, WindowKind.OWN_OVERLAY)) {
            val overlay = WindowObservation(2, kind, null, true, true)
            assertEquals("a", ForegroundPolicy.decide(listOf(overlay, app), "a", true).packageName)
        }
    }
    @Test fun `notification shade and lockscreen pause`() {
        val shade = WindowObservation(2, WindowKind.SYSTEM, "system", true, true)
        assertNull(ForegroundPolicy.decide(listOf(shade, app), "a", true).packageName)
        assertNull(ForegroundPolicy.decide(listOf(app), "a", false).packageName)
    }
    @Test fun `unknown foreground does not reuse old package`() {
        assertNull(ForegroundPolicy.decide(listOf(app.copy(packageName = null)), "a", true).packageName)
    }
    @Test fun `multiwindow and pip are explicit unsupported states`() {
        assertTrue(ForegroundPolicy.decide(listOf(app, app.copy(id = 2, packageName = "b")), "a", true).unsupported)
        assertTrue(ForegroundPolicy.decide(listOf(app.copy(pictureInPicture = true)), "a", true).unsupported)
    }
    @Test fun `activity window changes keep task identity and overlays cannot replace it`() {
        val first = app.copy(id = 5, taskId = 100)
        val next = first.copy(id = 6)
        val overlay = WindowObservation(7, WindowKind.OWN_OVERLAY, null, true, true, taskId = 999)
        assertEquals(100, ForegroundPolicy.decide(listOf(first), "a", true).taskId)
        assertEquals(100, ForegroundPolicy.decide(listOf(overlay, next), "a", true).taskId)
    }
}
