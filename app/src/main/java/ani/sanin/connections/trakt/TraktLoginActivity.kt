package ani.sanin.connections.trakt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.sanin.logError
import ani.sanin.startMainActivity
import ani.sanin.themes.ThemeManager
import kotlinx.coroutines.launch

class TraktLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()

        val data = intent?.data?.toString() ?: run {
            startMainActivity(this)
            finish()
            return
        }

        val code = Regex("""(?<=code=)[^&]+""").find(data)?.value
        if (code != null) {
            lifecycleScope.launch {
                TraktAuth.exchangeCode(code)
                startMainActivity(this@TraktLoginActivity)
                finish()
            }
        } else {
            startMainActivity(this)
            finish()
        }
    }
}
