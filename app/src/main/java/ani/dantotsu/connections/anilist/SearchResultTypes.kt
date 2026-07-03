package ani.dantotsu.connections.anilist

import ani.dantotsu.media.Media

data class AniMangaSearchResults(
    val type: String?,
    val perPage: Int? = null,
    val search: String? = null,
    val sort: String? = null,
    val isAdult: Boolean? = null,
    val onList: Boolean? = null,
    val genres: List<String>? = null,
    val excludedGenres: List<String>? = null,
    val tags: List<String>? = null,
    val excludedTags: List<String>? = null,
    val status: String? = null,
    val source: String? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val startYear: Int? = null,
    val seasonYear: Int? = null,
    val season: String? = null,
    val results: List<Media>,
    val page: Int,
    val hasNextPage: Boolean,
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
