package com.crosspoint.launcher.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

/** One article from an RSS or Atom feed. [summary] is plain text; [published] is epoch ms or 0. */
data class RssItem(
    val title: String,
    val link: String,
    val summary: String,
    val published: Long,
    val feedTitle: String
)

/**
 * Fetches RSS 2.0 and Atom feeds. Each successful download is cached so the page still shows
 * the last articles while offline.
 */
class RssRepository(context: Context) {
    private val cacheDir = File(context.cacheDir, "rss")

    /** Articles from all [feeds], newest first, plus the feeds that could not be loaded. */
    suspend fun load(feeds: List<String>, refresh: Boolean): Pair<List<RssItem>, List<String>> = withContext(Dispatchers.IO) {
        val failed = mutableListOf<String>()
        val items = feeds.flatMap { url ->
            val cache = File(cacheDir, sha1(url) + ".xml")
            val fresh = if (refresh || !cache.isFile) runCatching { download(url, cache) }.isSuccess else true
            if (!fresh) failed += url
            if (cache.isFile) runCatching { cache.inputStream().use { parse(it) } }.getOrElse { failed += url; emptyList() }
            else emptyList()
        }
        items.sortedByDescending { it.published } to failed.distinct()
    }

    private fun download(url: String, target: File) {
        var location = URL(url)
        repeat(5) {
            val connection = (location.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "CrossPoint Launcher")
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5")
            }
            try {
                when (val code = connection.responseCode) {
                    in 300..399 -> {
                        location = URL(location, connection.getHeaderField("Location") ?: error("Redirect without location"))
                        return@repeat
                    }
                    in 200..299 -> {
                        cacheDir.mkdirs()
                        val temp = File(target.path + ".tmp")
                        connection.inputStream.use { input ->
                            temp.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    check(total <= MAX_FEED_BYTES) { "Feed is too large" }
                                    output.write(buffer, 0, n)
                                }
                            }
                        }
                        // Only replace the cache with something that parses.
                        temp.inputStream().use { parse(it) }
                        check(temp.renameTo(target))
                        return
                    }
                    else -> error("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }

    companion object {
        private const val MAX_FEED_BYTES = 2 * 1024 * 1024

        /** Parses RSS 2.0 `<item>` and Atom `<entry>` elements. */
        fun parse(input: InputStream): List<RssItem> {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(input, null)
            }
            val items = mutableListOf<RssItem>()
            var feedTitle = ""
            var inItem = false
            var title = ""; var link = ""; var summary = ""; var date = ""
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.substringAfter(':')
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when {
                        name == "item" || name == "entry" -> {
                            inItem = true; title = ""; link = ""; summary = ""; date = ""
                        }
                        name == "title" && !inItem && feedTitle.isEmpty() -> feedTitle = readText(parser).trim()
                        !inItem -> {}
                        name == "title" -> title = readText(parser).trim()
                        name == "link" -> {
                            // Atom: <link rel="alternate" href="…"/>; RSS: <link>…</link>.
                            val href = parser.getAttributeValue(null, "href")
                            val rel = parser.getAttributeValue(null, "rel")
                            if (href != null) { if (link.isEmpty() || rel == null || rel == "alternate") link = href }
                            else link = readText(parser).trim()
                        }
                        name == "description" || name == "summary" || (name == "content" && summary.isEmpty()) || name == "encoded" ->
                            readText(parser).takeIf { it.isNotBlank() }?.let { if (summary.isEmpty() || name == "encoded") summary = it }
                        name == "pubDate" || name == "published" || name == "updated" || name == "date" ->
                            readText(parser).trim().let { if (date.isEmpty() || name == "published") date = it }
                    }
                    XmlPullParser.END_TAG -> if (name == "item" || name == "entry") {
                        inItem = false
                        if (title.isNotEmpty() || link.isNotEmpty()) {
                            items += RssItem(htmlToText(title), link, htmlToText(summary), parseDate(date), feedTitle)
                        }
                    }
                }
            }
            return items.map { if (it.feedTitle.isEmpty()) it.copy(feedTitle = feedTitle) else it }
        }

        /** Text of the current element, including text inside nested markup (e.g. Atom xhtml content). */
        private fun readText(parser: XmlPullParser): String {
            val text = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
            return text.toString()
        }

        fun htmlToText(html: String): String =
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
                .replace('￼', ' ') // image placeholders
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

        private val RFC822 = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z")

        fun parseDate(text: String): Long {
            if (text.isBlank()) return 0L
            runCatching { return java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            runCatching { return java.time.Instant.parse(text).toEpochMilli() }
            for (pattern in RFC822) {
                runCatching { return SimpleDateFormat(pattern, Locale.US).parse(text)!!.time }
            }
            return 0L
        }

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
