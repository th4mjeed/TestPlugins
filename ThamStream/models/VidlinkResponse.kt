package com.thamjeed.thamstream.models

data class VidlinkResponse(
    val stream: Stream?
)

data class Stream(
    val playlist: String?
)
