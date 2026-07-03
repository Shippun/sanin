package ani.dantotsu.connections.anilist

import ani.dantotsu.media.Media

data class AniMangaSearchResults(
    var type: String = "",
    var perPage: Int = 0,
    var search: String = "",
    var sort: String = "",
    var isAdult: Boolean = false,
    var onList: Boolean = false,
    var genres: MutableList<String>? = null,
    var excludedGenres: MutableList<String>? = null,
    var tags: MutableList<String>? = null,
    var excludedTags: MutableList<String>? = null,
    var status: String = "",
    var source: String = "",
    var format: String = "",
    var countryOfOrigin: String = "",
    var startYear: Int = 0,
    var seasonYear: Int = 0,
    var season: String = "",
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
