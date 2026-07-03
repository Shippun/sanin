package ani.dantotsu.connections.anilist

import ani.dantotsu.media.Media

data class AniMangaSearchResults(
    var type: String = "",
    var perPage: Int? = null,
    var search: String? = null,
    var sort: String? = null,
    var isAdult: Boolean = false,
    var onList: Boolean? = null,
    var genres: MutableList<String>? = null,
    var excludedGenres: MutableList<String>? = null,
    var tags: MutableList<String>? = null,
    var excludedTags: MutableList<String>? = null,
    var status: String? = null,
    var source: String? = null,
    var format: String? = null,
    var countryOfOrigin: String? = null,
    var startYear: Int? = null,
    var seasonYear: Int? = null,
    var season: String? = null,
    var results: MutableList<Media> = mutableListOf(),
    var page: Int = 0,
    var hasNextPage: Boolean = false,
)

data class CharacterSearchResults(
    val search: String?,
    val results: List<Any>,
    val page: Int,
    val hasNextPage: Boolean,
)

data class StudioSearchResults(
    val search: String?,
    val results: List<Any>,
    val page: Int,
    val hasNextPage: Boolean,
)

data class StaffSearchResults(
    val search: String?,
    val results: List<Any>,
    val page: Int,
    val hasNextPage: Boolean,
)

data class UserSearchResults(
    val search: String?,
    val results: List<Any>,
    val page: Int,
    val hasNextPage: Boolean,
)
