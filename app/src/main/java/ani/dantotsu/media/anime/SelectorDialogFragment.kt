package ani.dantotsu.media.anime

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.ui.test.performClick
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
//import androidx.glance.visibility
//import androidx.glance.visibility
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.crashlytics.CrashlyticsInterface
import ani.dantotsu.copyToClipboard
import ani.dantotsu.currActivity
import ani.dantotsu.currContext
import ani.dantotsu.databinding.BottomSheetSelectorBinding
import ani.dantotsu.databinding.ItemStreamBinding
import ani.dantotsu.databinding.ItemUrlBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.hideSystemBars
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaDetailsViewModel
import ani.dantotsu.media.MediaType
import ani.dantotsu.navBarHeight
import ani.dantotsu.parsers.Subtitle
import ani.dantotsu.parsers.Video
import ani.dantotsu.parsers.VideoExtractor
import ani.dantotsu.parsers.VideoType
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.settings.SettingsAddonActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.toast
import ani.dantotsu.tryWith
import ani.dantotsu.util.Logger
import ani.dantotsu.util.customAlertDialog
import ani.dantotsu.util.FocusEffectUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DecimalFormat


class SelectorDialogFragment : DialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var scope: CoroutineScope = lifecycleScope
    private var media: Media? = null
    private var episode: Episode? = null
    private var prevEpisode: String? = null
    private var makeDefault = false
    private var selected: String? = null
    private var launch: Boolean? = null
    private var isDownloadMenu: Boolean? = null
    private var episodes: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selected = it.getString("server")
            launch = it.getBoolean("launch", true)
            prevEpisode = it.getString("prev")
            isDownloadMenu = it.getBoolean("isDownload")
            episodes = it.getStringArrayList("episodes")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            val widthPx = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            w.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
            w.setDimAmount(0.5f)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor =
                requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        }
        animateEntry()
    }

    private fun animateEntry() {
        val density = resources.displayMetrics.density
        binding.root.apply {
            pivotY = 0f
            pivotX = width / 2f
            rotationX = 10f
            translationY = 40f * density
            scaleY = 0.96f
            alpha = 0.8f
        }
        binding.root.post {
            val lift = ObjectAnimator.ofFloat(binding.root, View.TRANSLATION_Y, 0f).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
            }
            val tilt = ObjectAnimator.ofFloat(binding.root, View.ROTATION_X, 0f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
            }
            val scale = ObjectAnimator.ofFloat(binding.root, View.SCALE_Y, 1f).apply {
                duration = 280
                interpolator = OvershootInterpolator(1.5f)
            }
            val fade = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f).apply {
                duration = 200
            }
            AnimatorSet().apply {
                playTogether(lift, tilt, scale, fade)
                start()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val density = resources.displayMetrics.density
        binding.root.apply {
            pivotY = 0f
            pivotX = width / 2f
            rotationX = 10f
            translationY = 40f * density
            scaleY = 0.96f
            alpha = 0.8f
        }
        binding.root.post {
            val lift = ObjectAnimator.ofFloat(binding.root, View.TRANSLATION_Y, 0f).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
            }
            val tilt = ObjectAnimator.ofFloat(binding.root, View.ROTATION_X, 0f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
            }
            val scale = ObjectAnimator.ofFloat(binding.root, View.SCALE_Y, 1f).apply {
                duration = 280
                interpolator = OvershootInterpolator(1.5f)
            }
            val fade = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f).apply {
                duration = 200
            }
            AnimatorSet().apply {
                playTogether(lift, tilt, scale, fade)
                start()
            }
        }
    }

    interface EpisodeDownloadListener {
        fun onFinishingUserSelection(selectedServerName: String,
                                     selectedSubtitles: MutableList<String>,
                                     selectedAudioTracks: MutableList<String>)
    }
    class EpisodeDownloadHandler(private val _onFinishingUserSelection: (String, MutableList<String>, MutableList<String>) -> Unit)
        : EpisodeDownloadListener{
        override fun onFinishingUserSelection(selectedServerName: String,
                                              selectedSubtitles: MutableList<String>,
                                              selectedAudioTracks: MutableList<String>) {
            _onFinishingUserSelection(selectedServerName, selectedSubtitles, selectedAudioTracks)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        model.getMedia().observe(viewLifecycleOwner) { m ->
            media = m
            if (media != null && !loaded) {
                loaded = true

                fun fail(resId: Int){
                    snackString(getString(resId))
                    tryWith {
                        dismissAllowingStateLoss()
                    }
                }
                fun initializeVideoServerSelector(ep: Episode, onEpisodeDownloadHandler: EpisodeDownloadHandler? = null) {
                    binding.selectorRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = navBarHeight
                    }
                    binding.selectorRecyclerView.adapter = null
                    binding.selectorProgressBar.visibility = View.VISIBLE
                    makeDefault = PrefManager.getVal(PrefName.MakeDefault)
                    binding.selectorMakeDefault.isChecked = makeDefault
                    binding.selectorMakeDefault.setOnClickListener {
                        makeDefault = binding.selectorMakeDefault.isChecked
                        PrefManager.setVal(PrefName.MakeDefault, makeDefault)
                    }
                    binding.selectorRecyclerView.layoutManager =
                        LinearLayoutManager(
                            requireActivity(),
                            LinearLayoutManager.VERTICAL,
                            false
                        )
                    val adapter = ExtractorAdapter(onEpisodeDownloadHandler)
                    binding.selectorRecyclerView.adapter = adapter
                    if (!ep.allStreams) {
                        scope.launch(Dispatchers.IO) {
                            model.loadEpisodeVideos(ep, media!!.selected!!.sourceIndex)
                                withContext(Dispatchers.Main) {
                                    if (_binding == null || !isAdded) return@withContext
                                    adapter.addAll(ep.extractors)
                                    binding.selectorProgressBar.visibility = View.GONE
                                    if (adapter.itemCount == 0) {
                                        fail(R.string.stream_selection_empty)
                                    }
                                    if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                                        adapter.performClick(0)
                                    }
                                    binding.selectorMakeDefault.post { binding.selectorMakeDefault.requestFocus() }
                                }
                        }
                    } else {
                        media!!.anime?.episodes?.set(media!!.anime?.selectedEpisode!!, ep)
                        adapter.addAll(ep.extractors)
                        if (ep.extractors?.size == 0) {
                            fail(R.string.stream_selection_empty)
                        }
                        if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                            adapter.performClick(0)
                        }
                        binding.selectorProgressBar.visibility = View.GONE
                        binding.selectorMakeDefault.post { binding.selectorMakeDefault.requestFocus() }
                    }
                }
                suspend fun loadEpisodeSingleServer(episodeName: String, selectedServerName: String): Boolean{
                    media?.anime?.selectedEpisode = episodeName
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)!!
                    episode = ep

                    var success = false
                    scope.launch(Dispatchers.IO) {
                        success = model.loadEpisodeSingleVideo(
                            ep,
                            media!!.selected!!,
                            selectedServerName = selectedServerName
                        )
                    }.join()
                                        return success
                }
                fun startEpisodeDownload(episodeName: String, selectedServerName: String,
                                         selectedSubtitles: MutableList<String>,
                                         selectedAudioTracks: MutableList<String>){
                    fun downloadUsingSingleServer(extractor: VideoExtractor): Boolean {
                        val episode =
                            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedExtractor =
                            extractor.server.name
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedVideo =
                            episode.selectedVideo
                        {
                                if (extractor.videos.size > episode.selectedVideo) extractor.videos[episode.selectedVideo] else null
                            val activity = currActivity() ?: requireActivity()

                            // download removed
                        }
                        return true
                    }

                    media?.anime?.selectedEpisode = episodeName
                    val ep = media?.anime?.episodes?.get(episodeName)!!
                    episode = ep

                    
                    if (ep.extractors?.find { it?.server?.name == selectedServerName } == null)
                        fail(R.string.auto_select_server_error)
                    else {
                        media!!.anime?.episodes?.set(media!!.anime?.selectedEpisode!!, ep)
                        val selectedExtractor =
                            ep.extractors?.find { it?.server?.name == selectedServerName }
                        if (selectedExtractor == null || !downloadUsingSingleServer(selectedExtractor))
                            fail(R.string.auto_select_server_error)
                    }
                }

                                if(episodes.isNullOrEmpty()){
                    fail(R.string.empty_episodes_list)
                }
                if (isDownloadMenu == false) {
                    media?.anime?.selectedEpisode = episodes?.get(0)
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)
                    episode = ep
                    if (ep != null) {
                        if (selected != null && media?.format != "LOCAL") {
                            binding.selectorListContainer.visibility = View.GONE
                            binding.selectorAutoListContainer.visibility = View.VISIBLE
                            binding.selectorAutoText.text = selected
                            binding.selectorCancel.setOnClickListener {
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                tryWith {
                                    dismissAllowingStateLoss()
                                }
                            }

                            fun failToList() {
                                snackString(getString(R.string.auto_select_server_error))
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                binding.selectorAutoListContainer.visibility = View.GONE
                                binding.selectorListContainer.visibility = View.VISIBLE
                                initializeVideoServerSelector(ep)
                            }

                            fun load() {
                                val size =
                                    if (model.watchSources!!.isDownloadedSource(media!!.selected!!.sourceIndex)) {
                                        ep.extractors?.firstOrNull()?.videos?.size
                                    } else {
                                        ep.extractors?.find { it?.server?.name == selected }?.videos?.size
                                    }

                                if (size != null && size >= media!!.selected!!.video) {
                                    media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedExtractor =
                                        selected
                                    media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedVideo =
                                        media!!.selected!!.video
                                    startExoplayer(media!!)
                                } else failToList()
                            }

                            if (ep.extractors?.filter { it?.server?.name == selected }.isNullOrEmpty()) {
                                scope.launch{
                                    val success = withContext(Dispatchers.IO){
                                        loadEpisodeSingleServer(ep.number, selected!!)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (_binding == null || !isAdded) return@withContext
                                        if (!success) {
                                            failToList()
                                        } else {
                                            load()
                                        }
                                    }
                                }
                            } else load()
                        }
                        else
                            initializeVideoServerSelector(ep)
                    }
                }
                else {
                    binding.selectorMakeDefault.visibility = View.GONE
                    media?.anime?.selectedEpisode = episodes?.get(0)
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)
                    episode = ep

                    if (ep != null) {
                        val downloadHandler =
                            EpisodeDownloadHandler(_onFinishingUserSelection = { selectedServerName,
                                                                                 selectedSubtitles,
                                                                                 selectedAudioTracks ->
                                binding.selectorListContainer.visibility = View.GONE
                                binding.selectorAutoListContainer.visibility = View.VISIBLE
                                binding.selectorTitle.text = "Starting Download"
                                binding.selectorAutoText.text =
                                    "Starting download using server:\n$selectedServerName"
                                binding.selectorCancel.visibility = View.GONE

                                scope.launch(Dispatchers.IO) {
                                    val serverSelectionScope = CoroutineScope(Dispatchers.IO)
                                    val serverSelectionTasks = mutableListOf<Deferred<Unit>>()
                                    for (episodeName in episodes!!.drop(1)) {
                                        serverSelectionTasks.add(serverSelectionScope.async {
                                            if(!loadEpisodeSingleServer(episodeName, selectedServerName)){
                                                                fail(R.string.auto_select_server_error)
                                            }
                                        })
                                    }
                                    serverSelectionTasks.awaitAll()

                                    for(episodeName in episodes!!){
                                        startEpisodeDownload(episodeName, selectedServerName, selectedSubtitles, selectedAudioTracks)
                                    }
                                    tryWith{
                                        dismissAllowingStateLoss()
                                    }
                                }
                            })
                        initializeVideoServerSelector(ep, downloadHandler)
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private val externalPlayerResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Logger.log(result.data.toString())
    }

    private fun exportMagnetIntent(episode: Episode, video: Video): Intent {
        val amnis = "com.amnis"
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(amnis, "$amnis.gui.player.PlayerActivity")
            data = Uri.parse(video.file.url)
            putExtra("title", "${media?.name} - ${episode.title}")
            putExtra("position", 0)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("secure_uri", true)
            val headersArray = arrayOf<String>()
            video.file.headers.forEach {
                headersArray.plus(arrayOf(it.key, it.value))
            }
            putExtra("headers", headersArray)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("UnsafeOptInUsageError")
    fun startExoplayer(media: Media) {
        if (!isAdded || _binding == null) return
        prevEpisode = null

        episode?.let { ep ->
            val video = ep.extractors?.find {
                it?.server?.name == ep.selectedExtractor
            }?.videos?.getOrNull(ep.selectedVideo)
            video?.file?.url?.let { url ->
                if (url.startsWith("magnet:") || url.endsWith(".torrent")) {
                    toast(R.string.torrent_addon_not_available)
                    return
                }
                        try {
                            externalPlayerResult.launch(exportMagnetIntent(ep, video))
                        } catch (e: ActivityNotFoundException) {
                            val amnis = "com.amnis"
                            try {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=$amnis")
                                    )
                                )
                                dismissAllowingStateLoss()
                            } catch (e: ActivityNotFoundException) {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=$amnis")
                                    )
                                )
                            }
                        }
                    }
                    return
                }
            }
        }

        dismissAllowingStateLoss()
        if (launch!!) {
            stopAddingToList()
            val intent = Intent(activity, ExoplayerView::class.java)
            ExoplayerView.media = media
            ExoplayerView.initialized = true
            startActivity(intent)
        } else {
            model.setEpisode(
                media.anime!!.episodes!![media.anime.selectedEpisode!!]!!,
                "startExo no launch"
            )
        }
    

    private fun stopAddingToList() {
        episode?.extractorCallback = null
        episode?.also {
            it.extractors = it.extractors?.toMutableList()
        }
    }

    private inner class ExtractorAdapter(private val onEpisodeDownloadHandler: EpisodeDownloadHandler? = null) :
        RecyclerView.Adapter<ExtractorAdapter.StreamViewHolder>() {
        val links = mutableListOf<VideoExtractor>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemStreamBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val extractor = links.getOrNull(position) ?: return
            holder.binding.streamName.text = ""//extractor.server.name
            holder.binding.streamName.visibility = View.GONE

            holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            holder.binding.streamRecyclerView.adapter = VideoAdapter(extractor, onEpisodeDownloadHandler)
        }

        override fun getItemCount(): Int = links.size

        fun add(videoExtractor: VideoExtractor) {
            if (videoExtractor.videos.isNotEmpty()) {
                links.add(videoExtractor)
                notifyItemInserted(links.size - 1)
            }
        }

        fun addAll(extractors: List<VideoExtractor>?) {
            val valid = extractors.orEmpty().toList().filterNotNull()
            if (valid.isEmpty()) return
            links.addAll(valid)
            notifyItemRangeInserted(0, links.size)
        }

        fun performClick(position: Int) {
            try {
                val extractor = links[position] ?: return
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                    extractor.server.name
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo = 0
                startExoplayer(media!!)
            } catch (e: Exception) {
                Injekt.get<CrashlyticsInterface>().logException(e)
            }
        }

        private inner class StreamViewHolder(val binding: ItemStreamBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.isFocusable = false
                itemView.setOnClickListener {
                    performClick(bindingAdapterPosition)
                }
            }
        }
    }

    private inner class VideoAdapter(private val extractor: VideoExtractor,private val onEpisodeDownloadHandler: EpisodeDownloadHandler?) :
        RecyclerView.Adapter<VideoAdapter.UrlViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
            return UrlViewHolder(
                ItemUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
            val binding = holder.binding
            val video = extractor.videos[position]
            if (isDownloadMenu == true) {
                binding.urlDownload.visibility = View.VISIBLE
            } else {
                binding.urlDownload.visibility = View.GONE
            }
            val subtitles = extractor.subtitles
            if (subtitles.isNotEmpty()) {
                binding.urlSub.visibility = View.VISIBLE
            } else {
                binding.urlSub.visibility = View.GONE
            }
            binding.urlSub.setOnClickListener {
                if (subtitles.isNotEmpty()) {
                    val subtitleNames = subtitles.map { it.language }
                    var subtitleToDownload: Subtitle? = null
                    requireActivity().customAlertDialog().apply {
                        setTitle(R.string.download_subtitle)
                        singleChoiceItems(subtitleNames.toTypedArray(),  dismissOnSelect = false) { which ->
                            subtitleToDownload = subtitles[which]
                        }
                        setPosButton(R.string.download) {
                            scope.launch(Dispatchers.IO) {
                                if (subtitleToDownload != null) {
                                    null,
                                        subtitleToDownload.file.url,
null,
                                            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.number,
                                            MediaType.ANIME
                                        )
                                    )
                                }
                            }
                        }
                        setNegButton(R.string.cancel) {}
                    }.show()
                } else {
                    snackString(R.string.no_subtitles_available)
                }
            }
            binding.urlDownload.setSafeOnClickListener {
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedExtractor =
                    extractor.server.name
                media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!.selectedVideo =
                    position
                if ((PrefManager.getVal(PrefName.DownloadManager) as Int) != 0) {
                    download(
                        requireActivity(),
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!,
                        media!!.userPreferredName
                    )
                }
                else {
                    val episode =
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]!!
                    val selectedVideo =
                        if (extractor.videos.size > episode.selectedVideo) extractor.videos[episode.selectedVideo] else null
                    // download handling removed
                }
            }
            if (video.format == VideoType.CONTAINER) {
                binding.urlSize.isVisible = video.size != null
                // if video size is null or 0, show "Unknown Size" else show the size in MB
                val sizeText = getString(
                    R.string.mb_size, "${if (video.extraNote != null) " : " else ""}${
                        if (video.size == 0.0) getString(R.string.size_unknown) else DecimalFormat("#.##").format(
                            video.size ?: 0
                        )
                    }"
                )
                binding.urlSize.text = sizeText
            }
            binding.urlNote.visibility = View.VISIBLE
            binding.urlNote.text = video.format.name
            binding.urlQuality.text = extractor.server.name
        }

        override fun getItemCount(): Int = extractor.videos.size

        private inner class UrlViewHolder(val binding: ItemUrlBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.isFocusable = true
                FocusEffectUtil.applyFocusListener(itemView)
                itemView.setSafeOnClickListener {
                    if (isDownloadMenu == true) {
                        binding.urlDownload.performClick()
                        return@setSafeOnClickListener
                    }
                    tryWith(true) {
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                            extractor.server.name
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo =
                            bindingAdapterPosition
                        if (makeDefault) {
                            media!!.selected!!.server = extractor.server.name
                            media!!.selected!!.video = bindingAdapterPosition
                            model.saveSelected(media!!.id, media!!.selected!!)
                        }
                                                startExoplayer(media!!)
                    }
                }
                itemView.setOnLongClickListener {
                    val video = extractor.videos[bindingAdapterPosition]
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(video.file.url), "video/*")
                    }
                    copyToClipboard(video.file.url, true)
                    dismissAllowingStateLoss()
                    startActivity(Intent.createChooser(intent, "Open Video in :"))
                    true
                }
            }
        }
    }

    companion object {
        fun newInstance(
            server: String? = null,
            la: Boolean = true,
            prev: String? = null,
            isDownload: Boolean,
            episodes: ArrayList<String>
        ): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("server", server)
                    putBoolean("launch", la)
                    putString("prev", prev)
                    putBoolean("isDownload", isDownload)
                    putStringArrayList("episodes", episodes)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {}

    override fun onDismiss(dialog: DialogInterface) {
        if (launch == false) {
            activity?.hideSystemBars()
            model.epChanged.postValue(true)
            if (prevEpisode != null) {
                media?.anime?.selectedEpisode = prevEpisode
                model.setEpisode(media?.anime?.episodes?.get(prevEpisode) ?: return, "prevEp")
            }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
