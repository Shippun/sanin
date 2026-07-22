package ani.sanin.settings

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.copyToClipboard
import ani.sanin.databinding.BottomSheetAddRepositoryBinding
import ani.sanin.databinding.ItemRepoBinding
import ani.sanin.extension.cloudstream.CloudstreamManager
import ani.sanin.extension.cloudstream.CloudstreamRepoParser
import ani.sanin.extension.cloudstream.RepoType
import ani.sanin.media.MediaType
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import ani.sanin.util.FocusEffectUtil
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.viewbinding.BindableItem
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RepoItem(
    val url: String,
    private val mediaType: MediaType,
    val onRemove: (String, MediaType) -> Unit
) : BindableItem<ItemRepoBinding>() {
    override fun getLayout() = R.layout.item_repo

    override fun bind(viewBinding: ItemRepoBinding, position: Int) {
        viewBinding.repoNameTextView.text = url.cleanShownUrl()
        viewBinding.repoDeleteImageView.setOnClickListener {
            onRemove(url, mediaType)
        }
        viewBinding.repoCopyImageView.setOnClickListener {
            viewBinding.repoCopyImageView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            copyToClipboard(url, true)
        }
    }

    override fun initializeViewBinding(view: View): ItemRepoBinding {
        return ItemRepoBinding.bind(view)
    }

    private fun String.cleanShownUrl(): String {
        return this
            .removePrefix("https://raw.githubusercontent.com/")
            .replace("index.min.json", "")
            .replace("repo.json", "")
            .removeSuffix("/")
    }
}

class AddRepositoryBottomSheet : DialogFragment() {
    private var _binding: BottomSheetAddRepositoryBinding? = null
    private val binding get() = _binding!!
    private var mediaType: MediaType = MediaType.ANIME
    private var onRepositoryAdded: ((String, MediaType) -> Unit)? = null
    private var repositories: MutableList<String> = mutableListOf()
    private var onRepositoryRemoved: ((String, MediaType) -> Unit)? = null
    private var adapter: GroupieAdapter = GroupieAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddRepositoryBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.apply {
            setGravity(Gravity.TOP)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawableResource(R.drawable.top_sheet_background)
        }

        binding.repositoriesRecyclerView.adapter = adapter
        binding.repositoriesRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.VERTICAL,
            false
        )
        adapter.addAll(repositories.map { RepoItem(it, mediaType, ::onRepositoryRemoved) })

        dialog?.window?.let { TvKeyboardUtil.retainWindowFocus(it) }
        binding.repositoryInput.hint = getString(R.string.anime_add_repository)
        TvKeyboardUtil.setupTvInput(binding.repositoryInput)

        binding.addButton.setOnClickListener {
            val input = binding.repositoryInput.text.toString()
            if (input.isNotBlank()) {
                acceptUrl(input)
            } else {
                binding.repositoryInput.error = "URL cannot be empty"
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.repositoryInput.setOnEditorActionListener { textView, action, keyEvent ->
            if (action == EditorInfo.IME_ACTION_DONE ||
                (keyEvent?.action == KeyEvent.ACTION_UP && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val url = textView.text.toString()
                if (url.isNotBlank()) {
                    acceptUrl(url)
                    return@setOnEditorActionListener true
                }
            }
            false
        }
    }

    private fun acceptUrl(url: String) {
        val finalUrl = getRepoUrl(url)
        context?.let { ctx ->
            addRepoWarning(ctx) {
                binding.addButton.isEnabled = false
                CoroutineScope(Dispatchers.IO).launch {
                    val result = CloudstreamRepoParser.detectRepoType(finalUrl)
                    if (result == null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            binding.addButton.isEnabled = true
                            binding.repositoryInput.error = "Failed to fetch repository"
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        when (result.type) {
                            RepoType.ANIYOMI -> addAniyomiRepo(finalUrl)
                            RepoType.CLOUDSTREAM_PACKS, RepoType.CLOUDSTREAM_PLUGINLISTS -> addCloudstreamRepo(finalUrl)
                            RepoType.UNKNOWN -> {
                                binding.addButton.isEnabled = true
                                binding.repositoryInput.error = "Unsupported repository format"
                            }
                        }
                    }
                    dismiss()
                }
            }
        }
    }

    private fun addAniyomiRepo(url: String) {
        val normalizedUrl = if (!url.contains("index.min.json")) {
            "${url.trimEnd('/')}/index.min.json"
        } else url
        val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos).plus(normalizedUrl)
        PrefManager.setVal(PrefName.AnimeExtensionRepos, repos)
        CoroutineScope(Dispatchers.IO).launch {
            Injekt.get<AnimeExtensionManager>().findAvailableExtensions()
        }
        onRepositoryAdded?.invoke(normalizedUrl, mediaType)
    }

    private fun addCloudstreamRepo(url: String) {
        CloudstreamManager.addRepo(url)
        CoroutineScope(Dispatchers.IO).launch {
            CloudstreamManager.refreshRepos()
        }
        onRepositoryAdded?.invoke(url, mediaType)
    }

    private fun getRepoUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input
        }

        val parts = input.split("/")
        val username = parts[0]
        val repo = parts[1]
        val branch = if (parts.size == 3) parts[2] else "repo"

        return "https://raw.githubusercontent.com/$username/$repo/$branch/index.min.json"
    }

    private fun onRepositoryRemoved(url: String, mediaType: MediaType) {
        onRepositoryRemoved?.invoke(url, mediaType)
        repositories.remove(url)
        adapter.update(repositories.map { RepoItem(it, mediaType, ::onRepositoryRemoved) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun addRepoWarning(context: Context, onRepositoryAdded: () -> Unit) {
            context.customAlertDialog()
                .setTitle(R.string.warning)
                .setMessage(R.string.add_repository_warning)
                .setPosButton(R.string.ok) {
                    onRepositoryAdded.invoke()
                }
                .setNegButton(R.string.cancel) { }
                .show()
        }

        fun addRepo(input: String, mediaType: MediaType) {
            val validLink = if (input.contains("github.com") && input.contains("blob")) {
                input.replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/")
            } else input

            when (mediaType) {
                MediaType.ANIME -> {
                    val anime =
                        PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
                            .plus(validLink)
                    PrefManager.setVal(PrefName.AnimeExtensionRepos, anime)
                    CoroutineScope(Dispatchers.IO).launch {
                        Injekt.get<AnimeExtensionManager>().findAvailableExtensions()
                    }
                }
                else -> {}
            }
        }

        fun removeRepo(input: String, mediaType: MediaType) {
            when (mediaType) {
                MediaType.ANIME -> {
                    val anime =
                        PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
                            .minus(input)
                    PrefManager.setVal(PrefName.AnimeExtensionRepos, anime)
                    CoroutineScope(Dispatchers.IO).launch {
                        Injekt.get<AnimeExtensionManager>().findAvailableExtensions()
                    }
                }
                else -> {}
            }
        }

        fun newInstance(
            mediaType: MediaType,
            repositories: List<String>,
            onRepositoryAdded: (String, MediaType) -> Unit,
            onRepositoryRemoved: (String, MediaType) -> Unit
        ): AddRepositoryBottomSheet {
            return AddRepositoryBottomSheet().apply {
                this.mediaType = mediaType
                this.repositories.addAll(repositories)
                this.onRepositoryAdded = onRepositoryAdded
                this.onRepositoryRemoved = onRepositoryRemoved
            }
        }
    }
}
