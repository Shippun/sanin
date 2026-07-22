package ani.sanin.extension.all

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.databinding.FragmentExtensionsBinding
import ani.sanin.extension.cloudstream.CloudstreamInstalledExtension
import ani.sanin.extension.cloudstream.CloudstreamManager
import ani.sanin.extension.common.CommonExtension
import ani.sanin.extension.common.ExtensionEcosystem
import ani.sanin.others.LanguageMapper
import ani.sanin.parsers.AnimeSources
import ani.sanin.settings.ExtensionsActivity
import ani.sanin.settings.SearchQueryHandler
import ani.sanin.settings.extensionprefs.AnimeSourcePreferencesFragment
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

class AllExtensionsFragment : Fragment(), SearchQueryHandler {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!

    private val aniyomiManager: AnimeExtensionManager = Injekt.get()
    private val adapter = AllExtensionsAdapter(
        onSettings = { ext -> handleSettings(ext) },
        onUninstall = { ext -> handleUninstall(ext) },
        onUpdate = { ext -> handleUpdate(ext) },
        skipIcons = PrefManager.getVal(PrefName.SkipExtensionIcons),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.allExtensionsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            combine(
                aniyomiManager.installedExtensionsFlow,
                CloudstreamManager.installedFlow,
            ) { aniyomi, cloudstream ->
                val merged = aniyomi.map { ext -> ext.toCommon() } +
                    cloudstream.map { ext -> ext.toCommon() }
                val sorted = sortToPinnedList(merged)
                adapter.submitList(sorted)
            }.collect { }
        }
        return binding.root
    }

    private fun sortToPinnedList(list: List<CommonExtension>): List<CommonExtension> {
        val pinned = AnimeSources.pinnedAnimeSources
        val pinnedExts = list.filter { it.name in pinned }
        val ordered = pinned.mapNotNull { name -> pinnedExts.find { it.name == name } }
        return ordered + list.filter { it.name !in pinned }
    }

    private fun handleSettings(ext: CommonExtension) {
        if (ext.ecosystem != ExtensionEcosystem.ANIYOMI || ext.source !is AnimeExtension.Installed) return
        val pkg = ext.source
        val changeUIVisibility: (Boolean) -> Unit = { show ->
            val activity = requireActivity() as ExtensionsActivity
            activity.findViewById<ViewPager2>(R.id.viewPager).isVisible = show
            activity.findViewById<TabLayout>(R.id.tabLayout).isVisible = show
            activity.findViewById<TextInputLayout>(R.id.searchView).isVisible = show
            activity.findViewById<ImageView>(R.id.languageselect).isVisible = show
            activity.findViewById<TextView>(R.id.extensions).text =
                if (show) getString(R.string.extensions) else ext.name
            activity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).isGone = show
        }
        val allSettings = pkg.sources.filterIsInstance<ConfigurableAnimeSource>()
        if (allSettings.isEmpty()) {
            Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedSetting = allSettings[0]
        if (allSettings.size > 1) {
            val names = allSettings.map { LanguageMapper.getLanguageName(it.lang) }.toTypedArray()
            var selectedIndex = 0
            requireContext().customAlertDialog().apply {
                setTitle("Select a Source")
                singleChoiceItems(names, selectedIndex) { which ->
                    selectedIndex = which
                    val fragment = AnimeSourcePreferencesFragment().getInstance(allSettings[selectedIndex]) {
                        changeUIVisibility(true)
                    }
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                        .replace(R.id.fragmentExtensionsContainer, fragment)
                        .addToBackStack(null)
                        .commit()
                }
                show()
            }
        } else {
            val fragment = AnimeSourcePreferencesFragment().getInstance(selectedSetting) {
                changeUIVisibility(true)
            }
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                .replace(R.id.fragmentExtensionsContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        changeUIVisibility(false)
    }

    private fun handleUninstall(ext: CommonExtension) {
        when (ext.ecosystem) {
            ExtensionEcosystem.ANIYOMI -> aniyomiManager.uninstallExtension(ext.pkgName)
            ExtensionEcosystem.CLOUDSTREAM -> CloudstreamManager.uninstallExtension(ext.pkgName)
            else -> {}
        }
        ani.sanin.snackString("Extension uninstalled")
    }

    private fun handleUpdate(ext: CommonExtension) {
        when (ext.ecosystem) {
            ExtensionEcosystem.ANIYOMI -> {
                val installed = aniyomiManager.installedExtensionsFlow.value.find { it.pkgName == ext.pkgName }
                if (installed != null) {
                    val context = requireContext()
                    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    aniyomiManager.updateExtension(installed)
                        .observeOn(rx.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(
                            { step ->
                                val builder = androidx.core.app.NotificationCompat.Builder(
                                    context, eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_PROGRESS
                                ).setSmallIcon(R.drawable.ic_round_sync_24)
                                    .setContentTitle("Updating extension")
                                    .setContentText("Step: $step")
                                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                                notificationManager.notify(1, builder.build())
                            },
                            { error ->
                                ani.sanin.util.Logger.log(error)
                                val builder = androidx.core.app.NotificationCompat.Builder(
                                    context, eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_ERROR
                                ).setSmallIcon(R.drawable.ic_round_info_24)
                                    .setContentTitle("Update failed")
                                    .setContentText(error.message)
                                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                                notificationManager.notify(1, builder.build())
                                ani.sanin.snackString("Update failed: ${error.message}")
                            },
                            {
                                ani.sanin.snackString("Extension updated")
                            }
                        )
                }
            }
            ExtensionEcosystem.CLOUDSTREAM -> {
                val installed = CloudstreamManager.installedFlow.value.find { it.pkgName == ext.pkgName }
                if (installed != null) {
                    lifecycleScope.launch {
                        CloudstreamManager.updateExtension(installed)
                        CloudstreamManager.loadInstalledExtensions(requireContext())
                        ani.sanin.snackString("Extension updated")
                    }
                }
            }
            else -> {}
        }
    }

    override fun updateContentBasedOnQuery(query: String?) {
        adapter.filter(query ?: "", adapter.currentList)
    }

    override fun notifyDataChanged() {}

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private fun AnimeExtension.Installed.toCommon() = CommonExtension(
        name = name, pkgName = pkgName, versionName = versionName,
        versionCode = versionCode, lang = lang, isNsfw = isNsfw,
        icon = icon, iconUrl = null, hasUpdate = hasUpdate,
        isObsolete = isObsolete, ecosystem = ExtensionEcosystem.ANIYOMI, source = this,
    )

    private fun CloudstreamInstalledExtension.toCommon() = CommonExtension(
        name = name, pkgName = pkgName, versionName = versionName,
        versionCode = versionCode, lang = lang, isNsfw = isNsfw,
        icon = icon, iconUrl = null, hasUpdate = hasUpdate,
        ecosystem = ExtensionEcosystem.CLOUDSTREAM, source = this,
    )
}

class AllExtensionsAdapter(
    private val onSettings: (CommonExtension) -> Unit,
    private val onUninstall: (CommonExtension) -> Unit,
    private val onUpdate: (CommonExtension) -> Unit,
    private val skipIcons: Boolean,
) : ListAdapter<CommonExtension, AllExtensionsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_extension, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ext = getItem(position)
        holder.name.text = ext.name
        val nsfw = if (ext.isNsfw) "(18+)" else ""
        val lang = LanguageMapper.getLanguageName(ext.lang)
        val badge = when (ext.ecosystem) {
            ExtensionEcosystem.ANIYOMI -> "[Aniyomi]"
            ExtensionEcosystem.CLOUDSTREAM -> "[Cloudstream]"
            else -> ""
        }
        holder.version.text = "$lang ${ext.versionName} $nsfw"
        holder.badge.text = badge
        holder.badge.isVisible = true
        if (!skipIcons && ext.icon != null) {
            holder.icon.setImageDrawable(ext.icon)
        }
        holder.update.isVisible = ext.hasUpdate
        holder.settings.setOnClickListener { onSettings(ext) }
        holder.delete.setOnClickListener { onUninstall(ext) }
        holder.update.setOnClickListener { onUpdate(ext) }
        FocusEffectUtil.applyFocusListener(holder.itemView)
        FocusEffectUtil.applyFocusListener(holder.dragHandle)
        FocusEffectUtil.applyFocusListener(holder.settings)
        FocusEffectUtil.applyFocusListener(holder.delete)
        if (ext.hasUpdate) FocusEffectUtil.applyFocusListener(holder.update)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.extensionNameTextView)
        val version: TextView = view.findViewById(R.id.extensionVersionTextView)
        val icon: ImageView = view.findViewById(R.id.extensionIconImageView)
        val settings: ImageView = view.findViewById(R.id.settingsImageView)
        val delete: ImageView = view.findViewById(R.id.deleteTextView)
        val update: ImageView = view.findViewById(R.id.updateTextView)
        val dragHandle: LinearLayout = view.findViewById(R.id.dragHandle)
        val badge: TextView = view.findViewById(R.id.extensionBadgeTextView)
    }

    fun filter(query: String, currentList: List<CommonExtension>) {
        val filtered = if (query.isEmpty()) currentList
            else currentList.filter { it.name.contains(query, ignoreCase = true) }
        submitList(filtered)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CommonExtension>() {
            override fun areItemsTheSame(a: CommonExtension, b: CommonExtension) = a.pkgName == b.pkgName
            override fun areContentsTheSame(a: CommonExtension, b: CommonExtension) = a == b
        }
    }
}
