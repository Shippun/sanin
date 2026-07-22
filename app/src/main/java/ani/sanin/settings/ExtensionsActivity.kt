package ani.sanin.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.databinding.ActivityExtensionsBinding
import ani.sanin.extension.all.AllExtensionsFragment
import ani.sanin.extension.cloudstream.CloudstreamManager
import ani.sanin.extension.cloudstream.CloudstreamUpdateWorker
import ani.sanin.extension.aniyomi.AniyomiExtensionsFragment
import ani.sanin.extension.cloudstream.CloudstreamExtensionsFragment
import ani.sanin.initActivity
import ani.sanin.media.MediaType
import ani.sanin.navBarHeight
import ani.sanin.snackString
import ani.sanin.others.AndroidBug5497Workaround
import ani.sanin.others.LanguageMapper
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Locale

class ExtensionsActivity : AppCompatActivity() {
    lateinit var binding: ActivityExtensionsBinding
    private var currentEcosystem = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        AndroidBug5497Workaround.assistActivity(this) { showing ->
            binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (showing) statusBarHeight else statusBarHeight + navBarHeight
            }
        }
        binding.searchView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = statusBarHeight + navBarHeight
        }
        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        CloudstreamManager.init(this)

        val ecoTabLayout = findViewById<TabLayout>(R.id.ecoPillTabLayout)
        val subTabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.offscreenPageLimit = 2

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> AllExtensionsFragment()
                1 -> AniyomiExtensionsFragment()
                2 -> CloudstreamExtensionsFragment()
                else -> AllExtensionsFragment()
            }
        }

        ecoTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentEcosystem = tab.position
                viewPager.currentItem = tab.position
                subTabLayout.visibility = if (tab.position == 0) View.GONE else View.VISIBLE
                binding.languageselect.visibility =
                    if (tab.position == 0 || tab.position == 2) View.GONE else View.VISIBLE
                binding.searchViewText.setText("")
                updateRepoButton()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        TabLayoutMediator(ecoTabLayout, viewPager) { tab, _ ->
            tab.text = when (tab.position) {
                0 -> "All"
                1 -> "Aniyomi"
                2 -> "Cloudstream"
                else -> null
            }
        }.attach()

        val searchView: AutoCompleteTextView = findViewById(R.id.searchViewText)
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val tag = "f${viewPager.currentItem}"
                val frag = supportFragmentManager.findFragmentByTag(tag)
                if (frag is SearchQueryHandler) {
                    frag.updateContentBasedOnQuery(s?.toString()?.trim())
                }
            }
        })

        TvKeyboardUtil.setupTvInput(binding.searchViewText)

        binding.languageselect.setOnClickListener {
            val languageOptions = LanguageMapper.Companion.Language.entries.map {
                it.name.lowercase().replace("_", " ")
                    .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString() }
            }.toTypedArray()
            val listOrder = PrefManager.getVal<String>(PrefName.LangSort)
            val index = LanguageMapper.Companion.Language.entries.indexOfFirst { it.code == listOrder }
            customAlertDialog().apply {
                setTitle("Language")
                singleChoiceItems(languageOptions, index) { selected ->
                    PrefManager.setVal(PrefName.LangSort, LanguageMapper.Companion.Language.entries[selected].code)
                    val tag = "f${viewPager.currentItem}"
                    val frag = supportFragmentManager.findFragmentByTag(tag)
                    if (frag is SearchQueryHandler) frag.notifyDataChanged()
                }
                show()
            }
        }

        updateRepoButton()
    }

    private fun updateRepoButton() {
        binding.openSettingsButton.setOnClickListener {
            when (currentEcosystem) {
                1 -> showAniyomiRepoDialog()
                2 -> showCloudstreamRepoDialog()
                0 -> showEcosystemPickerDialog()
            }
        }
        binding.openSettingsButton.setOnLongClickListener {
            if (currentEcosystem == 2) {
                CloudstreamUpdateWorker.triggerManualUpdate(this)
                snackString("Cloudstream update check triggered")
                true
            } else false
        }
    }

    private fun showAniyomiRepoDialog() {
        val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
        AddRepositoryBottomSheet.newInstance(
            MediaType.ANIME, repos.toList(),
            AddRepositoryBottomSheet::addRepo,
            AddRepositoryBottomSheet::removeRepo
        ).show(supportFragmentManager, "add_repo")
    }

    private fun showCloudstreamRepoDialog() {
        val repos = CloudstreamManager.getRepos()
        AddRepositoryBottomSheet.newInstance(
            MediaType.ANIME, repos,
            { url, _ -> CloudstreamManager.addRepo(url) },
            { url, _ -> CloudstreamManager.removeRepo(url) }
        ).show(supportFragmentManager, "add_repo")
    }

    private fun showEcosystemPickerDialog() {
        val options = arrayOf("Aniyomi", "Cloudstream")
        customAlertDialog().apply {
            setTitle("Repository Type")
            singleChoiceItems(options) { which ->
                when (which) {
                    0 -> showAniyomiRepoDialog()
                    1 -> showCloudstreamRepoDialog()
                }
            }
            show()
        }
    }
}

interface SearchQueryHandler {
    fun updateContentBasedOnQuery(query: String?)
    fun notifyDataChanged()
}
