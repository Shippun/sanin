package ani.dantotsu.settings

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivitySettingsAnimeBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.restartApp
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.customAlertDialog

class SettingsHomeActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsAnimeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        binding = ActivitySettingsAnimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAnimeLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            animeSettingsBack.isFocusable = true
            animeSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(animeSettingsBack)

            val homeBannerModes = arrayOf(
                getString(R.string.home_banner_carousel),
                getString(R.string.home_banner_profile),
                getString(R.string.home_banner_navigating),
                getString(R.string.home_banner_off)
            )

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(R.string.home_banner_mode),
                        desc = getString(R.string.home_banner_mode_desc),
                        icon = R.drawable.ic_round_filter_list_24,
                        onClick = {
                            customAlertDialog().apply {
                                setTitle(getString(R.string.home_banner_mode))
                                singleChoiceItems(
                                    homeBannerModes,
                                    PrefManager.getVal<Int>(PrefName.HomeBannerMode)
                                ) { index ->
                                    PrefManager.setVal(PrefName.HomeBannerMode, index)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.hero_card_image),
                        desc = getString(R.string.hero_card_image_desc),
                        icon = R.drawable.ic_round_image_search_24,
                        isChecked = PrefManager.getVal(PrefName.HeroCardImage),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.HeroCardImage, checked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Continue Watching",
                        desc = "Display continue watching section on home",
                        icon = R.drawable.ic_round_playlist_play_24,
                        isChecked = PrefManager.getVal(PrefName.ShowContinueWatching),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowContinueWatching, checked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Planned",
                        desc = "Display planned/watchlist section on home",
                        icon = R.drawable.ic_round_library_books_24,
                        isChecked = PrefManager.getVal(PrefName.ShowPlanned),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowPlanned, checked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Recommendations",
                        desc = "Display recommendations section on home",
                        icon = R.drawable.ic_round_star_graph_24,
                        isChecked = PrefManager.getVal(PrefName.ShowRecommendations),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowRecommendations, checked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Trending",
                        desc = "Display trending section on home",
                        icon = R.drawable.ic_round_area_chart_24,
                        isChecked = PrefManager.getVal(PrefName.ShowTrending),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowTrending, checked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Popular",
                        desc = "Display popular section on home",
                        icon = R.drawable.ic_round_favorite_24,
                        isChecked = PrefManager.getVal(PrefName.ShowPopular),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowPopular, checked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Show Recent",
                        desc = "Display recently updated section on home",
                        icon = R.drawable.ic_round_new_releases_24,
                        isChecked = PrefManager.getVal(PrefName.ShowRecent),
                        switch = { checked, _ ->
                            PrefManager.setVal(PrefName.ShowRecent, checked)
                            restartApp()
                        }
                    ),
                )
            )

            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@SettingsHomeActivity, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }

    override fun onResume() {
        ThemeManager(this).applyTheme()
        super.onResume()
    }
}
