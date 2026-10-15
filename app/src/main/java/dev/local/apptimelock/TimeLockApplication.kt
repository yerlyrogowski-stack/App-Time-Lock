package dev.local.apptimelock

import android.app.Application
import dev.local.apptimelock.data.AppStore

class TimeLockApplication : Application() {
    val store: AppStore by lazy { AppStore(this) }
    override fun onCreate() {
        super.onCreate()
        store.initialize()
    }
}
