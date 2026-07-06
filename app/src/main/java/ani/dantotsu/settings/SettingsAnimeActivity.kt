package ani.dantotsu.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivitySettingsAnimeBinding
import ani.dantotsu.download.DownloadsManager
import ani.dantotsu.initActivity
import ani.dantotsu.media.MediaType
import ani.dantotsu.navBarHeight
import ani.dantotsu.restartApp
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.customAlertDialog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsAnimeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAnimeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsAnimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {

            settingsAnimeLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            animeSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(animeSettingsBack)

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(R.string.player_settings),
                        desc = getString(R.string.player_settings_desc),
                        icon = R.drawable.ic_round_video_settings_24,
                        onClick = {
                            startActivity(Intent(context, PlayerSettingsActivity::class.java))
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.purge_anime_downloads),
                        desc = getString(R.string.purge_anime_downloads_desc),
                        icon = R.drawable.ic_round_delete_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle(R.string.purge_anime_downloads)
                                setMessage(R.string.purge_confirm, getString(R.string.anime))
                                setPosButton(R.string.yes, onClick = {
                                    val downloadsManager = Injekt.get<DownloadsManager>()
                                    downloadsManager.purgeDownloads(MediaType.ANIME)
                                })
                                setNegButton(R.string.no)
                                show()
                            }
                        }

                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.prefer_dub),
                        desc = getString(R.string.prefer_dub_desc),
                        icon = R.drawable.ic_round_audiotrack_24,
                        isChecked = PrefManager.getVal(PrefName.SettingsPreferDub),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.SettingsPreferDub, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.include_list),
                        desc = getString(R.string.include_list_anime_desc),
                        icon = R.drawable.view_list_24,
                        isChecked = PrefManager.getVal(PrefName.IncludeAnimeList),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.IncludeAnimeList, isChecked)
                            restartApp()
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Pause Overlay",
                        desc = "Show pause overlay when video is paused",
                        icon = R.drawable.ic_round_pause_24,
                        isChecked = PrefManager.getVal(PrefName.PauseOverlay),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.PauseOverlay, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Gesture Sliders",
                        desc = "Brightness/volume sliders via vertical gestures",
                        icon = R.drawable.ic_round_swipe_vertical_24,
                        isChecked = PrefManager.getVal(PrefName.GestureSliders),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.GestureSliders, isChecked)
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Auto-Hide Timeout",
                        desc = "Seconds before player controls auto-hide",
                        icon = R.drawable.ic_round_history_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Auto-Hide Timeout")
                                val values = arrayOf("2s", "3s", "4s", "5s", "6s", "8s", "10s")
                                singleChoiceItems(
                                    values,
                                    PrefManager.getVal<Int>(PrefName.AutoHideTimeout) / 2 - 1
                                ) { index ->
                                    PrefManager.setVal(PrefName.AutoHideTimeout, (index + 1) * 2)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = "Buffer Size",
                        desc = "Video buffer size in MB",
                        icon = R.drawable.ic_round_sd_card_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle("Buffer Size")
                                val values = arrayOf("16 MB", "32 MB", "64 MB", "128 MB")
                                singleChoiceItems(
                                    values,
                                    when (PrefManager.getVal<Int>(PrefName.BufferSize)) {
                                        16 -> 0; 32 -> 1; 64 -> 2; 128 -> 3; else -> 1
                                    }
                                ) { index ->
                                    PrefManager.setVal(PrefName.BufferSize, intArrayOf(16, 32, 64, 128)[index])
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Hardware Decoding",
                        desc = "Use hardware video decoder",
                        icon = R.drawable.ic_round_brightness_high_24,
                        isChecked = PrefManager.getVal(PrefName.HardwareDecoding),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.HardwareDecoding, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Smart Source Persistence",
                        desc = "Remember source selection across sessions",
                        icon = R.drawable.ic_round_smart_button_24,
                        isChecked = PrefManager.getVal(PrefName.SmartSourcePersistence),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.SmartSourcePersistence, isChecked)
                        }
                    ),
                )
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            var previousEp: View = when (PrefManager.getVal<Int>(PrefName.AnimeDefaultView)) {
                0 -> settingsEpList
                1 -> settingsEpGrid
                2 -> settingsEpCompact
                else -> settingsEpList
            }
            previousEp.alpha = 1f
            fun uiEp(mode: Int, current: View) {
                previousEp.alpha = 0.33f
                previousEp = current
                current.alpha = 1f
                PrefManager.setVal(PrefName.AnimeDefaultView, mode)
            }

            settingsEpList.setOnClickListener {
                uiEp(0, it)
            }

            settingsEpGrid.setOnClickListener {
                uiEp(1, it)
            }

            settingsEpCompact.setOnClickListener {
                uiEp(2, it)
            }

        }
    }
}
