package com.ryanjames.lunar.sync

import com.ryanjames.lunar.createTestLunarAppState
import com.ryanjames.lunar.createTestPlatformRuntime
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.SupabasePublicStorageSettings
import com.ryanjames.lunar.platform.UnavailablePdfPageRenderer
import com.ryanjames.lunar.testLocalFilesSource
import com.ryanjames.lunar.ui.SourceStatusTone
import com.ryanjames.lunar.ui.formatRelativeTime
import com.ryanjames.lunar.ui.sourceStatusLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SourceSyncStatusTest {

    @Test
    fun successfulRefreshRecordsSyncTimeAndNoError() = runBlocking {
        val clock = MutableClock(10_000L)
        val manager = syncManager(SupabaseHttpClient(), clock)
        val source = supabaseSource("bucket_a")

        manager.refreshSource(source)

        val status = assertNotNull(manager.state.value.sourceStatuses[source.id])
        assertEquals(10_000L, status.lastSyncedEpochMillis)
        assertEquals(10_000L, status.lastAttemptEpochMillis)
        assertNull(status.lastError)
    }

    @Test
    fun failedRefreshRecordsErrorAndKeepsPreviousSyncTime() = runBlocking {
        val clock = MutableClock(10_000L)
        val httpClient = SupabaseHttpClient()
        val manager = syncManager(httpClient, clock)
        val source = supabaseSource("bucket_a")

        manager.refreshSource(source)
        clock.nowMillis = 70_000L
        httpClient.failure = "HTTP 503 from Supabase"
        manager.refreshSource(source)

        val failed = assertNotNull(manager.state.value.sourceStatuses[source.id])
        assertEquals(10_000L, failed.lastSyncedEpochMillis)
        assertEquals(70_000L, failed.lastAttemptEpochMillis)
        assertEquals("HTTP 503 from Supabase", failed.lastError)

        clock.nowMillis = 130_000L
        httpClient.failure = null
        manager.refreshSource(source)

        val recovered = assertNotNull(manager.state.value.sourceStatuses[source.id])
        assertEquals(130_000L, recovered.lastSyncedEpochMillis)
        assertNull(recovered.lastError)
    }

    @Test
    fun refreshingAllSourcesKeepsEachSourcesOwnStatus() = runBlocking {
        val httpClient = SupabaseHttpClient(failingBuckets = setOf("broken"))
        val manager = syncManager(httpClient, MutableClock(5_000L))
        val healthy = supabaseSource("healthy")
        val broken = supabaseSource("broken")

        manager.refreshAllSources(listOf(healthy, broken), force = true)

        val statuses = manager.state.value.sourceStatuses
        assertNull(statuses.getValue(healthy.id).lastError)
        assertEquals(5_000L, statuses.getValue(healthy.id).lastSyncedEpochMillis)
        assertEquals("Bucket broken is unavailable", statuses.getValue(broken.id).lastError)
        assertNull(statuses.getValue(broken.id).lastSyncedEpochMillis)
    }

    @Test
    fun forgettingASourceClearsItsStatus() = runBlocking {
        val manager = syncManager(SupabaseHttpClient(), MutableClock(1_000L))
        val kept = supabaseSource("kept")
        val removed = supabaseSource("removed")
        manager.refreshAllSources(listOf(kept, removed), force = true)

        manager.forgetSource(removed.id)

        assertEquals(setOf(kept.id), manager.state.value.sourceStatuses.keys)
    }

    @Test
    fun removingASourceFromTheAppClearsItsSyncStatus() = runBlocking {
        val source = supabaseSource("bucket_a")
        val runtime = createTestPlatformRuntime(initialSources = listOf(source))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val appState = createTestLunarAppState(scope = scope, runtime = runtime)
            runtime.syncManager.refreshSource(source)
            assertNotNull(runtime.syncManager.state.value.sourceStatuses[source.id]?.lastError)

            appState.removeSource(source.id)

            withTimeout(5_000L) {
                while (source.id in runtime.syncManager.state.value.sourceStatuses) delay(10L)
            }
            assertTrue(runtime.sourceRegistry.sources.value.none { it.id == source.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun statusLineReportsEachOutcome() {
        val source = supabaseSource("bucket_a")
        val now = 10L * 60_000L

        val notSynced = sourceStatusLine(source, CloudSyncState(), now)
        assertEquals("Not synced yet", notSynced.text)

        val synced = sourceStatusLine(
            source,
            CloudSyncState(sourceStatuses = mapOf(source.id to SourceSyncStatus(lastSyncedEpochMillis = now - 2 * 60_000L))),
            now,
        )
        assertEquals("Synced 2 minutes ago", synced.text)
        assertEquals(SourceStatusTone.NEUTRAL, synced.tone)

        val failed = sourceStatusLine(
            source,
            CloudSyncState(sourceStatuses = mapOf(source.id to SourceSyncStatus(lastError = "HTTP 503 from Supabase"))),
            now,
        )
        assertEquals("HTTP 503 from Supabase", failed.text)
        assertEquals(SourceStatusTone.ERROR, failed.tone)

        val expired = sourceStatusLine(
            source,
            CloudSyncState(
                sourceStatuses = mapOf(
                    source.id to SourceSyncStatus(lastError = "HTTP 400: {\"error\":\"invalid_grant\"}"),
                ),
            ),
            now,
        )
        assertEquals("Sign-in expired, reconnect", expired.text)

        val syncing = sourceStatusLine(
            source,
            CloudSyncState(isRefreshing = true, activeSourceLabels = listOf(source.label)),
            now,
        )
        assertEquals(SourceStatusTone.ACTIVE, syncing.tone)

        val local = sourceStatusLine(testLocalFilesSource(id = "local", label = "Desk"), CloudSyncState(), now)
        assertFalse(local.tone == SourceStatusTone.ERROR)
    }

    @Test
    fun relativeTimeUsesTheLargestWholeUnit() {
        assertEquals("just now", formatRelativeTime(0L, 30_000L))
        assertEquals("1 minute ago", formatRelativeTime(0L, 60_000L))
        assertEquals("59 minutes ago", formatRelativeTime(0L, 59 * 60_000L))
        assertEquals("3 hours ago", formatRelativeTime(0L, 3 * 3_600_000L))
        assertEquals("2 days ago", formatRelativeTime(0L, 50 * 3_600_000L))
        assertEquals("just now", formatRelativeTime(10_000L, 0L))
    }
}

private suspend fun syncManager(
    httpClient: SyncHttpClient,
    clock: Clock,
): LibrarySyncManager {
    val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())
    repository.initialize()
    return LibrarySyncManager(
        repository = repository,
        httpClient = httpClient,
        pdfStore = NoOpManagedPdfStore,
        renderer = UnavailablePdfPageRenderer,
        clock = clock,
    ).also { it.initialize() }
}

private fun supabaseSource(bucket: String) = CloudSupabaseSource(
    id = "supabase_$bucket",
    label = "Supabase $bucket",
    addedAtEpochMillis = 1L,
    settings = SupabasePublicStorageSettings(
        projectUrl = "https://example.supabase.co",
        bucketName = bucket,
    ),
)

private class MutableClock(var nowMillis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}

/** Lists empty buckets, failing for [failingBuckets] or for every bucket while [failure] is set. */
private class SupabaseHttpClient(
    private val failingBuckets: Set<String> = emptySet(),
) : SyncHttpClient {
    var failure: String? = null

    override suspend fun getText(url: String, headers: Map<String, String>): String = error("Unexpected GET $url")

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray = error("Unexpected GET $url")

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): String {
        failure?.let { throw IOException(it) }
        val bucket = url.substringAfterLast('/')
        if (bucket in failingBuckets) throw IOException("Bucket $bucket is unavailable")
        return "[]"
    }
}
