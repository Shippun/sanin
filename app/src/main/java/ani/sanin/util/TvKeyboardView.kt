package ani.sanin.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import ani.sanin.R

class TvKeyboardView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val compact: Boolean = false
) : FrameLayout(context, attrs, defStyleAttr) {

    private val primaryColor = FocusEffectUtil.getPrimaryColor(context)

    var target: EditText? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null && !compact && !isVisible) show()
            }
        }

    private var isSymbolsMode = false

    private lateinit var modeToggle: TextView
    private val letterKeys = mutableListOf<TextView>()
    private val allKeys = mutableListOf<TextView>()

    private val letters = listOf(
        "Q","W","E","R","T","Y","U","I","O","P",
        "A","S","D","F","G","H","J","K","L",
        "Z","X","C","V","B","N","M",",","."
    )

    private val symbols = listOf(
        "1","2","3","4","5","6","7","8","9","0",
        "-","/",":",";","(",")","$","&","@",
        "+","=","[","]","{","}","#","%","*"
    )

    init {
        inflate(context, if (compact) R.layout.tv_keyboard_compact else R.layout.tv_keyboard_view, this)
        setupKeys()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeys() {
        val letterIds = listOf(
            R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT,
            R.id.keyY, R.id.keyU, R.id.keyI, R.id.keyO, R.id.keyP,
            R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF, R.id.keyG,
            R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL,
            R.id.keyZ, R.id.keyX, R.id.keyC, R.id.keyV, R.id.keyB,
            R.id.keyN, R.id.keyM, R.id.keyComma, R.id.keyPeriod
        )

        modeToggle = findViewById(R.id.keyModeToggle)

        for (id in letterIds) {
            val v = findViewById<TextView>(id)
            letterKeys.add(v)
            allKeys.add(v)
        }

        val specialIds = listOf(
            R.id.keyBackspace, R.id.keyEnter, R.id.keySpace,
            R.id.keyHide, R.id.keyModeToggle
        )

        for (id in specialIds) {
            allKeys.add(findViewById(id))
        }

        for (v in allKeys) {
            v.setOnClickListener { onKeyClick(v) }
            v.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) applyKeyFocus(view)
                else removeKeyFocus(view)
            }
        }
    }

    private fun applyKeyFocus(v: View) {
        val scale = if (compact) 1.08f else 1.12f
        v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
        val borderPx = (if (compact) 1f else 2f) * v.resources.displayMetrics.density
        val corner = (if (compact) 3f else 6f) * v.resources.displayMetrics.density
        v.background = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(0x4DFFFFFF.toInt())
            setStroke(borderPx.toInt(), primaryColor)
            cornerRadius = corner
        }
    }

    private fun removeKeyFocus(v: View) {
        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        v.setBackgroundColor(0x1AFFFFFF.toInt())
    }

    private fun onKeyClick(view: TextView) {
        val editText = target ?: return
        val text = editText.text ?: return
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)
        val minPos = minOf(start, end)
        val maxPos = maxOf(start, end)

        when (view.id) {
            R.id.keyBackspace -> {
                if (minPos != maxPos) {
                    text.delete(minPos, maxPos)
                } else if (minPos > 0) {
                    text.delete(minPos - 1, minPos)
                }
            }
            R.id.keyEnter -> {
                editText.onEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            R.id.keySpace -> {
                text.insert(minPos, " ")
                editText.setSelection(minPos + 1)
            }
            R.id.keyHide -> {
                hide()
                editText.clearFocus()
            }
            R.id.keyModeToggle -> toggleMode()
            else -> {
                val char = view.text?.toString() ?: return
                text.insert(minPos, char)
                editText.setSelection(minPos + char.length)
            }
        }
    }

    private fun toggleMode() {
        isSymbolsMode = !isSymbolsMode
        val chars = if (isSymbolsMode) symbols else letters
        for (i in letterKeys.indices) {
            letterKeys[i].text = chars.getOrElse(i) { "" }
        }
        modeToggle.text = if (isSymbolsMode) "ABC" else "\u003F123"
    }

    private var keyboardHeight = 0

    fun show() {
        if (compact) {
            visibility = VISIBLE
            return
        }
        animate().cancel()
        if (isVisible) return
        visibility = VISIBLE
        requestLayout()
        post {
            keyboardHeight = height
            translationY = height.toFloat()
            animate().translationY(0f).setDuration(200).start()
        }
    }

    fun hide() {
        if (compact) {
            visibility = GONE
            return
        }
        animate().cancel()
        if (!isVisible) return
        val h = if (keyboardHeight > 0) keyboardHeight else height
        animate().translationY(h.toFloat()).setDuration(200).withEndAction {
            visibility = GONE
            translationY = 0f
        }.start()
    }

    fun isKeyboardVisible(): Boolean = isVisible
}
