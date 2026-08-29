package com.uzairansar.hermex.ui.chat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class LinkPreviewMetadataProviderTest {
    @Test
    fun cacheEvictsEntriesUntilTheRetainedImageBudgetIsSatisfied() {
        val cache = LinkPreviewMetadataCache(
            maxEntries = 10,
            maxImageBytes = 6,
            successTtlMillis = 1_000,
            failureTtlMillis = 100,
        )

        cache["one"] = LinkPreviewMetadata(title = "one", imageBytes = ByteArray(4))
        cache["two"] = LinkPreviewMetadata(title = "two", imageBytes = ByteArray(4))

        assertEquals(1, cache.size())
        assertEquals(4L, cache.imageByteCount())
        assertEquals("two", cache["two"]?.title)
    }

    @Test
    fun cacheExpiresFailuresSoonerThanSuccessfulMetadata() {
        var now = 0L
        val cache = LinkPreviewMetadataCache(
            maxEntries = 10,
            maxImageBytes = 100,
            successTtlMillis = 1_000,
            failureTtlMillis = 100,
            nowMillis = { now },
        )
        cache["failure"] = LinkPreviewMetadata()
        cache["success"] = LinkPreviewMetadata(title = "loaded")

        now = 100

        assertEquals(null, cache["failure"])
        assertEquals("loaded", cache["success"]?.title)
    }

    @Test
    fun parsesOpenGraphMetadataAndResolvesRelativeImages() {
        val metadata = parseLinkPreviewMetadata(
            """
            <html><head>
              <meta property="og:title" content="Hermex &amp; Android">
              <meta content="A richer preview" name="description">
              <meta property="og:image" content="/images/card.png">
              <title>Fallback title</title>
            </head></html>
            """.trimIndent(),
            "https://example.com/articles/one".toHttpUrl(),
        )

        assertEquals("Hermex & Android", metadata.title)
        assertEquals("A richer preview", metadata.description)
        assertEquals("https://example.com/images/card.png", metadata.imageUrl)
    }

    @Test
    fun fallsBackToTheDocumentTitleAndCleansMarkup() {
        val metadata = parseLinkPreviewMetadata(
            "<html><head><title>  A <b>useful</b> page  </title></head></html>",
            "https://example.com".toHttpUrl(),
        )

        assertEquals("A useful page", metadata.title)
    }

    @Test
    fun blocksLocalPrivateAndCredentialedPreviewUrls() {
        listOf(
            "http://example.com/",
            "http://localhost/",
            "https://router.local/",
            "https://service.internal/",
            "https://single-label/",
            "https://user:password@example.com/",
        ).forEach { value ->
            assertThrows(IOException::class.java) { requirePublicLinkPreviewUrl(value.toHttpUrl()) }
        }
    }

    @Test
    fun acceptsPublicPreviewUrlsAndRejectsNonPublicAddresses() {
        requirePublicLinkPreviewUrl("https://example.com/article".toHttpUrl())

        assertTrue(isPublicLinkPreviewAddress(ipv4(8, 8, 8, 8)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(127, 0, 0, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(10, 0, 0, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(100, 64, 0, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(169, 254, 1, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(192, 0, 2, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(198, 51, 100, 1)))
        assertFalse(isPublicLinkPreviewAddress(ipv4(203, 0, 113, 1)))
    }

    private fun ipv4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))
}
