package com.thamjeed.thamstream.sources

import com.lagradost.cloudstream3.ExtractorLink

interface Source {

    suspend fun loadMovie(
        tmdbId: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean

    suspend fun loadEpisode(
        tmdbId: Int,
        season: Int,
        episode: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean
}
