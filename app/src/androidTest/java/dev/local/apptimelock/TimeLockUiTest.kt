package dev.local.apptimelock

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.local.apptimelock.core.Session
import dev.local.apptimelock.core.TimerSnapshot
import dev.local.apptimelock.data.AppState
import dev.local.apptimelock.data.InstalledApp
import dev.local.apptimelock.ui.TimeLockApp
import dev.local.apptimelock.ui.TimeLockTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeLockUiTest {
    @get:Rule val compose = createComposeRule()
    private val apps = listOf(InstalledApp("test.a", "测试应用甲", ColorDrawable(Color.GREEN)), InstalledApp("test.b", "测试应用乙", ColorDrawable(Color.BLUE)))
    @Test fun initialScreenClearlyShowsProtectionOffAndConsent() {
        var enabled = false
        compose.setContent { TimeLockTheme { TimeLockApp(AppState(ready = true), apps, true, { _, _ -> }, { enabled = true }, {}) } }
        compose.onNodeWithText("保护未开启").assertIsDisplayed()
        compose.onNodeWithText("开启使用提醒").performClick()
        compose.onNodeWithText("了解并去设置").assertIsDisplayed().performClick()
        assertTrue(enabled)
    }
    @Test fun appSearchAndSelectionReachCorrectPackage() {
        var selected = ""
        compose.setContent { TimeLockTheme { TimeLockApp(AppState(ready = true), apps, true, { pkg, _ -> selected = pkg }, {}, {}) } }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithTag("app_search").performTextInput("甲")
        compose.onNodeWithText("测试应用甲").assertIsDisplayed()
        compose.onNodeWithText("测试应用乙").assertDoesNotExist()
        compose.onNodeWithTag("toggle_test.a").performClick()
        assertEquals("test.a", selected)
    }
    @Test fun remainingAllowanceAndExpiredStateAreVisible() {
        val state = AppState(ready = true, serviceConnected = true, timer = TimerSnapshot(mapOf("test.a" to Session(600_000, 180_000), "test.b" to Session(600_000, 0))))
        compose.setContent { TimeLockTheme { TimeLockApp(state, apps, true, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("提醒服务已连接").assertIsDisplayed()
        compose.onNodeWithText("03:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("时间已到 · 下次重新选择").performScrollTo().assertIsDisplayed()
    }
    @Test fun selectingAnAppWhileServiceIsOffExplainsWhyItWillNotBlockYet() {
        compose.setContent { TimeLockTheme { TimeLockApp(AppState(ready = true), apps, true, { _, _ -> }, {}, {}) } }
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("保护尚未生效").assertIsDisplayed()
        compose.onNodeWithTag("toggle_test.a").performScrollTo().performClick()
        compose.onNodeWithText("了解并去设置").assertIsDisplayed()
    }
}
