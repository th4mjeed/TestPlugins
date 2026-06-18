package com.thamjeed.thamstream.models

data class TmdbLoadData(
    val id: Int,
    val type: String,
    val season: Int? = null,
    val episode: Int? = null
)
