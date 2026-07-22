package ani.sanin.extension.cloudstream

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.databinding.FragmentExtensionsBinding
import ani.sanin.others.LanguageMapper
import ani.sanin.snackString
import ani.sanin.util.FocusEffectUtil
import kotlinx.coroutines.launch
import rx.android.schedulers.AndroidSchedulers

class CloudstreamInstalledFragment : Fragment() {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!

    private val adapter = CsInstalledAdapter(
        onUpdate = { ext -> handleUpdate(ext) },
        onUninstall = { ext -> handleUninstall(ext) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.allExtensionsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            CloudstreamManager.installedFlow.collect { list ->
                adapter.submitList(list)
            }
        }
        return binding.root
    }

    private fun handleUpdate(ext: CloudstreamInstalledExtension) {
        if (!ext.hasUpdate) { snackString("No update available"); return }
        val context = requireContext()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        CloudstreamManager.updateExtension(ext)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { step ->
                    val builder = androidx.core.app.NotificationCompat.Builder(
                        context, eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_PROGRESS
                    ).setSmallIcon(R.drawable.ic_round_sync_24)
                        .setContentTitle("Updating Cloudstream extension")
                        .setContentText("Step: $step")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                    notificationManager.notify(1, builder.build())
                },
                { error ->
                    snackString("Update failed: ${error.message}")
                    val builder = androidx.core.app.NotificationCompat.Builder(
                        context, eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_ERROR
                    ).setSmallIcon(R.drawable.ic_round_info_24)
                        .setContentTitle("Update failed")
                        .setContentText(error.message)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    notificationManager.notify(1, builder.build())
                },
                { snackString("Extension updated") }
            )
    }

    private fun handleUninstall(ext: CloudstreamInstalledExtension) {
        CloudstreamManager.uninstallExtension(ext.pkgName)
        snackString("Extension uninstalled")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

private class CsInstalledAdapter(
    private val onUpdate: (CloudstreamInstalledExtension) -> Unit,
    private val onUninstall: (CloudstreamInstalledExtension) -> Unit,
) : ListAdapter<CloudstreamInstalledExtension, CsInstalledAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_extension, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ext = getItem(position)
        holder.name.text = ext.name
        val lang = if (ext.lang.isNotEmpty()) LanguageMapper.getLanguageName(ext.lang) else ""
        holder.version.text = "$lang ${ext.versionName}"
        if (ext.icon != null) holder.icon.setImageDrawable(ext.icon)
        holder.update.isVisible = ext.hasUpdate
        holder.settings.visibility = View.GONE
        holder.dragHandle.visibility = View.GONE
        holder.badge.visibility = View.GONE
        holder.update.setOnClickListener { onUpdate(ext) }
        holder.delete.setOnClickListener { onUninstall(ext) }
        FocusEffectUtil.applyFocusListener(holder.itemView)
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

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CloudstreamInstalledExtension>() {
            override fun areItemsTheSame(a: CloudstreamInstalledExtension, b: CloudstreamInstalledExtension) = a.pkgName == b.pkgName
            override fun areContentsTheSame(a: CloudstreamInstalledExtension, b: CloudstreamInstalledExtension) = a == b
        }
    }
}
