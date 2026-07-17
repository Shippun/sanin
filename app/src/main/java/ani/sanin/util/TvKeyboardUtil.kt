package ani.sanin.util

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

object TvKeyboardUtil {

    fun ensureKeyboardOnFocus(editText: EditText) {
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                showKeyboardDelayed(v)
            }
        }
    }

    fun showKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    }

    fun showKeyboardDelayed(view: View, delayMs: Long = 150) {
        view.postDelayed({
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return@postDelayed
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }, delayMs)
    }

    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun TextView.setupTvKeyboard() {
        setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                showKeyboardDelayed(v)
            }
        }
    }
}
