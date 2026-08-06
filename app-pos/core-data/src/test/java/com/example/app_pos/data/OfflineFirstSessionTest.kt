package com.example.app_pos.data

import com.example.app_pos.network.api.ApprovalApi
import com.example.app_pos.network.api.AuthApi
import com.example.app_pos.network.api.BuyerApi
import com.example.app_pos.network.api.CustomerApi
import com.example.app_pos.network.api.LedgerApi
import com.example.app_pos.network.api.SyncApi
import com.example.app_pos.network.api.UserApi
import com.example.app_pos.data.remote.RemoteDataSource
import com.example.app_pos.data.sync.SyncEngine
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * The session gate, which is what phase 7 actually changes.
 *
 * Before this phase the session lived in a MutableStateFlow inside the Room repository, so
 * it died with the process; now it comes from the persisted TokenStore. These tests pin the
 * behaviour the login gate depends on — MainActivity picks its start destination from
 * isSessionValid() synchronously, before the nav graph exists, so a regression here shows up
 * as "the app asks for a login it should not" (or worse, does not ask when it should).
 *
 * No network is involved: the repository is offline-first and none of these paths call out.
 */
class OfflineFirstSessionTest {

    private val tokens = FakeTokenStore()

    private fun repo(vararg users: com.example.app_pos.model.User): OfflineFirstRepository {
        val local = FakeLocalSource(users.toList())
        val remote = unusedRemote()
        val moshi = Moshi.Builder().build()
        return OfflineFirstRepository(
            local = local,
            remote = remote,
            syncEngine = SyncEngine(local, remote, moshi),
            tokens = tokens,
            moshi = moshi
        )
    }

    @Test
    fun `no session means the gate is closed`() {
        val repo = repo(testUser())

        assertFalse(repo.isSessionValid())
        assertNull(repo.currentSellerId())
    }

    @Test
    fun `signing in opens the gate and names the seller`() = runTest {
        val repo = repo(testUser())

        assertTrue(repo.login("05554443322"))

        assertTrue(repo.isSessionValid())
        assertEquals("u_owner", repo.currentSellerId())
    }

    @Test
    fun `an unknown number cannot sign in`() = runTest {
        val repo = repo(testUser())

        assertFalse(repo.login("05550001122"))

        assertFalse(repo.isSessionValid())
    }

    @Test
    fun `a null number cannot sign in`() = runTest {
        val repo = repo(testUser())

        assertFalse(repo.login(null))

        assertFalse(repo.isSessionValid())
    }

    /**
     * The reopen-the-app case, and the whole point of the phase: the store keeps the
     * session, so a fresh repository over the same store is already signed in. Before,
     * this was a new MutableStateFlow(null) and the merchant saw the login screen again.
     */
    @Test
    fun `a session outlives the repository instance`() = runTest {
        repo(testUser()).login("05554443322")

        val afterRestart = repo(testUser())

        assertTrue(afterRestart.isSessionValid())
        assertEquals("u_owner", afterRestart.currentSellerId())
    }

    @Test
    fun `an expired session closes the gate`() = runTest {
        val repo = repo(testUser())
        repo.login("05554443322")
        assertTrue(repo.isSessionValid())

        // Past the 7-day TTL the login minted.
        tokens.now += 8L * 24 * 60 * 60 * 1000

        assertFalse(repo.isSessionValid())
        assertNull(repo.currentSellerId())
    }

    @Test
    fun `signing out clears the stored session`() = runTest {
        val repo = repo(testUser())
        repo.login("05554443322")

        repo.logout()

        assertFalse(repo.isSessionValid())
        assertNull(repo.currentSellerId())
    }

    @Test
    fun `the current user resolves from the persisted session`() = runTest {
        val repo = repo(testUser())

        assertNull("signed out means no user", repo.observeCurrentUser().first())

        repo.login("05554443322")

        val user = repo.observeCurrentUser().first()
        assertNotNull(user)
        assertEquals("u_owner", user!!.userId)
    }

    /** Sign-out has to reach the screens, not just the gate — the profile listens on this. */
    @Test
    fun `signing out emits a null user`() = runTest {
        val repo = repo(testUser())
        repo.login("05554443322")
        assertNotNull(repo.observeCurrentUser().first())

        repo.logout()

        assertNull(repo.observeCurrentUser().first())
    }

    /**
     * A stored session whose user is gone (the database was wiped, the row was removed)
     * must not resolve to some other account. Null is the safe answer.
     */
    @Test
    fun `a session for an unknown user resolves to null`() = runTest {
        val repo = repo(testUser())
        repo.login("05554443322")

        val emptyDb = repo()  // same token store, no users

        assertNull(emptyDb.observeCurrentUser().first())
    }

    /**
     * A RemoteDataSource that is constructed but never called: phase 7 wires the network in
     * without putting it on any screen's path. Retrofit only needs a syntactically valid
     * base URL to create the interfaces — no server is started, and nothing here would reach
     * one anyway.
     */
    private fun unusedRemote(): RemoteDataSource {
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return RemoteDataSource(
            authApi = retrofit.create(AuthApi::class.java),
            userApi = retrofit.create(UserApi::class.java),
            customerApi = retrofit.create(CustomerApi::class.java),
            ledgerApi = retrofit.create(LedgerApi::class.java),
            buyerApi = retrofit.create(BuyerApi::class.java),
            approvalApi = retrofit.create(ApprovalApi::class.java),
            syncApi = retrofit.create(SyncApi::class.java),
            moshi = moshi
        )
    }
}
