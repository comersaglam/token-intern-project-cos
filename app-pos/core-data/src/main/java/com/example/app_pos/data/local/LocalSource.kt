package com.example.app_pos.data.local

import com.example.app_pos.model.Repository
import com.example.app_pos.model.User
import kotlinx.coroutines.flow.Flow

/**
 * What the composing repository needs from on-device storage.
 *
 * It is the full [Repository] surface plus [observeAllUsers]. The extra method exists
 * because OfflineFirstRepository resolves "who is signed in" from the PERSISTED session
 * rather than from the local source's own RAM mock, so it needs the user table unfiltered.
 *
 * Kept as an interface for one concrete reason: it makes the session logic testable without
 * Room. A JVM unit test can supply a trivial implementation and assert on the gate — the
 * thing this phase actually changes — instead of standing up a database to reach it.
 */
interface LocalSource : Repository {

    /** Every stored user, with no session filter applied. */
    fun observeAllUsers(): Flow<List<User>>
}
