package dev.local.apptimelock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.local.apptimelock.data.AppCatalog
import dev.local.apptimelock.data.InstalledApp
import dev.local.apptimelock.ui.TimeLockApp
import dev.local.apptimelock.ui.TimeLockTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val store get() = (application as TimeLockApplication).store
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var appsLoaded by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by store.state.collectAsStateWithLifecycle()
            TimeLockTheme {
                TimeLockApp(
                    state = state, apps = apps, appsLoaded = appsLoaded,
                    onToggle = { pkg, enabled -> lifecycleScope.launch { store.setControlled(pkg, enabled) } },
                    onEnable = { lifecycleScope.launch {
                        runCatching { store.acceptConsent() }.onSuccess { openAccessibility() }
                            .onFailure { store.reportError("无法保存设置，请检查手机存储空间。") }
                    } },
                    onAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    onCountdownToggle = { enabled -> lifecycleScope.launch { store.setShowCountdown(enabled) } },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { AppCatalog.load(this@MainActivity) }.onSuccess {
                apps = it
                appsLoaded = true
                store.prunePackages(it.map { app -> app.packageName }.toSet())
            }.onFailure { store.reportError("应用列表暂时无法读取，请稍后重新打开。") }
        }
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
