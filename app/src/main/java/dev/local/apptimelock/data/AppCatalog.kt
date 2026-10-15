package dev.local.apptimelock.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

object AppCatalog {
    private val flags = PackageManager.ResolveInfoFlags.of(0)
    fun protectedPackages(context: Context): Set<String> = buildSet {
        add(context.packageName)
        addAll(listOf("com.android.settings", "com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.android.emergency", "com.android.permissioncontroller", "com.google.android.permissioncontroller"))
        val pm = context.packageManager
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Intent.ACTION_DIAL),
            Intent(Settings.ACTION_SETTINGS),
        ).forEach { intent -> pm.queryIntentActivities(intent, flags).forEach { add(it.activityInfo.packageName) } }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
    }

    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val excluded = protectedPackages(context)
        val collator = Collator.getInstance(Locale.CHINA)
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags,
        ).asSequence().filter { it.activityInfo.packageName !in excluded }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info -> runCatching {
                InstalledApp(info.activityInfo.packageName, info.loadLabel(context.packageManager).toString(), info.loadIcon(context.packageManager))
            }.getOrNull() }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }.toList()
    }
}
