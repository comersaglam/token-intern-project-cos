package com.example.app_pos.data

import android.content.Context
import androidx.room.Room
import com.example.app_pos.data.db.AppDatabase
import com.example.app_pos.model.Repository

/**
 * Builds the Room database and the [RoomRepository] once, and seeds demo data on first
 * open. Deliberately a plain singleton object (no DI framework) — the app is small, so
 * a manual provider is enough (overengineering avoided). The app calls
 * [RepositoryProvider.get] from its Application/Activity.
 */
object RepositoryProvider {

    @Volatile private var repository: Repository? = null

    fun get(context: Context): Repository =
        repository ?: synchronized(this) {
            repository ?: build(context.applicationContext).also { repository = it }
        }

    /**
     * The already-built repository, for callers with no Context (ViewModels). The app
     * builds it once in Application.onCreate via [get], so this is non-null by the time
     * any screen runs. Kept as a simple accessor instead of a DI framework — the app is
     * small enough that a manual singleton is the right amount of structure.
     */
    val instance: Repository
        get() = repository ?: error("RepositoryProvider.get(context) must run first (Application.onCreate)")

    private fun build(appContext: Context): Repository {
        val db = Room.databaseBuilder(appContext, AppDatabase::class.java, "veresiye.db")
            // Mock phase: if the entity set changes, wipe rather than migrate. Real
            // migrations arrive with the backend phase (schemas are exported for diffing).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(SeedCallback(appContext))
            .build()
        return RoomRepository(db)
    }
}
