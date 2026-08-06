package com.example.app_pos

import android.app.Application
import com.example.app_pos.network.auth.TokenStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Hilt's entry point: the annotation generates the application-wide component every
 * @AndroidEntryPoint class and @HiltViewModel resolves against.
 *
 * Its one job beyond that is priming the token store — the single disk read that loads a
 * persisted session into memory. Everything afterwards is served from that cache, which is
 * what lets isSessionValid() stay synchronous without touching disk on the main thread.
 */
@HiltAndroidApp
class App : Application() {

    @Inject lateinit var tokenStore: TokenStore

    override fun onCreate() {
        super.onCreate()

        // Deliberately BLOCKING, and it is the narrow case where that is right.
        //
        // MainActivity picks its navigation start destination from isSessionValid() in
        // onCreate, before the nav graph exists, so it cannot await anything. Priming in a
        // background coroutine would race that read: lose the race and a signed-in merchant
        // gets the login screen — the exact bug this phase exists to fix, appearing
        // intermittently, which is the worst way for it to appear.
        //
        // The cost is one small DataStore read on a file the process just opened anyway,
        // once per launch, at a point where no frame has been drawn yet.
        runBlocking { tokenStore.prime() }
    }
}
