
package com.thanh3872.vkvideofork

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import java.util.*

class VkVideoJav2026Provider : MainAPI() {
    override var name = "VK Video @jav2026"
    override var mainUrl = "https://m.vkvideo.ru"
    override var appNameList = "VK Video Jav2026"
    override var lang = "vi"
    override var hasMainPage = true
    override var hasChromecastSupport = true
    override var hasDownloadSupport = true
    override var supportedTypes = setOf(TvType.NSFW)

    companion object {
        const val API_BASE = "https://api.vkvideo.ru/method"
        const val CLIENT_ID = "52649896"
        const val API_VERSION = "5.269"
        val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://m.vkvideo.ru/",
            "Origin" to "https://m.vkvideo.ru",
            "Content-Type" to "application/x-www-form-urlencoded"
        )
    }

    // -------------------- MAIN PAGE --------------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val videoList = mutableListOf<SearchResponse>()
        
        val response = app.post(
            url = "$API_BASE/catalog.getVideo",
            params = mapOf("v" to API_VERSION, "client_id" to CLIENT_ID),
            headers = HEADERS,
            data = ""
        ).parsedSafe<CatalogVideoResponse>() ?: return HomePageResponse(emptyList())
        
        val section = response.response?.catalog?.sections?.firstOrNull()
        val block = section?.blocks?.firstOrNull { it.data_type == "videos" }
        block?.items?.forEach { video ->
            videoList.add(
                newMovieSearchResponse(
                    name = video.title ?: "Untitled",
                    url = video.getVideoUrl(),
                    this.name,
                    TvType.NSFW
                ) {
                    this.posterUrl = video.thumb?.url
                    this.duration = video.duration?.toInt()
                }
            )
        }
        
        return HomePageResponse(listOf(HomePageList("Mới nhất", videoList)))
    }

    // -------------------- LOAD (chi tiết video) --------------------
    override suspend fun load(url: String): LoadResponse? {
        val parts = url.removePrefix("vkvideo:").split("_")
        if (parts.size < 2) return null
        val ownerId = parts[0]
        val videoId = parts[1]
        val accessKey = if (parts.size > 2) parts[2] else ""
        
        val videosParam = "${ownerId}_${videoId}${if (accessKey.isNotEmpty()) "_$accessKey" else ""}"
        
        val response = app.get(
            url = "$API_BASE/video.get",
            params = mapOf(
                "v" to API_VERSION,
                "client_id" to CLIENT_ID,
                "videos" to videosParam
            ),
            headers = HEADERS
        ).parsedSafe<VideoGetResponse>() ?: return null
        
        val video = response.response?.items?.firstOrNull() ?: return null
        
        return newMovieLoadResponse(
            name = video.title ?: "Unknown",
            url = url,
            this.name,
            TvType.NSFW,
            url
        ) {
            this.posterUrl = video.thumb?.url ?: video.image?.firstOrNull()?.url
            this.year = video.date?.let { java.time.Instant.ofEpochSecond(it.toLong()).toString().substring(0,4) }?.toIntOrNull()
            this.duration = video.duration?.toInt()
            this.plot = video.description
            this.addActors(video.actors?.joinToString { it.name })
        }
    }

    // -------------------- TÌM KIẾM --------------------
    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }

    // -------------------- LẤY LINK PHÁT --------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.removePrefix("vkvideo:").split("_")
        if (parts.size < 2) return false
        val ownerId = parts[0]
        val videoId = parts[1]
        val accessKey = if (parts.size > 2) parts[2] else ""
        
        val videosParam = "${ownerId}_${videoId}${if (accessKey.isNotEmpty()) "_$accessKey" else ""}"
        
        val response = app.get(
            url = "$API_BASE/video.get",
            params = mapOf(
                "v" to API_VERSION,
                "client_id" to CLIENT_ID,
                "videos" to videosParam
            ),
            headers = HEADERS
        ).parsedSafe<VideoGetResponse>() ?: return false
        
        val video = response.response?.items?.firstOrNull() ?: return false
        val files = video.files ?: return false
        
        val qualities = mapOf(
            "mp4_720" to Quality.P720,
            "mp4_480" to Quality.P480,
            "mp4_360" to Quality.P360,
            "mp4_240" to Quality.P240
        )
        for ((key, quality) in qualities) {
            files.getExtra(key)?.let { url ->
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "VK ${key.replace("mp4_", "")}p",
                        url = url,
                        referer = "$mainUrl/",
                        quality = quality,
                        isM3u8 = false
                    )
                )
            }
        }
        
        files.hls?.let { url ->
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = "VK HLS",
                    url = url,
                    referer = "$mainUrl/",
                    quality = Quality.P720.value,
                    isM3u8 = true
                )
            )
        }
        
        return true
    }

    // -------------------- DATA CLASSES --------------------
    data class CatalogVideoResponse(
        @JsonProperty("response") val response: ResponseData?
    ) {
        data class ResponseData(
            @JsonProperty("catalog") val catalog: Catalog?
        )
        data class Catalog(
            @JsonProperty("sections") val sections: List<Section>?
        )
        data class Section(
            @JsonProperty("id") val id: String?,
            @JsonProperty("title") val title: String?,
            @JsonProperty("blocks") val blocks: List<Block>?
        )
        data class Block(
            @JsonProperty("id") val id: String?,
            @JsonProperty("data_type") val data_type: String?,
            @JsonProperty("items") val items: List<VideoItem>?
        )
        data class VideoItem(
            @JsonProperty("title") val title: String?,
            @JsonProperty("duration") val duration: Int?,
            @JsonProperty("thumb") val thumb: Thumb?,
            @JsonProperty("video") val video: VideoRef?
        ) {
            fun getVideoUrl(): String {
                val owner = video?.owner_id ?: ""
                val id = video?.id ?: ""
                val key = video?.access_key ?: ""
                return "vkvideo:${owner}_${id}_$key"
            }
        }
        data class Thumb(
            @JsonProperty("url") val url: String?
        )
        data class VideoRef(
            @JsonProperty("owner_id") val owner_id: String?,
            @JsonProperty("id") val id: String?,
            @JsonProperty("access_key") val access_key: String?
        )
    }

    data class VideoGetResponse(
        @JsonProperty("response") val response: ResponseData?
    ) {
        data class ResponseData(
            @JsonProperty("items") val items: List<Video>?
        )
        data class Video(
            @JsonProperty("title") val title: String?,
            @JsonProperty("duration") val duration: Int?,
            @JsonProperty("description") val description: String?,
            @JsonProperty("date") val date: Int?,
            @JsonProperty("thumb") val thumb: Thumb?,
            @JsonProperty("image") val image: List<Image>?,
            @JsonProperty("actors") val actors: List<Actor>?,
            @JsonProperty("files") val files: VideoFiles?
        )
        data class Thumb(
            @JsonProperty("url") val url: String?
        )
        data class Image(
            @JsonProperty("url") val url: String?
        )
        data class Actor(
            @JsonProperty("name") val name: String?
        )
        data class VideoFiles(
            @JsonProperty("mp4_240") val mp4_240: String?,
            @JsonProperty("mp4_360") val mp4_360: String?,
            @JsonProperty("mp4_480") val mp4_480: String?,
            @JsonProperty("mp4_720") val mp4_720: String?,
            @JsonProperty("hls") val hls: String?
        ) {
            fun getExtra(key: String): String? {
                return when(key) {
                    "mp4_240" -> mp4_240
                    "mp4_360" -> mp4_360
                    "mp4_480" -> mp4_480
                    "mp4_720" -> mp4_720
                    else -> null
                }
            }
        }
    }
}
