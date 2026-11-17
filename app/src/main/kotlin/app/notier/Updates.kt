package app.notier

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

internal object Updates {
    const val PROJECT = "https://github.com/nullnode1/notier-app"
    const val RELEASES = "$PROJECT/releases"
    const val SITE = "https://nullnode1.github.io/notier-app/"
    const val OPEN_UPDATES = "app.notier.OPEN_UPDATES"
    private const val ENDPOINT = "https://api.github.com/repos/nullnode1/notier-app/releases?per_page=100"
    private const val NOTIFICATIONS_ENABLED = "update_notifications"
    private const val LAST_NOTIFIED = "update_last_notified"
    private const val CACHE = "update_release_cache"
    private const val ARCHIVE_SIZE = 20
    private val automaticInterval = TimeUnit.HOURS.toMillis(12)
    private val retryInterval = TimeUnit.MINUTES.toMillis(15)
    private val manualInterval = TimeUnit.MINUTES.toMillis(1)
    private const val LAST_ATTEMPT = "update_last_attempt"
    private const val RETRY_AT = "update_retry_at"
    private const val CHANNEL = "releases"
    private const val JOB_ID = 42613
    private val requestLock = Any()
    @Volatile private var memoizedCache: Pair<String, Cache?>? = null
    @Volatile private var lastIssue: Issue? = null

    data class Release(
        val tag: String, val url: String, val apk: String, val bytes: Long,
        val title: String = tag, val body: String = "", val publishedAt: String = ""
    ) {
        val announcement get() = "$tag:${body.hashCode()}"
    }
    enum class Issue { OFFLINE, RATE_LIMITED, UNAVAILABLE }

    data class Check(val releases: List<Release>, val checkedAt: Long = 0, val issue: Issue? = null) {
        val release get() = releases.firstOrNull()

        fun announcement(installed: String): Release? {
            val newest = release ?: return null
            val seenVersion = Version.parse(installed) ?: return null
            val latestVersion = Version.parse(newest.tag) ?: return null
            return newest.takeIf { latestVersion >= seenVersion && (latestVersion > seenVersion || it.body.isNotBlank()) }
        }

        fun available(installed: String): Release? =
            release?.takeIf { versionIsNewer(it.tag, installed) }
    }

    private data class Cache(val releases: List<Release>, val checkedAt: Long, val etag: String?) {
        fun result(issue: Issue? = null) = Check(releases, checkedAt, issue)
    }

    fun installedVersion(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun cached(context: Context): Check {
        val issue = when {
            !online(context) -> Issue.OFFLINE
            prefs(context).getLong(RETRY_AT, 0) > System.currentTimeMillis() -> Issue.RATE_LIMITED
            else -> lastIssue
        }
        return readCache(context)?.result(issue) ?: Check(emptyList(), issue = issue)
    }

    fun shouldCheck(context: Context): Boolean {
        val now = System.currentTimeMillis()
        return online(context) && prefs(context).getLong(RETRY_AT, 0) <= now &&
            !recent(readCache(context)?.checkedAt ?: 0, now, automaticInterval) &&
            !recent(prefs(context).getLong(LAST_ATTEMPT, 0), now, retryInterval)
    }

    private fun recent(at: Long, now: Long, interval: Long): Boolean = at > 0 && now - at in 0 until interval

    fun check(context: Context, manual: Boolean = false): Check = synchronized(requestLock) {
        val cached = readCache(context)
        val now = System.currentTimeMillis()
        fun snapshot(issue: Issue? = null) = cached?.result(issue) ?: Check(emptyList(), issue = issue)
        fun failure(issue: Issue): Check {
            lastIssue = issue.takeUnless { it == Issue.OFFLINE }
            return snapshot(issue)
        }
        if (!online(context)) return@synchronized snapshot(Issue.OFFLINE)
        val preferences = prefs(context)
        if (preferences.getLong(RETRY_AT, 0) > now) return@synchronized snapshot(Issue.RATE_LIMITED)
        if (!manual && cached != null && recent(cached.checkedAt, now, automaticInterval))
            return@synchronized cached.result()
        val interval = if (manual) manualInterval else retryInterval
        if (recent(preferences.getLong(LAST_ATTEMPT, 0), now, interval))
            return@synchronized snapshot(lastIssue)
        preferences.edit().putLong(LAST_ATTEMPT, now).apply()

        try {
            val connection = URL(ENDPOINT).openConnection() as HttpsURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Notier-Android")
            cached?.etag?.let { connection.setRequestProperty("If-None-Match", it) }
            try {
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val releases = connection.inputStream.bufferedReader().use { reader ->
                            val response = StringBuilder()
                            val buffer = CharArray(8192)
                            while (true) {
                                val size = reader.read(buffer)
                                if (size == -1) break
                                if (response.length + size > 8_000_000) throw IOException("Release listing too large")
                                response.append(buffer, 0, size)
                            }
                            JSONArray(response.toString())
                        }
                        Cache(catalog(releases), now, connection.getHeaderField("ETag"))
                    }
                    HttpURLConnection.HTTP_NOT_MODIFIED -> cached?.copy(checkedAt = now)
                        ?: return@synchronized failure(Issue.UNAVAILABLE)
                    else -> {
                        val retry = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                        if (code == 429 || (code == 403 && (retry != null || remaining == "0"))) {
                            val reset = connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()?.times(1000)
                            val delay = if (retry != null) retry * 1000 else reset?.minus(now) ?: 60_000
                            prefs(context).edit().putLong(RETRY_AT, now + delay.coerceIn(60_000, 86_400_000)).apply()
                            return@synchronized failure(Issue.RATE_LIMITED)
                        }
                        return@synchronized failure(Issue.UNAVAILABLE)
                    }
                }.also {
                    saveCache(context, it)
                    lastIssue = null
                }.result()
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            failure(if (online(context)) Issue.UNAVAILABLE else Issue.OFFLINE)
        }
    }

    internal fun catalog(releases: JSONArray): List<Release> = (0 until releases.length()).asSequence().mapNotNull { index ->
        val row = releases.optJSONObject(index) ?: return@mapNotNull null
        val tag = row.optString("tag_name")
        val version = Version.parse(tag) ?: return@mapNotNull null
        val url = row.optString("html_url")
        if (row.optBoolean("draft") || row.optBoolean("prerelease") || url != "$RELEASES/tag/$tag")
            return@mapNotNull null
        val assets = row.optJSONArray("assets") ?: return@mapNotNull null
        val release = (0 until assets.length()).asSequence().mapNotNull asset@{
            val asset = assets.optJSONObject(it) ?: return@asset null
            val name = asset.optString("name")
            val apkUrl = asset.optString("browser_download_url")
            val bytes = asset.optLong("size")
            if (asset.optString("state") != "uploaded" || !name.endsWith(".apk", ignoreCase = true) ||
                !apkUrl.startsWith("$RELEASES/download/$tag/") || bytes <= 0) return@asset null
            Release(tag, url, apkUrl, bytes, row.optString("name", "").trim().take(220).ifBlank { tag },
                row.optString("body", "").trim().take(20_000), row.optString("published_at", "")) to when {
                name.equals("Notier-v${version.major}.${version.minor}.${version.patch}.apk", true) -> 0
                name.equals("Notier.apk", true) -> 1
                else -> 2
            }
        }.minByOrNull { it.second }?.first
        release?.let { version to it }
    }.sortedByDescending { it.first }.distinctBy { it.first }.take(ARCHIVE_SIZE).map { it.second }.toList()

    private fun readCache(context: Context): Cache? {
        val raw = prefs(context).getString(CACHE, null) ?: return null
        memoizedCache?.let { if (it.first == raw) return it.second }
        val cache = runCatching { decodeCache(raw) }.getOrNull()
        memoizedCache = raw to cache
        return cache
    }

    private fun decodeCache(raw: String): Cache {
        val data = JSONObject(raw)
        val entries = data.optJSONArray("releases")
        // v1.0.4 cached only the download URL. Retain it offline and fetch the articles online.
        if (entries == null) return Cache(listOfNotNull(cachedRelease(data)), 0, null)
        val releases = (0 until entries.length().coerceAtMost(ARCHIVE_SIZE)).mapNotNull { index ->
            entries.optJSONObject(index)?.let(::cachedRelease)
        }
        return Cache(releases, data.getLong("checked"), data.optString("etag").ifEmpty { null })
    }

    private fun cachedRelease(data: JSONObject): Release? {
        val tag = data.optString("tag")
        val url = data.optString("url")
        val apk = data.optString("apk")
        val bytes = data.optLong("size")
        if (Version.parse(tag) == null || url != "$RELEASES/tag/$tag" ||
            !apk.startsWith("$RELEASES/download/$tag/") || bytes <= 0) return null
        return Release(tag, url, apk, bytes, data.optString("title", tag),
            data.optString("body", ""), data.optString("published", ""))
    }

    private fun saveCache(context: Context, cache: Cache) {
        val releases = JSONArray()
        for (release in cache.releases) {
            releases.put(JSONObject().put("tag", release.tag).put("url", release.url).put("apk", release.apk)
                .put("size", release.bytes).put("title", release.title).put("body", release.body)
                .put("published", release.publishedAt))
        }
        val data = JSONObject().put("checked", cache.checkedAt).put("etag", cache.etag).put("releases", releases)
        val raw = data.toString()
        prefs(context).edit().putString(CACHE, raw).remove(RETRY_AT).apply()
        memoizedCache = raw to cache
    }

    internal fun versionIsNewer(candidate: String, installed: String): Boolean {
        val available = Version.parse(candidate) ?: return false
        val current = Version.parse(installed) ?: return false
        return available > current
    }

    private data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version) = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

        companion object {
            private val pattern = Regex("v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?", RegexOption.IGNORE_CASE)

            fun parse(tag: String): Version? {
                val match = pattern.matchEntire(tag.trim()) ?: return null
                val (major, minor, patch) = match.destructured
                return Version(
                    major.toIntOrNull() ?: return null,
                    minor.ifEmpty { "0" }.toIntOrNull() ?: return null,
                    patch.ifEmpty { "0" }.toIntOrNull() ?: return null
                )
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("notier", Context.MODE_PRIVATE)

    fun notificationsEnabled(context: Context) = prefs(context).getBoolean(NOTIFICATIONS_ENABLED, false)

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun ensureScheduled(context: Context) {
        if (!notificationsEnabled(context)) return
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) == null) changeNotifications(context, true)
        val cached = readCache(context)
        if (cached?.result()?.available(installedVersion(context)) == null)
            context.getSystemService(NotificationManager::class.java).cancel(JOB_ID)
    }

    fun changeNotifications(context: Context, enabled: Boolean): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (enabled) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setPersisted(true)
                .build()
            if (runCatching { scheduler.schedule(job) }.getOrNull() != JobScheduler.RESULT_SUCCESS) return false
        } else {
            scheduler.cancel(JOB_ID)
            context.getSystemService(NotificationManager::class.java).cancel(JOB_ID)
        }
        prefs(context).edit().putBoolean(NOTIFICATIONS_ENABLED, enabled).apply()
        return true
    }

    fun notifyIfNew(context: Context, release: Release) {
        val prefs = prefs(context)
        if (!notificationsEnabled(context) || !canNotify(context) || prefs.getString(LAST_NOTIFIED, null) == release.tag)
            return
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(prefs.getString("language", "tr") ?: "tr"))
        val localized = context.createConfigurationContext(configuration)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.updates), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, JOB_ID, Intent(context, MainActivity::class.java).setAction(OPEN_UPDATES)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        manager.notify(JOB_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle("Notier")
            .setContentText(localized.getString(R.string.updates_available, release.tag))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
        prefs.edit().putString(LAST_NOTIFIED, release.tag).apply()
    }
}

internal class UpdateJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private var task: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!Updates.notificationsEnabled(this) || !Updates.canNotify(this)) return false
        task = thread(name = "Notier releases") {
            val running = Thread.currentThread()
            val result = Updates.check(this)
            main.post {
                if (task !== running) return@post
                task = null
                if (result.issue == null) result.available(Updates.installedVersion(this@UpdateJob))?.let { release ->
                    runCatching { Updates.notifyIfNew(this@UpdateJob, release) }
                }
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.interrupt()
        task = null
        return Updates.notificationsEnabled(this)
    }
}
