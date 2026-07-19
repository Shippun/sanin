package ani.sanin.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

object TvKeyboardUtil {

    private val TAG_KEYBOARD = "tv_custom_keyboard"

    private fun useCustomKeyboard(): Boolean = PrefManager.getVal(PrefName.UseCustomKeyboard)

    fun setupTvInput(view: View) {
        retainWindowFocus(view)

        if (useCustomKeyboard()) {
            if (view is EditText) {
                view.showSoftInputOnFocus = false
            }
            attachKeyboardToWindow(view)
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if (v is EditText) showCustomKeyboard(v)
                    applyFocusBorder(v)
                } else {
                    hideCustomKeyboard(v)
                    removeFocusBorder(v)
                }
            }
        } else {
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    showKeyboardDelayed(v)
                    applyFocusBorder(v)
                } else {
                    removeFocusBorder(v)
                }
            }
        }
    }

    fun ensureKeyboardOnFocus(editText: EditText) {
        retainWindowFocus(editText)

        if (useCustomKeyboard()) {
            editText.showSoftInputOnFocus = false
            attachKeyboardToWindow(editText)
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) showCustomKeyboard(v)
                else hideCustomKeyboard(v)
            }
        } else {
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) showKeyboardDelayed(v)
            }
        }
    }

    fun showKeyboard(view: View) {
        if (useCustomKeyboard()) {
            view.requestFocus()
            if (view is EditText) showCustomKeyboard(view)
        } else {
            view.requestFocus()
            retainWindowFocus(view)
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    fun showKeyboardDelayed(view: View, delayMs: Long = 150) {
        if (useCustomKeyboard()) {
            view.postDelayed({
                if (view is EditText) showCustomKeyboard(view)
            }, delayMs)
        } else {
            retainWindowFocus(view)
            view.postDelayed({
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    ?: return@postDelayed
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            }, delayMs)
        }
    }

    fun hideKeyboard(view: View) {
        view.clearFocus()
        if (useCustomKeyboard()) {
            hideCustomKeyboard(view)
        }
    }

    fun retainWindowFocus(window: Window) {
        if (Build.VERSION.SDK_INT >= 24) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE)
        }
    }

    private fun retainWindowFocus(view: View) {
        (view.context as? Activity)?.window?.let { retainWindowFocus(it) }
    }

    fun TextView.setupTvKeyboard() {
        if (useCustomKeyboard()) {
            if (this is EditText) {
                showSoftInputOnFocus = false
            }
            attachKeyboardToWindow(this)
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus && v is EditText) showCustomKeyboard(v)
                else if (!hasFocus) hideCustomKeyboard(v)
            }
        } else {
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) showKeyboardDelayed(v)
            }
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

    private fun attachKeyboardToWindow(view: View) {
        val activity = view.context as? Activity ?: return
        getOrCreateKeyboard(activity)
    }

    private fun getOrCreateKeyboard(activity: Activity): TvKeyboardView {
        val decorView = activity.window.decorView as? ViewGroup
            ?: error("Cannot access decor view")
        var keyboard = decorView.findViewWithTag<TvKeyboardView>(TAG_KEYBOARD)
        if (keyboard == null) {
            keyboard = TvKeyboardView(activity).apply {
                this.tag = TAG_KEYBOARD
                visibility = View.GONE
            }
            decorView.addView(
                keyboard,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return keyboard
    }

    private fun showCustomKeyboard(view: View) {
        val editText = view as? EditText ?: return
        val activity = editText.context as? Activity ?: return
        getOrCreateKeyboard(activity).apply {
            target = editText
            show()
        }
    }

    private fun hideCustomKeyboard(view: View) {
        val activity = view.context as? Activity ?: return
        getOrCreateKeyboard(activity).hide()
    }
}
