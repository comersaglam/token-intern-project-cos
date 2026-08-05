package com.example.app_pos

import android.app.Application
import com.example.app_pos.data.RepositoryProvider

/**
 * Builds the Room-backed repository once, at process start, so every screen can reach
 * it via RepositoryProvider.instance without threading a Context through ViewModels.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RepositoryProvider.get(this)
    }
}
