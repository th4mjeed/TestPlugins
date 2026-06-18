package com.thamjeed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLinkType

@CloudstreamPlugin
class ThamStreamPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(ThamStreamProvider())
    }
}

class ThamStreamProvider : MainAPI() {
    override var name = "ThamStream"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiKey = "3dd19c514fa3abbc33c01a741e5bf444"
    private val bearerToken = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzZGQxOWM1MTRmYTNhYmJjMzNjMDFhNzQxZTViZjQ0NCIsIm5iZiI6MTc4MTQzMzcxMy41ODYsInN1YiI6IjZhMmU4NTcxNzVhZWViZjc4N2U0OTNiNiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.aEBYAuK76uiDCzw4n1JUSaqyeiBCP0mH-w8WsREZ8RE"
    private val tmdbBase = "https://api.themoviedb.org/3"
    private val imageBase = "https://image.tmdb.org/t/p/w500"
    private val backdropBase = "https://image.tmdb.org/t/p/w1280"

    private val authHeaders = mapOf(
        "Authorization" to "Bearer $bearerToken",
        "accept" to "application/json"
    )

    override val mainPage = mainPageOf(
        "$tmdbBase/movie/popular?language=en-US&page=1"                              to "Popular Movies",
        "$tmdbBase/tv/popular?language=en-US&page=1"                                 to "Popular TV Shows",
        "$tmdbBase/movie/top_rated?language=en-US&page=1"                            to "Top Rated Movies",
        "$tmdbBase/tv/top_rated?language=en-US&page=1"                               to "Top Rated TV Shows",
        "$tmdbBase/movie/now_playing?language=en-US&page=1"                          to "Now Playing",
        "$tmdbBase/tv/on_the_air?language=en-US&page=1"                              to "Currently Airing",
        "$tmdbBase/movie/upcoming?language=en-US&page=1"                             to "Upcoming Movies",
        "$tmdbBase/discover/movie?with_genres=28&sort_by=popularity.desc&page=1"     to "Action Movies",
        "$tmdbBase/discover/movie?with_genres=35&sort_by=popularity.desc&page=1"     to "Comedy Movies",
        "$tmdbBase/discover/movie?with_genres=27&sort_by=popularity.desc&page=1"     to "Horror Movies",
        "$tmdbBase/discover/movie?with_genres=878&sort_by=popularity.desc&page=1"    to "Sci-Fi Movies",
        "$tmdbBase/discover/movie?with_genres=18&sort_by=popularity.desc&page=1"     to "Drama Movies",
        "$tmdbBase/discover/movie?with_genres=10749&sort_by=popularity.desc&page=1"  to "Romance Movies",
        "$tmdbBase/discover/movie?with_genres=53&sort_by=popularity.desc&page=1"     to "Thriller Movies",
        "$tmdbBase/discover/tv?with_genres=18&sort_by=popularity.desc&page=1"        to "Drama Series",
        "$tmdbBase/discover/tv?with_genres=35&sort_by=popularity.desc&page=1"        to "Comedy Series",
        "$tmdbBase/discover/tv?with_genres=10765&sort_by=popularity.desc&page=1"     to "Sci-Fi & Fantasy Series",
        "$tmdbBase/discover/tv?with_genres=80&sort_by=popularity.desc&page=1"        to "Crime Series",
        "$tmdbBase/discover/tv?with_genres=10759&sort_by=popularity.desc&page=1"     to "Action & Adventure Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("page=1", "page=$page")
        val isMovie = url.contains("/movie/") || url.contains("discover/movie")
        val response = app.get(url, headers = authHeaders).parsed<TmdbPageResponse>()
        val items = response.results.mapNotNull { it.toSearchResponse(isMovie) }
        return newHomePageResponse(request.name, items, hasNext = page < (response.total_pages ?: 1))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val movieResults = app.get(
            "$tmdbBase/search/movie?query=${query.encodeUrl()}&language=en-US&page=1",
            headers = authHeaders
        ).parsed<TmdbPageResponse>().results.mapNotNull { it.toSearchResponse(isMovie = true) }

        val tvResults = app.get(
            "$tmdbBase/search/tv?query=${query.encodeUrl()}&language=en-US&page=1",
            headers = authHeaders
        ).parsed<TmdbPageResponse>().results.mapNotNull { it.toSearchResponse(isMovie = false) }

        return movieResults + tvResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<TmdbLoadData>(url)
        val isMovie = data.type == "movie"
        val detailUrl = "$tmdbBase/${data.type}/${data.id}?language=en-US&append_to_response=credits,videos"
        val detail = app.get(detailUrl, headers = authHeaders).parsed<TmdbDetail>()

        val title = detail.title ?: detail.name ?: return null
        val poster = detail.poster_path?.let { "$imageBase$it" }
        val backdrop = detail.backdrop_path?.let { "$backdropBase$it" }
        val overview = detail.overview
        val year = (detail.release_date ?: detail.first_air_date)?.take(4)?.toIntOrNull()
        val genreNames = detail.genres?.map { it.name } ?: emptyList()

        val cast = detail.credits?.cast?.take(15)?.map {
            ActorData(Actor(it.name, it.profile_path?.let { p -> "$imageBase$p" }))
        } ?: emptyList()

        return if (isMovie) {
            newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = url
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = overview
                this.year = year
                this.tags = genreNames
                this.actors = cast
            }
        } else {
            val seasons = detail.seasons?.filter { it.season_number > 0 } ?: emptyList()

            val episodes = seasons.flatMap { season ->
                val seasonDetail = app.get(
                    "$tmdbBase/tv/${data.id}/season/${season.season_number}?language=en-US",
                    headers = authHeaders
                ).parsed<TmdbSeasonDetail>()

                seasonDetail.episodes?.map { ep ->
                    newEpisode(
                        TmdbLoadData(
                            id = data.id,
                            type = "tv",
                            season = season.season_number,
                            episode = ep.episode_number
                        ).toJson()
                    ) {
                        this.name = ep.name
                        this.season = season.season_number
                        this.episode = ep.episode_number
                        this.posterUrl = ep.still_path?.let { "$imageBase$it" }
                        this.description = ep.overview
                        this.date = ep.air_date?.let {
                            runCatching {
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    .parse(it)?.time
                            }.getOrNull()
                        }
                    }
                } ?: emptyList()
            }

            newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = overview
                this.year = year
                this.tags = genreNames
                this.actors = cast
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<TmdbLoadData>(data)

        loadVidlink(loadData, callback)

        // loadNetflixMirror(loadData, callback)

        return true
    }

    private suspend fun loadVidlink(
        loadData: TmdbLoadData,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val encRes = app.get(
            "https://enc-dec.app/api/enc-vidlink?text=${loadData.id}"
        ).parsed<EncryptResponse>()

        val encrypted = encRes.result ?: return false

        val apiUrl = if (loadData.type == "movie") {
            "https://vidlink.pro/api/b/movie/$encrypted"
        } else {
            "https://vidlink.pro/api/b/tv/$encrypted/${loadData.season}/${loadData.episode}"
        }

        val streamRes = app.get(
            apiUrl,
            headers = mapOf(
                "Referer" to "https://vidlink.pro/",
                "Origin" to "https://vidlink.pro"
            )
        ).parsed<VidlinkResponse>()

        val playlist = streamRes.stream?.playlist ?: return false

        callback(
            newExtractorLink(
                source = "Vidlink",
                name = "Vidlink",
                url = playlist,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "https://vidlink.pro/"
                this.quality = Qualities.Unknown.value
            }
        )

        return true
    }

    private suspend fun loadNetflixMirror(
        loadData: TmdbLoadData,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }

    // ─── Helpers ───────────────────────────────────────────────

    private fun TmdbResult.toSearchResponse(isMovie: Boolean): SearchResponse? {
        val title = this.title ?: this.name ?: return null
        val poster = this.poster_path?.let { "$imageBase$it" }
        val year = (this.release_date ?: this.first_air_date)?.take(4)?.toIntOrNull()
        val loadData = TmdbLoadData(id = this.id, type = if (isMovie) "movie" else "tv").toJson()

        return if (isMovie) {
            newMovieSearchResponse(name = title, url = loadData, type = TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        } else {
            newTvSeriesSearchResponse(name = title, url = loadData, type = TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")

    // ─── Data Classes ──────────────────────────────────────────

    data class TmdbLoadData(
        val id: Int,
        val type: String,
        val season: Int? = null,
        val episode: Int? = null
    )

    data class TmdbPageResponse(
        val results: List<TmdbResult>,
        val total_pages: Int?
    )

    data class TmdbResult(
        val id: Int,
        val title: String?,
        val name: String?,
        val poster_path: String?,
        val release_date: String?,
        val first_air_date: String?
    )

    data class TmdbDetail(
        val id: Int,
        val title: String?,
        val name: String?,
        val overview: String?,
        val poster_path: String?,
        val backdrop_path: String?,
        val vote_average: Double?,
        val release_date: String?,
        val first_air_date: String?,
        val genres: List<TmdbGenre>?,
        val seasons: List<TmdbSeason>?,
        val credits: TmdbCredits?,
        val videos: TmdbVideos?
    )

    data class TmdbGenre(val id: Int, val name: String)

    data class TmdbSeason(
        val season_number: Int,
        val episode_count: Int,
        val name: String?,
        val poster_path: String?
    )

    data class TmdbCredits(val cast: List<TmdbCast>?)

    data class TmdbCast(
        val name: String,
        val profile_path: String?,
        val character: String?
    )

    data class TmdbVideos(val results: List<TmdbVideo>?)

    data class TmdbVideo(
        val key: String,
        val site: String,
        val type: String
    )

    data class TmdbSeasonDetail(
        val episodes: List<TmdbEpisode>?
    )

    data class TmdbEpisode(
        val episode_number: Int,
        val name: String?,
        val overview: String?,
        val still_path: String?,
        val vote_average: Double?,
        val air_date: String?
    )

    // ─── Vidlink Data Classes ──────────────────────────────────

    data class EncryptResponse(val result: String?)

    data class VidlinkResponse(val stream: VidlinkStream?)

    data class VidlinkStream(val playlist: String?)
}
