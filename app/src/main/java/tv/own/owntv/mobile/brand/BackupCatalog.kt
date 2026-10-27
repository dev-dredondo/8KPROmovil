package tv.own.owntv.mobile.brand

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.mobile.login.BackupAccount
import tv.own.owntv.mobile.login.BackupStore
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * 8K PRO: the backup provider's live channels, used **only** to offer an alternative when a channel
 * stops working. Nothing here is stored in the database or shown in any list: the catalogue lives in
 * memory, is fetched the first time it is needed and kept for [CACHE_MS].
 */
class BackupCatalog(private val store: BackupStore) {

    /** One backup channel offered in place of a dead one. [url] is never shown. */
    data class Alternative(val name: String, val url: String, val logoUrl: String?, val score: Double)

    private class Entry(val name: String, val streamId: Long, val logo: String?, val tokens: Set<String>, val key: String, val flat: String)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }
    private val mutex = Mutex()
    private var cacheFor: BackupAccount? = null
    private var cacheAt = 0L
    private var entries: List<Entry> = emptyList()
    private var extension = "ts"

    /** Whether the panel gave this device a backup provider. */
    val configured: Boolean get() = store.current() != null

    /**
     * Backup channels whose name looks like [channelName], best first. Null when there is no backup
     * provider or it could not be reached; empty when nothing looks alike.
     */
    suspend fun alternatives(channelName: String, limit: Int = 6): List<Alternative>? = withContext(Dispatchers.IO) {
        val account = store.current() ?: return@withContext null
        val list = load(account) ?: return@withContext null
        val wanted = normalize(channelName)
        if (wanted.tokens.isEmpty()) return@withContext emptyList()
        list.asSequence()
            .map { it to score(wanted, it) }
            .filter { it.second >= MIN_SCORE }
            .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.name.length })
            .distinctBy { it.first.key }
            .take(limit)
            .map { (e, sc) -> Alternative(e.name, streamUrl(account, e.streamId), e.logo, sc) }
            .toList()
    }

    private suspend fun load(account: BackupAccount): List<Entry>? = mutex.withLock {
        val fresh = cacheFor == account && SystemClock.elapsedRealtime() - cacheAt < CACHE_MS && entries.isNotEmpty()
        if (fresh) return entries
        runCatching {
            val base = "${account.serverUrl}/player_api.php?username=${Uri.encode(account.username)}&password=${Uri.encode(account.password)}"
            // Which container the account may use: .ts when allowed, otherwise HLS.
            extension = runCatching {
                val info = JSONObject(get(base)).optJSONObject("user_info")
                val formats = info?.optJSONArray("allowed_output_formats")
                val allowed = (0 until (formats?.length() ?: 0)).map { formats!!.optString(it) }
                if (allowed.isEmpty() || "ts" in allowed) "ts" else "m3u8"
            }.getOrDefault("ts")
            val arr = JSONArray(get("$base&action=get_live_streams"))
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val id = o.optLong("stream_id", -1)
                if (name.isEmpty() || id < 0) continue
                val n = normalize(name)
                if (n.tokens.isEmpty()) continue
                out += Entry(name, id, o.optString("stream_icon").takeIf { it.startsWith("http") }, n.tokens, n.key, n.flat)
            }
            entries = out
            cacheFor = account
            cacheAt = SystemClock.elapsedRealtime()
            out as List<Entry>
        }.onFailure { Log.w(TAG, "backup catalogue failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            resp.body.string()
        }

    private fun streamUrl(account: BackupAccount, streamId: Long): String =
        "${account.serverUrl}/live/${Uri.encode(account.username)}/${Uri.encode(account.password)}/$streamId.$extension"

    /** [flat]: the words run together in order, so "laSexta" and "La Sexta" meet. */
    private class Normalized(val tokens: Set<String>, val numbers: Set<String>, val key: String, val flat: String)

    /**
     * Channel names from two providers rarely match letter for letter: "ES: LA 1 FHD", "|ES| La 1 HD",
     * "LA 1 (1080p)". Lower case, no accents, no country prefix, no quality or codec words — what is
     * left is what the channel is called.
     */
    private fun normalize(raw: String): Normalized {
        var s = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        s = s.replace(Regex("^\\s*[\\[(|]?\\s*[a-z]{2,3}\\s*[\\])|:\\-]+"), " ") // "ES:", "|ES|", "[ES]", "ES -"
        s = s.replace("+", " plus ").replace("&", " y ")
        val words = s.split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in NOISE && !(it.endsWith("fps") && it.dropLast(3).all(Char::isDigit)) }
            .map { WORD_NUMBERS[it] ?: it }
        val tokens = words.toSet()
        val numbers = tokens.filter { t -> t.all(Char::isDigit) }.toSet()
        return Normalized(tokens, numbers, tokens.sorted().joinToString(" "), words.joinToString(""))
    }

    /** Overlap of the two names' words (Dice), with the numbers required to agree: "La 1" is never "La 2". */
    private fun score(a: Normalized, e: Entry): Double {
        val bNumbers = e.tokens.filter { t -> t.all(Char::isDigit) }.toSet()
        if (a.numbers != bNumbers && a.flat != e.flat) return 0.0
        if (a.key == e.key) return 1.0
        if (a.flat.isNotEmpty() && a.flat == e.flat) return 0.95
        val common = a.tokens.count { it in e.tokens }
        if (common == 0) return 0.0
        return 2.0 * common / (a.tokens.size + e.tokens.size)
    }

    private companion object {
        const val TAG = "BackupCatalog"
        const val CACHE_MS = 6 * 60 * 60 * 1000L
        const val MIN_SCORE = 0.6
        /** Words that say how a channel is delivered, not which channel it is. */
        val NOISE = setOf(
            "hd", "fhd", "uhd", "sd", "hq", "lq", "4k", "8k", "2k", "1080", "1080p", "1080i", "720", "720p", "576p", "480p",
            "hevc", "h265", "h264", "x265", "x264", "hdr", "sdr", "50", "60", "backup", "bk", "vip", "raw", "multi", "multiaudio",
            "es", "esp", "spa", "spain", "espana",
        )
        val WORD_NUMBERS = mapOf("uno" to "1", "dos" to "2", "tres" to "3", "cuatro" to "4", "cinco" to "5", "one" to "1", "two" to "2")
    }
}
