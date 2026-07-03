package ani.dantotsu.parsers

import ani.dantotsu.FileUrl
import java.io.Serializable

data class Book(
    val name: String = "",
    val link: String = "",
    val coverUrl: FileUrl? = null,
    val chapters: List<Any> = emptyList(),
) : Serializable
