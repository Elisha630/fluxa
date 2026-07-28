package com.fluxawallpapers.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PinterestScraper {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    data class PinData(
        val imageUrl: String,
        val title: String,
        val author: String,
        val keywords: List<String> = emptyList()
    )

    suspend fun searchPins(query: String, maxPins: Int = 15): List<PinData> =
        withContext(Dispatchers.IO) {
            try {
                val q = java.net.URLEncoder.encode(query, "UTF-8")
                val doc = Jsoup.connect("https://www.pinterest.com/search/pins/?q=$q")
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .get()
                extractPinsFromDocument(doc, maxPins)
            } catch (e: Exception) {
                FluxaLog.e("Pinterest search scrape failed for '$query': ${e.message}", e)
                emptyList()
            }
        }

    private fun extractPinsFromDocument(doc: Document, maxPins: Int): List<PinData> {
        val pins = mutableListOf<PinData>()

        // Try parsing __PWS_DATA__ embedded JSON
        try {
            val pws = doc.select("script#__PWS_DATA__").first()
            if (pws != null) {
                pins.addAll(parsePwsData(pws.html(), maxPins))
            }
        } catch (_: Exception) {}

        // Fallback: scan img tags for pinimg.com URLs
        if (pins.size < maxPins) {
            val existing = pins.map { it.imageUrl }.toSet()
            for (img in doc.select("img[src*=pinimg.com]")) {
                if (pins.size >= maxPins) break
                val srcset = img.attr("srcset")
                val rawUrl = extractBestUrl(srcset) ?: img.attr("src")
                if (!rawUrl.contains("pinimg.com")) continue
                val url = upgradeToOriginal(rawUrl)
                if (url in existing) continue
                val altText = img.attr("alt").ifBlank { "Pinterest Pin" }
                pins.add(PinData(url, altText, "Pinterest", keywords = extractKeywords(altText)))
            }
        }
        return pins
    }

    private fun parsePwsData(json: String, max: Int): List<PinData> {
        val pins = mutableListOf<PinData>()
        // Improved regex to find 'orig' URLs which are high-res
        val urlRegex = """"orig"\s*:\s*\{\s*"url"\s*:\s*"([^"]+)"""".toRegex()
        val titleRegex = """"title"\s*:\s*"([^"]+)"""".toRegex()
        val authorRegex = """"full_name"\s*:\s*"([^"]+)"""".toRegex()

        val urls = urlRegex.findAll(json).map { it.groupValues[1] }.toList()
        val titles = titleRegex.findAll(json).map { it.groupValues[1] }.toList()
        val authors = authorRegex.findAll(json).map { it.groupValues[1] }.toList()

        for ((i, url) in urls.withIndex()) {
            if (pins.size >= max) break
            val highResUrl = upgradeToOriginal(url)
            val title = titles.getOrElse(i) { "Pinterest Pin" }.replace("\\u0020", " ")
            pins.add(PinData(
                highResUrl,
                title,
                authors.getOrElse(i) { "Pinterest" }.replace("\\u0020", " "),
                keywords = extractKeywords(title)
            ))
        }
        return pins
    }

    /**
     * Extracts descriptive keywords from a Pinterest pin title.
     * Strips common noise words, dimensions, and source references.
     *
     * Example: "Cyberpunk City Night Wallpaper HD 4K" → ["cyberpunk", "city", "night"]
     */
    private fun extractKeywords(title: String): List<String> {
        val noise = setOf(
            "wallpaper", "hd", "4k", "8k", "background", "photo", "image",
            "pic", "pin", "pinterest", "unsplash", "pexels", "pixabay",
            "download", "free", "best", "top", "new", "the", "a", "an",
            "in", "on", "at", "to", "for", "of", "and", "or", "is", "it",
            "with", "by", "from", "as", "be", "this", "that", "are", "was",
            "×", "x", "•", "|", "-", "–", "—", "(", ")", "[", "]", "{", "}"
        )
        val cleaned = title.lowercase()
            .replace(Regex("""\d{3,4}x\d{3,4}"""), " ")  // dimensions like 1920x1080
            .replace(Regex("""\d+\s*(px|mp|k)"""), " ")   // 1080p, 12MP, 4K
            .replace(Regex("""[^a-z0-9\s]"""), " ")        // punctuation → spaces
            .replace(Regex("""\s+"""), " ")                // collapse whitespace
            .trim()
        return cleaned.split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && it !in noise }
            .distinct()
            .take(6)
    }

    private fun extractBestUrl(srcset: String): String? {
        if (srcset.isBlank()) return null
        // srcset usually contains multiple sizes, take the last (largest) one
        val last = srcset.split(",").lastOrNull()?.trim() ?: return null
        return last.substringBefore(" ")
    }

    /**
     * Upgrades a Pinterest image URL to its high-resolution /736x/ version.
     *
     * Pinterest serves thumbnails at various sizes (/236x/, /474x/, /136x136/, etc.)
     * in feed/board pages. The /736x/ folder provides a consistently available,
     * high-quality desktop-sized variant that works reliably as a wallpaper source
     * without the extension-mismatch 404s that /originals/ can produce.
     *
     * Example:
     *   https://i.pinimg.com/236x/ab/cd/ef/abcdef123456789.jpg
     *   → https://i.pinimg.com/736x/ab/cd/ef/abcdef123456789.jpg
     */
    private fun upgradeToOriginal(url: String): String {
        if (!url.contains("pinimg.com")) return url

        // Match any size subdirectory: /236x/, /474x236/, /originals/, /236h/, etc.
        // Replace with the safe high-quality /736x/ variant.
        val sizeRegex = Regex("/(?:\\d+x\\d*|originals|\\d+h)/")
        var upgraded = url.replace(sizeRegex, "/736x/")

        // Strip query parameters that might limit dimensions (e.g. ?w=236&h=354)
        upgraded = upgraded.replace(Regex("\\?.*$"), "")

        return upgraded.trim()
    }
}
