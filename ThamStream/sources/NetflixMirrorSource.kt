package com.thamjeed.thamstream.sources

import com.lagradost.cloudstream3.ExtractorLink

class NetflixMirrorSource : Source {

    override suspend fun loadMovie(
        tmdbId: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return false
    }

    override suspend fun loadEpisode(
        tmdbId: Int,
        season: Int,
        episode: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return false
    }
}
