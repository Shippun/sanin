package ani.sanin.extension.cloudstream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import ani.sanin.snackString
import ani.sanin.util.FocusEffectUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CloudstreamAvailableFragment : Fragment() {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding get() = _binding!!
    private var selectedRepoUrl: String? = null
    private var selectedCategory: String? = null

    private val reposAdapter = ReposAdapter(
        onRepoClick = { repo -> openRepo(repo) },
        onDelete = { repo -> deleteRepo(repo) },
    )

    private val categoryAdapter = CategoryAdapter(
        onCategoryClick = { category -> openCategory(category) },
    )

    private val extensionAdapter = CsAvailableAdapter(
        onInstall = { ext -> installExtension(ext) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.allExtensionsRecyclerView.adapter = reposAdapter

        lifecycleScope.launch {
            CloudstreamManager.reposFlow.collect { repos ->
                if (selectedRepoUrl == null) {
                    reposAdapter.submitList(repos)
                }
            }
        }
        lifecycleScope.launch { CloudstreamManager.refreshRepos() }
        return binding.root
    }

    private fun openRepo(repo: CloudstreamRepo) {
        selectedRepoUrl = repo.url
        val categories = CloudstreamManager.getCategoriesForRepo(repo.url)
        if (categories.size <= 1 && categories.firstOrNull()?.name == "Uncategorized") {
            binding.allExtensionsRecyclerView.adapter = extensionAdapter
            extensionAdapter.submitList(categories.firstOrNull()?.extensions ?: emptyList())
        } else {
            binding.allExtensionsRecyclerView.adapter = categoryAdapter
            categoryAdapter.submitList(categories)
        }
    }

    private fun openCategory(category: CloudstreamRepoCategory) {
        val repo = CloudstreamManager.reposFlow.value.find { it.url == selectedRepoUrl } ?: return
        val extensions = repo.extensions.filter { it.category.ifEmpty { "Uncategorized" } == category.name }
        binding.allExtensionsRecyclerView.adapter = extensionAdapter
        extensionAdapter.submitList(extensions)
    }

    private fun deleteRepo(repo: CloudstreamRepo) {
        CloudstreamManager.removeRepo(repo.url)
        snackString("Repository removed")
        lifecycleScope.launch { CloudstreamManager.refreshRepos() }
    }

    private fun installExtension(ext: CloudstreamAvailableExtension) {
        lifecycleScope.launch {
            CloudstreamManager.installExtension(ext)
            CloudstreamManager.loadInstalledExtensions(requireContext())
            val current = extensionAdapter.currentList.toMutableList()
            val idx = current.indexOfFirst { it.pkgName == ext.pkgName }
            if (idx >= 0) {
                current[idx] = current[idx].copy() // trigger diff
                extensionAdapter.submitList(current)
            }
            snackString("${ext.name} installed")
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

private class ReposAdapter(
    private val onRepoClick: (CloudstreamRepo) -> Unit,
    private val onDelete: (CloudstreamRepo) -> Unit,
) : ListAdapter<CloudstreamRepo, ReposAdapter.ViewHolder>(DIFF_REPO) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cloudstream_repo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val repo = getItem(position)
        holder.name.text = repo.name
        holder.url.text = repo.url
        holder.count.text = "${repo.extensions.size} extensions"
        holder.itemView.setOnClickListener { onRepoClick(repo) }
        holder.delete.setOnClickListener { onDelete(repo) }
        FocusEffectUtil.applyFocusListener(holder.itemView)
        FocusEffectUtil.applyFocusListener(holder.delete)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.repoName)
        val url: TextView = view.findViewById(R.id.repoUrl)
        val count: TextView = view.findViewById(R.id.repoCount)
        val delete: ImageView = view.findViewById(R.id.repoDelete)
    }

    companion object { val DIFF_REPO = object : DiffUtil.ItemCallback<CloudstreamRepo>() {
        override fun areItemsTheSame(a: CloudstreamRepo, b: CloudstreamRepo) = a.url == b.url
        override fun areContentsTheSame(a: CloudstreamRepo, b: CloudstreamRepo) = a == b
    }}
}

private class CategoryAdapter(
    private val onCategoryClick: (CloudstreamRepoCategory) -> Unit,
) : ListAdapter<CloudstreamRepoCategory, CategoryAdapter.ViewHolder>(DIFF_CAT) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = getItem(position)
        holder.textView.text = "${cat.name} (${cat.extensions.size})"
        holder.itemView.setOnClickListener { onCategoryClick(cat) }
        FocusEffectUtil.applyFocusListener(holder.itemView)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    companion object { val DIFF_CAT = object : DiffUtil.ItemCallback<CloudstreamRepoCategory>() {
        override fun areItemsTheSame(a: CloudstreamRepoCategory, b: CloudstreamRepoCategory) = a.name == b.name
        override fun areContentsTheSame(a: CloudstreamRepoCategory, b: CloudstreamRepoCategory) = a == b
    }}
}

private class CsAvailableAdapter(
    private val onInstall: (CloudstreamAvailableExtension) -> Unit,
) : ListAdapter<CloudstreamAvailableExtension, CsAvailableAdapter.ViewHolder>(DIFF_AVAIL) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_extension_all, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ext = getItem(position)
        holder.name.text = ext.name
        holder.version.text = "${ext.lang} ${ext.versionName}"
        holder.install.setOnClickListener { onInstall(ext) }
        FocusEffectUtil.applyFocusListener(holder.itemView)
        FocusEffectUtil.applyFocusListener(holder.install)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.extensionNameTextView)
        val version: TextView = view.findViewById(R.id.extensionVersionTextView)
        val icon: ImageView = view.findViewById(R.id.extensionIconImageView)
        val install: ImageView = view.findViewById(R.id.closeTextView)
    }

    companion object { val DIFF_AVAIL = object : DiffUtil.ItemCallback<CloudstreamAvailableExtension>() {
        override fun areItemsTheSame(a: CloudstreamAvailableExtension, b: CloudstreamAvailableExtension) = a.pkgName == b.pkgName
        override fun areContentsTheSame(a: CloudstreamAvailableExtension, b: CloudstreamAvailableExtension) = a == b
    }}
}
