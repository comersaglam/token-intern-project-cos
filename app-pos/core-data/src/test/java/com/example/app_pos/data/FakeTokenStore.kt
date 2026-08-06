package com.example.app_pos.data

import com.example.app_pos.network.auth.StoredSession
import com.example.app_pos.network.auth.TokenStore
import com.example.app_pos.network.dto.SessionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * An in-memory [TokenStore] for tests: DataStoreTokenStore's behaviour without Android.
 *
 * It deliberately keeps the two properties the real one is relied upon for — the cache is
 * what makes the reads synchronous, and validity is judged against [now] — so a test that
 * passes here is testing the same contract the repository depends on. What it drops is
 * only the disk.
 *
 * [now] is settable so expiry can be tested without sleeping. It starts at the real clock
 * because the repository mints its expiry from System.currentTimeMillis(); a fake epoch
 * would make every freshly-saved session look years expired.
 */
class FakeTokenStore(var now: Long = System.currentTimeMillis()) : TokenStore {

    private val cached = MutableStateFlow<StoredSession?>(null)

    /** How many times the store was primed — proves the disk read happens once. */
    var primeCount = 0
        private set

    override suspend fun prime() {
        primeCount++
    }

    override fun accessTokenOrNull(): String? = valid()?.accessToken

    override fun refreshTokenOrNull(): String? = cached.value?.refreshToken

    override fun currentUserIdOrNull(): String? = valid()?.userId

    override fun isValid(): Boolean = valid() != null

    override fun observeSession(): Flow<StoredSession?> = cached.asStateFlow()

    override suspend fun save(session: SessionDto) {
        cached.value = StoredSession(
            accessToken = session.token,
            refreshToken = session.refreshToken,
            userId = session.user.userId,
            expiresAtMillis = parseExpiry(session.expiresAt)
        )
    }

    override suspend fun clear() {
        cached.value = null
    }

    private fun valid(): StoredSession? = cached.value?.takeIf { it.isValid(now) }

    private fun parseExpiry(raw: String): Long =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(raw)?.time ?: 0L
        }.getOrDefault(0L)

    companion object {
        /** ISO-8601 UTC, the format the repository mints and the store parses back. */
        fun iso(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))
    }
}
