package ani.dantotsu.media.manga.mangareader

import android.content.Context
import android.graphics.Bitmap
import ani.dantotsu.FileUrl
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation

open class BaseImageAdapter {
    companion object {
        suspend fun Context.loadBitmapOld(
            image: FileUrl,
            transformations: List<BitmapTransformation> = emptyList()
        ): Bitmap? = null

        suspend fun Context.loadBitmap(
            image: FileUrl,
            transformations: List<BitmapTransformation> = emptyList()
        ): Bitmap? = null

        fun mergeBitmap(bmp1: Bitmap, bmp2: Bitmap): Bitmap? = null
    }
}
