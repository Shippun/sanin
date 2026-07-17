package ani.sanin.settings

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsExtrasBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil

class SettingsExtrasActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsExtrasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        binding = ActivitySettingsExtrasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsExtrasLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            extrasSettingsBack.isFocusable = true
            extrasSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(
                extrasSettingsBack,
                extrasExtensions,
                extrasNotifications,
                extrasAddons,
                extrasLogCapture,
            )

            extrasExtensions.isFocusable = true
            extrasExtensions.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsExtensionsActivity::class.java))
            }

            extrasNotifications.isFocusable = true
            extrasNotifications.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsNotificationActivity::class.java))
            }

            extrasAddons.isFocusable = true
            extrasAddons.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsAddonActivity::class.java))
            }

            extrasLogCapture.isFocusable = true
            extrasLogCapture.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsLogActivity::class.java))
            }
        }
    }

    override fun onResume() {
        ThemeManager(this).applyTheme()
        super.onResume()
    }
}
