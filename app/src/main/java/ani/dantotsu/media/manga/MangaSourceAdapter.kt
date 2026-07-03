package ani.dantotsu.media.manga

import android.view.View
import android.view.ViewGroup

open class MangaSourceAdapter(
    position: Int,
    model: Any?,
    sourceIndex: Int,
    mediaId: Int,
    fragment: Any?,
    scope: Any?,
) {
    open fun getCount(): Int = 0
    open fun getItem(position: Int): Any? = null
    open fun getItemViewType(position: Int): Int = 0
    open fun isEnabled(position: Int): Boolean = false
}
