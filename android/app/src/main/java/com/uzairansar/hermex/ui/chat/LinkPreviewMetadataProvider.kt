package com.uzairansar.hermex.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

data class LinkPreviewMetadata(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val imageBytes: ByteArray? = null,
)

object LinkPreviewMetadataProvider {
    private const val MAX_HTML_BYTES = 256L * 1_024L
    private const val MAX_IMAGE_BYTES = 2L * 1_024L * 1_024L
    private const val MAX_CACHE_ENTRIES = 64
    private const val MAX_CACHED_IMAGE_BYTES = 16L * 1_024L * 1_024L
    private const val SUCCESS_CACHE_TTL_MILLIS = 15L * 60L * 1_000L
    private const val FAILURE_CACHE_TTL_MILLIS = 30L * 1_000L
    private const val MAX_REDIRECTS = 5
    private val client = OkHttpClient.Builder()
        .dns(PublicLinkPreviewDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val cache = LinkPreviewMetadataCache(
        maxEntries = MAX_CACHE_ENTRIES,
        maxImageBytes = MAX_CACHED_IMAGE_BYTES,
        successTtlMillis = SUCCESS_CACHE_TTL_MILLIS,
        failureTtlMillis = FAILURE_CACHE_TTL_MILLIS,
    )

    suspend fun metadata(url: HttpUrl): LinkPreviewMetadata = withContext(Dispatchers.IO) {
        cache[url.toString()]?.let { return@withContext it }
        val metadata = runCatching { fetch(url) }.getOrDefault(LinkPreviewMetadata())
        cache[url.toString()] = metadata
        metadata
    }

    private fun fetch(url: HttpUrl): LinkPreviewMetadata {
        val parsed = executePreviewRequest(url, "text/html,application/xhtml+xml").use { response ->
            if (!response.isSuccessful) return LinkPreviewMetadata()
            val body = response.body
            val type = body.contentType()?.toString().orEmpty()
            if (!type.contains("html", ignoreCase = true)) return LinkPreviewMetadata()
            val bytes = body.source().readByteArray(MAX_HTML_BYTES + 1)
            if (bytes.size > MAX_HTML_BYTES) return LinkPreviewMetadata()
            parseLinkPreviewMetadata(bytes.toString(Charsets.UTF_8), response.request.url)
        }
        val imageBytes = parsed.imageUrl
            ?.toHttpUrlOrNull()
            ?.let(::fetchImage)
        return parsed.copy(imageBytes = imageBytes)
    }

    private fun fetchImage(url: HttpUrl): ByteArray? {
        return executePreviewRequest(url, "image/*").use { response ->
            if (!response.isSuccessful || response.body.contentType()?.type != "image") return null
            val bytes = response.body.source().readByteArray(MAX_IMAGE_BYTES + 1)
            bytes.takeIf { it.size <= MAX_IMAGE_BYTES }
        }
    }

    private fun executePreviewRequest(initialUrl: HttpUrl, accept: String): Response {
        var url = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requirePublicLinkPreviewUrl(url)
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", accept)
                    .header("User-Agent", "Hermex-Android-LinkPreview/1.0")
                    .build(),
            ).execute()
            val location = response.header("Location")
            if (response.code !in REDIRECT_CODES || location == null) return response
            response.close()
            if (redirectCount == MAX_REDIRECTS) throw IOException("Link preview redirected too many times.")
            url = url.resolve(location) ?: throw IOException("Link preview returned an invalid redirect.")
        }
        throw IOException("Link preview redirected too many times.")
    }
}

internal class LinkPreviewMetadataCache(
    private val maxEntries: Int,
    private val maxImageBytes: Long,
    private val successTtlMillis: Long,
    private val failureTtlMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val metadata: LinkPreviewMetadata,
        val cachedAtMillis: Long,
    )

    private val entries = LinkedHashMap<String, Entry>(maxEntries, 0.75f, true)
    private var retainedImageBytes = 0L

    @Synchronized
    operator fun get(key: String): LinkPreviewMetadata? {
        val entry = entries[key] ?: return null
        val ttl = if (entry.metadata.isEmpty()) failureTtlMillis else successTtlMillis
        if (nowMillis() - entry.cachedAtMillis >= ttl) {
            remove(key)
            return null
        }
        return entry.metadata
    }

    @Synchronized
    operator fun set(key: String, metadata: LinkPreviewMetadata) {
        remove(key)
        entries[key] = Entry(metadata, nowMillis())
        retainedImageBytes += metadata.imageBytes?.size?.toLong() ?: 0L
        trimToBudget()
    }

    @Synchronized
    internal fun imageByteCount(): Long = retainedImageBytes

    @Synchronized
    internal fun size(): Int = entries.size

    private fun trimToBudget() {
        while (entries.size > maxEntries || retainedImageBytes > maxImageBytes) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: return
            remove(eldestKey)
        }
    }

    private fun remove(key: String) {
        retainedImageBytes -= entries.remove(key)?.metadata?.imageBytes?.size?.toLong() ?: 0L
    }

    private fun LinkPreviewMetadata.isEmpty(): Boolean =
        title == null && description == null && imageUrl == null && imageBytes == null
}

private object PublicLinkPreviewDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        requirePublicLinkPreviewHost(hostname)
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !isPublicLinkPreviewAddress(it) }) {
            throw UnknownHostException("Link previews cannot access local or private network addresses.")
        }
        return addresses
    }
}

internal fun requirePublicLinkPreviewUrl(url: HttpUrl) {
    if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) {
        throw IOException("Unsupported link preview URL.")
    }
    requirePublicLinkPreviewHost(url.host)
}

internal fun requirePublicLinkPreviewHost(host: String) {
    val normalized = host.trim().trimEnd('.').lowercase()
    val blockedSuffixes = listOf(
        "localhost",
        ".localhost",
        ".local",
        ".lan",
        ".internal",
        ".home.arpa",
        ".test",
        ".invalid",
        ".example",
    )
    if (normalized.isEmpty() || blockedSuffixes.any(normalized::endsWith)) {
        throw UnknownHostException("Link previews cannot access local or private network hosts.")
    }
    if ('.' !in normalized && ':' !in normalized) {
        throw UnknownHostException("Link previews require a public host name.")
    }
}

internal fun isPublicLinkPreviewAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return false
    }
    val bytes = address.address.map { it.toInt() and 0xff }
    if (bytes.size == 4) {
        val first = bytes[0]
        val second = bytes[1]
        val third = bytes[2]
        return when {
            first == 0 || first == 10 || first == 127 -> false
            first == 100 && second in 64..127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 0 && third in setOf(0, 2) -> false
            first == 192 && second == 88 && third == 99 -> false
            first == 192 && second == 168 -> false
            first == 198 && second in 18..19 -> false
            first == 198 && second == 51 && third == 100 -> false
            first == 203 && second == 0 && third == 113 -> false
            first >= 224 -> false
            else -> true
        }
    }
    if (bytes.size == 16) {
        val uniqueLocal = bytes[0] and 0xfe == 0xfc
        val documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
        return !uniqueLocal && !documentation
    }
    return false
}

private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

internal fun parseLinkPreviewMetadata(html: String, baseUrl: HttpUrl): LinkPreviewMetadata {
    val meta = linkedMapOf<String, String>()
    META_TAG.findAll(html).forEach { tagMatch ->
        val attributes = ATTRIBUTE.findAll(tagMatch.value).associate { match ->
            match.groupValues[1].lowercase() to decodeHtmlText(match.groupValues[3])
        }
        val key = (attributes["property"] ?: attributes["name"])?.lowercase()
        val content = attributes["content"]?.trim()?.takeIf { it.isNotEmpty() }
        if (key != null && content != null && key !in meta) meta[key] = content
    }
    val title = sequenceOf(meta["og:title"], meta["twitter:title"], TITLE.find(html)?.groupValues?.get(1))
        .mapNotNull { it?.let(::decodeHtmlText)?.cleanPreviewText() }
        .firstOrNull()
    val description = sequenceOf(meta["og:description"], meta["twitter:description"], meta["description"])
        .mapNotNull { it?.cleanPreviewText() }
        .firstOrNull()
    val image = sequenceOf(meta["og:image"], meta["twitter:image"], meta["twitter:image:src"])
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .mapNotNull(baseUrl::resolve)
        .firstOrNull()
        ?.toString()
    return LinkPreviewMetadata(title = title, description = description, imageUrl = image)
}

private fun String.cleanPreviewText(): String? =
    replace(TAG, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)
        .takeIf { it.isNotEmpty() }

private fun decodeHtmlText(value: String): String = value
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&apos;", "'", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)

private val META_TAG = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ATTRIBUTE = Regex("([A-Za-z_:.-]+)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.DOT_MATCHES_ALL))
private val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val TAG = Regex("<[^>]+>")
