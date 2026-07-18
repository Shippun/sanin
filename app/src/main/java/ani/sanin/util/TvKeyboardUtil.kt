package ani.sanin.util

import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

object TvKeyboardUtil {

    fun setupTvInput(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                backHandled[v] = false
                showKeyboardDelayed(v)
                applyFocusBorder(v)
            } else {
                removeFocusBorder(v)
            }
        }
    }

    fun ensureKeyboardOnFocus(editText: EditText) {
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                backHandled[v] = false
                showKeyboardDelayed(v)
            }
        }
    }

    fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    private val backDismissed = mutableSetOf<View>()

    fun showKeyboardDelayed(view: View, delayMs: Long = 150) {
        if (backDismissed.add(view)) {
            setupBackDismiss(view)
        }
        view.postDelayed({
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return@postDelayed
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }, delayMs)
    }

    fun hideKeyboard(view: View) {
        view.clearFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun TextView.setupTvKeyboard() {
        setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                backHandled[v] = false
                showKeyboardDelayed(v)
            }
        }
    }

    private val backHandled = mutableMapOf<View, Boolean>()

    fun setupBackDismiss(view: View) {
        view.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (backHandled[v] == true) {
                    backHandled[v] = false
                    return@setOnKeyListener false
                }
                backHandled[v] = true
                v.clearFocus()
                (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.let { imm ->
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun applyTvFocusBorder(v: View) {
        if (!isTv(v.context)) return
        val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
        val borderWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3f, v.resources.displayMetrics
        ).toInt()
        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, v.resources.displayMetrics
        ).toInt()

        val borderDrawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(borderWidthPx, primaryColor)
            setCornerRadius(cornerRadiusPx.toFloat())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = borderDrawable
        }
    }

    fun removeTvFocusBorder(v: View) {
        if (!isTv(v.context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = null
        }
    }

    private fun applyFocusBorder(v: View) = applyTvFocusBorder(v)
    private fun removeFocusBorder(v: View) = removeTvFocusBorder(v)
}
