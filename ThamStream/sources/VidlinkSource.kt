package com.thamjeed.thamstream.sources

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.thamjeed.thamstream.models.*

class VidlinkSource : Source {

    override suspend fun loadMovie(
        tmdbId: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val encRes = app.get(
            "https://enc-dec.app/api/enc-vidlink?text=$tmdbId"
        ).parsed<EncryptResponse>()

        val encrypted = encRes.result ?: return false

        val streamRes = app.get(
            "https://vidlink.pro/api/b/movie/$encrypted",
            headers = mapOf(
                "Referer" to "https://vidlink.pro/",
                "Origin" to "https://vidlink.pro"
            )
        ).parsed<VidlinkResponse>()

        val playlist = streamRes.stream?.playlist ?: return false

        callback.invoke(
            newExtractorLink(
                "Vidlink",
                "Vidlink",
                playlist,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://vidlink.pro/"
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }

    override suspend fun loadEpisode(
        tmdbId: Int,
        season: Int,
        episode: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val encRes = app.get(
            "https://enc-dec.app/api/enc-vidlink?text=$tmdbId"
        ).parsed<EncryptResponse>()

        val encrypted = encRes.result ?: return false

        val streamRes = app.get(
            "https://vidlink.pro/api/b/tv/$encrypted/$season/$episode",
            headers = mapOf(
                "Referer" to "https://vidlink.pro/",
                "Origin" to "https://vidlink.pro"
            )
        ).parsed<VidlinkResponse>()

        val playlist = streamRes.stream?.playlist ?: return false

        callback.invoke(
            newExtractorLink(
                "Vidlink",
                "Vidlink",
                playlist,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://vidlink.pro/"
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }
}
