package ani.dantotsu.settings

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivitySettingsAddonsBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil

class SettingsAddonActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAddonsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivitySettingsAddonsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAddonsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }

            addonSettingsBack.isFocusable = true
            FocusEffectUtil.applyFocusListener(addonSettingsBack)
            addonSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(

                )
            )
            settingsRecyclerView.layoutManager =
                LinearLayoutManager(this@SettingsAddonActivity, LinearLayoutManager.VERTICAL, false)

        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
