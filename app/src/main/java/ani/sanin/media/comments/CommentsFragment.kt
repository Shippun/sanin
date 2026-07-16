package ani.sanin.media.comments

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.animation.doOnEnd
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Button
import ani.sanin.R
import ani.sanin.connections.mal.MAL
import ani.sanin.media.MediaListDialogFragment
import ani.sanin.buildMarkwon
import ani.sanin.connections.LogoApi
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.comments.Comment
import ani.sanin.connections.comments.CommentResponse
import ani.sanin.connections.comments.CommentsAPI
import ani.sanin.connections.trakt.TraktAPI
import ani.sanin.connections.trakt.TraktAuth
import ani.sanin.connections.trakt.TraktComment
import ani.sanin.connections.trakt.TraktSearchResult
import ani.sanin.databinding.DialogEdittextBinding
import ani.sanin.databinding.FragmentCommentsBinding
import ani.sanin.loadImage
import ani.sanin.media.MediaNameAdapter
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.others.IdMappers
import ani.sanin.setBaseline
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.Logger
import ani.sanin.util.customAlertDialog
import com.xwray.groupie.GroupieAdapter
import com.xwray.groupie.Section
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@SuppressLint("ClickableViewAccessibility")
class CommentsFragment : Fragment() {
    lateinit var binding: FragmentCommentsBinding
    lateinit var activity: MediaDetailsActivity
    private var interactionState = InteractionState.NONE
    private var commentWithInteraction: CommentItem? = null
    private val section = Section()
    private val adapter = GroupieAdapter()
    private var tag: Int? = null
    private var filterTag: Int? = null
    private var mediaId: Int = -1
    var mediaName: String = ""
    private var backgroundColor: Int = 0
    var pagesLoaded = 1
    var totalPages = 1
    private var userProgress: Int? = null
    private var totalEpisodesOrChapters: Int? = null
    private var isAnime: Boolean = true
    private var commentsLoaded = false
    private var isAutoFilterOn = false
    private var isSpoilerMode = false

    private var currentSource = CommentSource.DANOTSU
    private var traktResult: TraktSearchResult? = null

    enum class CommentSource { DANOTSU, TRAKT }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCommentsBinding.inflate(inflater, container, false)
        binding.commentsLayout.isNestedScrollingEnabled = true
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MediaDetailsActivity

        activity.binding.commentMessageContainer?.let {
            binding.commentsLayout.setBaseline(it, includeSystemNavBar = true)
        }
        val mediaId = arguments?.getInt("mediaId") ?: -1
        mediaName = arguments?.getString("mediaName") ?: "unknown"
        if (mediaId == -1) {
            snackString("Invalid Media ID")
            return
        }
        this.mediaId = mediaId
        backgroundColor = (binding.root.background as? ColorDrawable)?.color ?: 0

        val markwon = buildMarkwon(activity, fragment = this@CommentsFragment)

        activity.binding.commentUserAvatar.loadImage(Anilist.avatar)
        val markwonEditor = MarkwonEditor.create(markwon)
        activity.binding.commentInput.addTextChangedListener(
            MarkwonEditorTextWatcher.withProcess(markwonEditor)
        )

        val isOfflineOrLocal = !ani.sanin.isOnline(activity)

        binding.commentsRefresh.setOnRefreshListener {
            val refreshOffline = !ani.sanin.isOnline(activity)
            if (refreshOffline) {
                binding.commentsRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            binding.commentsOfflineText.visibility = View.GONE
            binding.commentsListContainer.visibility = View.VISIBLE
            updateUiForSource()
            activity.binding.commentMessageContainer.visibility =
                if (CommentsAPI.authToken != null && currentSource == CommentSource.DANOTSU) View.VISIBLE else View.GONE

            lifecycleScope.launch {
                loadAndDisplayComments()
                binding.commentsRefresh.isRefreshing = false
            }
            activity.binding.commentReplyToContainer.visibility = View.GONE
        }

        binding.commentsList.adapter = adapter
        binding.commentsList.layoutManager = LinearLayoutManager(activity)
        adapter.add(section)

        val model: MediaDetailsViewModel by activityViewModels()
        model.getMedia().observe(viewLifecycleOwner) { newMedia ->
            if (newMedia != null && newMedia.id != 0) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val logoUrl = LogoApi.getLogoUrl(newMedia.id)
                    if (!logoUrl.isNullOrBlank()) {
                        binding.commentsLogo.visibility = View.VISIBLE
                        binding.commentsLogo.loadImage(logoUrl)
                    } else {
                        binding.commentsTitle.visibility = View.VISIBLE
                        binding.commentsTitle.text = newMedia.userPreferredName ?: newMedia.name
                    }
                }

                // Add to List button
                val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
                fun updateAddToList() {
                    val statuses: Array<String> = resources.getStringArray(R.array.status)
                    val statusStrings = resources.getStringArray(R.array.status_anime)
                    val userStatus =
                        if (newMedia.userStatus != null) statusStrings[statuses.indexOf(newMedia.userStatus).coerceAtLeast(0)] else statusStrings[0]
                    if (newMedia.userStatus != null) {
                        binding.commentsAddToList.visibility = View.VISIBLE
                        binding.commentsAddToList.text = userStatus
                    } else {
                        binding.commentsAddToList.setText(R.string.add_list)
                    }
                }
                updateAddToList()
                val fm = requireActivity().supportFragmentManager
                binding.commentsAddToList.setOnClickListener {
                    if (rescueMode) {
                        if (MAL.token != null) {
                            if (fm.findFragmentByTag("dialog") == null)
                                MediaListDialogFragment().show(fm, "dialog")
                        } else snackString("Please login to MAL")
                    } else if (Anilist.userid != null) {
                        if (fm.findFragmentByTag("dialog") == null)
                            MediaListDialogFragment().show(fm, "dialog")
                    } else snackString(getString(R.string.please_login_anilist))
                }
                FocusEffectUtil.applyFocusListener(binding.commentsAddToList)

                isAnime = newMedia.anime != null
                userProgress = newMedia.userProgress
                totalEpisodesOrChapters = newMedia.anime?.totalEpisodes
                updateCurrentProgressButton()

                if (!commentsLoaded || newMedia.id != this.mediaId) {
                    this.mediaId = newMedia.id
                    commentsLoaded = true
                    traktResult = null
                    currentSource = CommentSource.DANOTSU

                    lifecycleScope.launch {
                        traktResult = lookupTraktIds()
                        updateSourceBarVisibility()
                    }

                    if (isOfflineOrLocal) {
                        binding.commentsOfflineText.visibility = View.VISIBLE
                        binding.commentsListContainer.visibility = View.GONE
                        binding.commentSourceBar.visibility = View.GONE
                        binding.openRules.visibility = View.GONE
                        binding.commentFilter.visibility = View.GONE
                        binding.commentSort.visibility = View.GONE
                        binding.commentCurrentProgress.visibility = View.GONE
                        binding.commentsProgressBar.visibility = View.GONE
                        activity.binding.commentMessageContainer.visibility = View.GONE
                    } else if (CommentsAPI.authToken != null) {
                        lifecycleScope.launch {
                            val commentId = arguments?.getInt("commentId")
                            if (commentId != null && commentId > 0) {
                                loadSingleComment(commentId)
                            } else {
                                loadAndDisplayComments()
                            }
                        }
                    } else {
                        activity.binding.commentMessageContainer.visibility = View.GONE
                    }
                }
            }
        }

        binding.commentSort.setOnClickListener { sortView ->
            fun sortComments(sortOrder: String) {
                val groups = section.groups
                when (sortOrder) {
                    "newest" -> groups.sortByDescending { CommentItem.timestampToMillis((it as CommentItem).comment.timestamp) }
                    "oldest" -> groups.sortBy { CommentItem.timestampToMillis((it as CommentItem).comment.timestamp) }
                    "highest_rated" -> groups.sortByDescending { (it as CommentItem).comment.upvotes - it.comment.downvotes }
                    "lowest_rated" -> groups.sortBy { (it as CommentItem).comment.upvotes - it.comment.downvotes }
                }
                section.update(groups)
            }

            val popup = PopupMenu(activity, sortView)
            popup.setOnMenuItemClickListener { item ->
                val sortOrder = when (item.itemId) {
                    R.id.comment_sort_newest -> "newest"
                    R.id.comment_sort_oldest -> "oldest"
                    R.id.comment_sort_highest_rated -> "highest_rated"
                    R.id.comment_sort_lowest_rated -> "lowest_rated"
                    else -> return@setOnMenuItemClickListener false
                }
                PrefManager.setVal(PrefName.CommentSortOrder, sortOrder)
                if (totalPages > pagesLoaded) {
                    lifecycleScope.launch {
                        loadAndDisplayComments()
                        activity.binding.commentReplyToContainer.visibility = View.GONE
                    }
                } else {
                    sortComments(sortOrder)
                }
                binding.commentsList.scrollToPosition(0)
                true
            }
            popup.inflate(R.menu.comments_sort_menu)
            popup.show()
        }

        binding.openRules.setOnClickListener {
            activity.customAlertDialog().apply {
                setTitle("Commenting Rules")
                    .setMessage(
                        "🚨 BREAK ANY RULE = YOU'RE GONE\n\n" +
                                "1. NO RACISM, DISCRIMINATION, OR HATE SPEECH\n" +
                                "2. NO SPAMMING OR SELF-PROMOTION\n" +
                                "3. ABSOLUTELY NO NSFW CONTENT\n" +
                                "4. ENGLISH ONLY – NO EXCEPTIONS\n" +
                                "5. NO IMPERSONATION, HARASSMENT, OR ABUSE\n" +
                                "6. NO ILLEGAL CONTENT OR EXTREME DISRESPECT TOWARDS ANY FANDOM\n" +
                                "7. DO NOT REQUEST OR SHARE REPOSITORIES/EXTENSIONS\n" +
                                "8. SPOILERS ALLOWED ONLY WITH SPOILER TAGS AND A WARNING\n" +
                                "9. NO SEXUALIZING OR INAPPROPRIATE COMMENTS ABOUT MINOR CHARACTERS\n" +
                                "10. IF IT'S WRONG, DON'T POST IT!\n\n"
                    )
                setNegButton("I Understand") {}
                show()
            }
        }

        binding.commentFilter.setOnClickListener {
            activity.customAlertDialog().apply {
                val customView = DialogEdittextBinding.inflate(layoutInflater)
                setTitle("Enter a chapter/episode number tag")
                setCustomView(customView.root)
                setPosButton("OK") {
                    val text = customView.dialogEditText.text.toString()
                    filterTag = text.toIntOrNull()
                    updateCurrentProgressButton()
                    lifecycleScope.launch {
                        loadAndDisplayComments()
                    }
                }
                setNeutralButton("Clear") {
                    filterTag = null
                    updateCurrentProgressButton()
                    lifecycleScope.launch {
                        loadAndDisplayComments()
                    }
                }
                setNegButton("Cancel") {}
                show()
            }
        }

        binding.commentCurrentProgress.setOnClickListener {
            val progress = userProgress ?: return@setOnClickListener
            if (progress <= 0) return@setOnClickListener
            if (filterTag != null && filterTag != progress) {
                filterTag = null
                isAutoFilterOn = false
            } else {
                isAutoFilterOn = !isAutoFilterOn
            }
            updateCurrentProgressButton()
            lifecycleScope.launch {
                loadAndDisplayComments()
            }
        }

        binding.commentCurrentProgress.setOnLongClickListener {
            val progress = userProgress ?: return@setOnLongClickListener false
            if (progress <= 0) return@setOnLongClickListener false
            val total = totalEpisodesOrChapters ?: progress
            val maxEp = maxOf(total, progress)
            val label = "Ep"

            val items = Array(maxEp) { i -> "$label ${i + 1}" }
            val currentSelection = if (filterTag != null) filterTag!! - 1 else progress - 1
            activity.customAlertDialog().apply {
                setTitle("Filter by Episode")
                singleChoiceItems(items, currentSelection) { selected ->
                    filterTag = selected + 1
                    isAutoFilterOn = true
                    updateCurrentProgressButton()
                    lifecycleScope.launch {
                        loadAndDisplayComments()
                    }
                }
                show()
            }
            true
        }

        binding.commentSourceSanin.setOnClickListener {
            if (currentSource != CommentSource.DANOTSU) {
                currentSource = CommentSource.DANOTSU
                highlightSource()
                lifecycleScope.launch { loadAndDisplayComments() }
            }
        }
        binding.commentSourceSanin.setOnFocusChangeListener { v, hasFocus ->
            v.elevation = if (hasFocus) 8f else 0f
        }
        binding.commentSourceTrakt.setOnClickListener {
            if (currentSource != CommentSource.TRAKT) {
                currentSource = CommentSource.TRAKT
                highlightSource()
                lifecycleScope.launch { loadAndDisplayComments() }
            }
        }
        binding.commentSourceTrakt.setOnFocusChangeListener { v, hasFocus ->
            v.elevation = if (hasFocus) 8f else 0f
        }
        FocusEffectUtil.applyFocusListener(binding.openRules, binding.commentFilter, binding.commentSort)

        var isFetching = false
        binding.commentsList.setOnTouchListener(
            object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    if (event?.action == MotionEvent.ACTION_UP) {
                        if (!binding.commentsList.canScrollVertically(1) && !isFetching &&
                            (binding.commentsList.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() == (binding.commentsList.adapter!!.itemCount - 1)
                        ) {
                            if (pagesLoaded < totalPages && totalPages > 1) {
                                binding.commentBottomRefresh.visibility = View.VISIBLE
                                loadMoreComments()
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    withContext(Dispatchers.Main) {
                                        binding.commentBottomRefresh.visibility = View.GONE
                                    }
                                }
                            } else {
                                Logger.log("No more comments")
                            }
                        }
                    }
                    return false
                }

                private fun loadMoreComments() {
                    isFetching = true
                    lifecycleScope.launch {
                        if (currentSource == CommentSource.DANOTSU) {
                            val comments = withContext(Dispatchers.IO) {
                                CommentsAPI.getCommentsForId(
                                    mediaId,
                                    pagesLoaded + 1,
                                    getEffectiveFilter(),
                                    PrefManager.getVal(PrefName.CommentSortOrder, "newest")
                                )
                            }
                            comments?.comments?.forEach { comment ->
                                withContext(Dispatchers.Main) {
                                    section.add(
                                        CommentItem(
                                            comment,
                                            buildMarkwon(activity, fragment = this@CommentsFragment),
                                            section,
                                            this@CommentsFragment,
                                            backgroundColor,
                                            0
                                        )
                                    )
                                }
                            }
                            totalPages = comments?.totalPages ?: 1
                        } else {
                            val type = traktResult?.mediaType ?: return@launch
                            val id = traktResult?.traktId ?: return@launch
                            val traktComments = TraktAPI.getComments(type, id, pagesLoaded + 1)
                            traktComments.forEach { tc ->
                                val comment = traktToComment(tc)
                                withContext(Dispatchers.Main) {
                                    section.add(
                                        CommentItem(
                                            comment,
                                            buildMarkwon(activity, fragment = this@CommentsFragment),
                                            section,
                                            this@CommentsFragment,
                                            backgroundColor,
                                            0
                                        )
                                    )
                                }
                            }
                            totalPages = if (traktComments.size < 25) pagesLoaded else pagesLoaded + 1
                        }
                        pagesLoaded++
                        isFetching = false
                    }
                }
            })

        activity.binding.commentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if ((activity.binding.commentInput.text.length) > 300) {
                    activity.binding.commentInput.text.delete(
                        300,
                        activity.binding.commentInput.text.length
                    )
                    snackString("Comment cannot be longer than 300 characters")
                }
            }
        })

        activity.binding.commentInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val targetWidth = activity.binding.commentInputLayout.width -
                        activity.binding.commentLabel.width -
                        activity.binding.commentSend.width -
                        activity.binding.commentUserAvatar.width - 12 + 16
                val anim = ValueAnimator.ofInt(activity.binding.commentInput.width, targetWidth)
                anim.addUpdateListener { valueAnimator ->
                    val layoutParams = activity.binding.commentInput.layoutParams
                    layoutParams.width = valueAnimator.animatedValue as Int
                    activity.binding.commentInput.layoutParams = layoutParams
                }
                anim.duration = 300

                anim.start()
                anim.doOnEnd {
                    activity.binding.commentLabel.visibility = View.VISIBLE
                    activity.binding.commentSend.visibility = View.VISIBLE
                    activity.binding.commentSpoiler.visibility = View.VISIBLE
                    activity.binding.commentGif.visibility = View.VISIBLE
                    activity.binding.commentLabel.animate().translationX(0f).setDuration(300).start()
                    activity.binding.commentSend.animate().translationX(0f).setDuration(300).start()
                }
            }

            activity.binding.commentLabel.setOnClickListener {
                activity.customAlertDialog().apply {
                    val customView = DialogEdittextBinding.inflate(layoutInflater)
                    setTitle("Enter a chapter/episode number tag")
                    setCustomView(customView.root)
                    setPosButton("OK") {
                        val text = customView.dialogEditText.text.toString()
                        tag = text.toIntOrNull()
                        if (tag == null) {
                            activity.binding.commentLabel.background = ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_label_off_24,
                                null
                            )
                        } else {
                            activity.binding.commentLabel.background = ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_label_24,
                                null
                            )
                        }
                    }
                    setNeutralButton("Clear") {
                        tag = null
                        activity.binding.commentLabel.background = ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_label_off_24,
                            null
                        )
                    }
                    setNegButton("Cancel") {
                        tag = null
                        activity.binding.commentLabel.background = ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_label_off_24,
                            null
                        )
                    }
                    show()
                }
            }
        }

        activity.binding.commentSpoiler.setOnClickListener {
            isSpoilerMode = !isSpoilerMode
            activity.binding.commentSpoiler.alpha = if (isSpoilerMode) 1f else 0.5f
            activity.binding.commentSpoiler.setImageResource(
                if (isSpoilerMode) R.drawable.format_spoiler_24
                else R.drawable.ic_round_remove_red_eye_24
            )
        }

        activity.binding.commentGif.setOnClickListener {
            val gifPicker = GifPickerBottomDialog.newInstance()
            gifPicker.setOnGifSelectedListener { gifUrl ->
                val currentText = activity.binding.commentInput.text.toString()
                val gifMarkdown = "![gif]($gifUrl)"
                val newText = if (currentText.isEmpty()) gifMarkdown else "$currentText\n$gifMarkdown"
                activity.binding.commentInput.setText(newText)
                activity.binding.commentInput.setSelection(newText.length)
            }
            gifPicker.show(childFragmentManager, "gifPicker")
        }

        activity.binding.commentSend.setOnClickListener {
            if (CommentsAPI.isBanned) {
                snackString("You are banned from commenting :(")
                return@setOnClickListener
            }

            if (PrefManager.getVal(PrefName.FirstComment)) {
                showCommentRulesDialog()
            } else {
                showTagDialogThenProcess()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        tag = null
        section.groups.forEach {
            if (it is CommentItem && it.containsGif()) {
                it.notifyChanged()
            }
        }
    }

    enum class InteractionState {
        NONE, EDIT, REPLY
    }

    fun onTagClicked(tag: String) {
        val model: MediaDetailsViewModel by activityViewModels()
        val currentMedia = model.getMedia().value ?: return

        if (isAnime) {
            val ep = currentMedia.anime?.episodes?.get(tag)
            if (ep != null) {
                model.onEpisodeClick(currentMedia, tag, childFragmentManager, true)
            } else {
                snackString("Episode $tag not found for this provider")
            }
        }
    }

    private fun chapterMatchesTag(chapterNumber: String, tag: String): Boolean {
        if (chapterNumber == tag) return true
        val chapterValue = MediaNameAdapter.findChapterNumber(chapterNumber)
        val tagValue = MediaNameAdapter.findChapterNumber(tag)
        return chapterValue != null && tagValue != null && chapterValue == tagValue
    }

    private fun getEffectiveFilter(): Int? = when {
        filterTag != null -> filterTag
        isAutoFilterOn && userProgress != null && userProgress!! > 0 -> userProgress
        else -> null
    }

    private suspend fun lookupTraktIds(): TraktSearchResult? {
        val imdbId = IdMappers.getImdbId(mediaId) ?: return null
        return TraktAPI.searchByImdb(imdbId)
    }

    private fun updateSourceBarVisibility() {
        val hasTrakt = traktResult != null && PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1
        binding.commentSourceBar.visibility = if (hasTrakt) View.VISIBLE else View.GONE
        if (hasTrakt) highlightSource()
    }

    private fun highlightSource() {
        val primary = resolveColorAttr(com.google.android.material.R.attr.colorPrimary)
        val onPrimary = resolveColorAttr(com.google.android.material.R.attr.colorOnPrimary)
        val onBg = resolveColorAttr(android.R.attr.textColorPrimary)

        when (currentSource) {
            CommentSource.DANOTSU -> {
                binding.commentSourceSanin.setTextColor(onPrimary)
                binding.commentSourceSanin.setBackgroundColor(primary)
                binding.commentSourceTrakt.setTextColor(onBg)
                binding.commentSourceTrakt.background = null
            }
            CommentSource.TRAKT -> {
                binding.commentSourceTrakt.setTextColor(onPrimary)
                binding.commentSourceTrakt.setBackgroundColor(primary)
                binding.commentSourceSanin.setTextColor(onBg)
                binding.commentSourceSanin.background = null
            }
        }
        updateUiForSource()
    }

    private fun updateUiForSource() {
        val isSanin = currentSource == CommentSource.DANOTSU
        binding.openRules.visibility = if (isSanin) View.VISIBLE else View.GONE
        binding.commentFilter.visibility = if (isSanin) View.VISIBLE else View.GONE
        binding.commentSort.visibility = View.VISIBLE
        binding.commentCurrentProgress.visibility = if (isSanin && (userProgress ?: 0) > 0) View.VISIBLE else View.GONE
        activity.binding.commentMessageContainer.visibility =
            if (isSanin && CommentsAPI.authToken != null) View.VISIBLE
            else if (!isSanin && TraktAuth.isLoggedIn()) View.VISIBLE
            else View.GONE
    }

    private fun updateCurrentProgressButton() {
        val progress = userProgress ?: 0
        if (progress <= 0) {
            binding.commentCurrentProgress.visibility = View.GONE
            return
        }
        val label = "Ep"
        val isManualFilter = filterTag != null && filterTag != progress
        val activeFilter = filterTag ?: progress

        val badge = binding.commentCurrentProgress
        when {
            isManualFilter -> {
                badge.text = "$label $activeFilter  ✕"
                badge.alpha = 1f
                val primaryColor = resolveColorAttr(com.google.android.material.R.attr.colorPrimary)
                badge.setTextColor(
                    resolveColorAttr(com.google.android.material.R.attr.colorOnPrimary)
                )
                badge.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(primaryColor)
                }
            }
            isAutoFilterOn -> {
                badge.text = "$label $progress"
                badge.alpha = 1f
                badge.setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
                badge.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(
                        (1f * resources.displayMetrics.density).toInt(),
                        android.graphics.Color.WHITE
                    )
                }
            }
            else -> {
                badge.text = "$label $progress"
                badge.alpha = 0.33f
                badge.setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
                badge.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 16f * resources.displayMetrics.density
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(
                        (1f * resources.displayMetrics.density).toInt(),
                        android.graphics.Color.WHITE
                    )
                }
            }
        }
        badge.visibility = View.VISIBLE
    }

    private fun resolveColorAttr(attr: Int): Int {
        val typedArray = activity.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()
        return color
    }

    private fun showTagDialogThenProcess() {
        if (interactionState == InteractionState.EDIT) {
            processComment()
            return
        }

        val commentText = activity.binding.commentInput.text.toString()
        if (commentText.isEmpty()) {
            snackString("Comment cannot be empty")
            return
        }

        val label = "episode"
        val total = totalEpisodesOrChapters
        val defaultProgress = userProgress ?: 0

        activity.customAlertDialog().apply {
            val customView = DialogEdittextBinding.inflate(layoutInflater)
            if (defaultProgress > 0) {
                customView.dialogEditText.setText(defaultProgress.toString())
            }
            customView.dialogEditText.hint = if (total != null && total > 0)
                "1–$total"
            else
                "$label number"
            setTitle("Tag $label (optional)")
            setCustomView(customView.root)
            setPosButton("Send") {
                val entered = customView.dialogEditText.text.toString().toIntOrNull()
                if (entered != null && total != null && total > 0 && entered > total) {
                    snackString("Tag cannot exceed total ${label}s ($total)")
                    tag = null
                } else {
                    tag = entered
                }
                activity.binding.commentLabel.background = if (tag != null)
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_label_24, null)
                else
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_label_off_24, null)
                processComment()
            }
            setNeutralButton("No tag") {
                tag = null
                activity.binding.commentLabel.background =
                    ResourcesCompat.getDrawable(resources, R.drawable.ic_label_off_24, null)
                processComment()
            }
            setNegButton(R.string.cancel) {}
            show()
        }
    }

    private suspend fun loadAndDisplayComments() {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        section.clear()
        pagesLoaded = 1
        updateUiForSource()

        when (currentSource) {
            CommentSource.DANOTSU -> loadSaninComments()
            CommentSource.TRAKT -> loadTraktComments()
        }

        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
    }

    private suspend fun loadSaninComments() {
        val effectiveFilter = getEffectiveFilter()
        val comments = withContext(Dispatchers.IO) {
            CommentsAPI.getCommentsForId(
                mediaId,
                page = 1,
                tag = effectiveFilter,
                sort = null
            )
        }
        comments?.comments?.forEach { comment ->
            withContext(Dispatchers.Main) {
                section.add(
                    CommentItem(
                        comment,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        0
                    )
                )
            }
        }
        totalPages = comments?.totalPages ?: 1
    }

    private suspend fun loadTraktComments() {
        val type = traktResult?.mediaType ?: run {
            withContext(Dispatchers.Main) { snackString("Trakt: media not found") }
            totalPages = 1
            return
        }
        val id = traktResult?.traktId ?: run {
            withContext(Dispatchers.Main) { snackString("Trakt: media not found") }
            totalPages = 1
            return
        }
        val sort = when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
            "newest" -> "newest"
            "oldest" -> "oldest"
            else -> "likes"
        }
        val traktComments = withContext(Dispatchers.IO) {
            TraktAPI.getComments(type, id, page = 1, sort = sort)
        }
        traktComments.forEach { tc ->
            val comment = traktToComment(tc)
            withContext(Dispatchers.Main) {
                section.add(
                    CommentItem(
                        comment,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        0
                    )
                )
            }
        }
        totalPages = if (traktComments.size < 25) 1 else 2
    }

    private fun traktToComment(tc: TraktComment): Comment {
        val avatarUrl = tc.user.images?.avatar?.full
        return Comment(
            commentId = tc.id,
            userId = tc.user.username,
            mediaId = mediaId,
            parentCommentId = if (tc.parentId > 0) tc.parentId else null,
            content = tc.comment,
            timestamp = tc.createdAt,
            deleted = false,
            tag = null,
            upvotes = tc.likes,
            downvotes = 0,
            userVoteType = if (tc.userLiked) 1 else 0,
            username = tc.user.name ?: tc.user.username,
            profilePictureUrl = avatarUrl,
            totalVotes = tc.likes,
            isTrakt = true
        )
    }

    private suspend fun loadSingleComment(commentId: Int) {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        section.clear()

        val comment = withContext(Dispatchers.IO) {
            CommentsAPI.getSingleComment(commentId)
        }
        if (comment != null) {
            withContext(Dispatchers.Main) {
                section.add(
                    CommentItem(
                        comment,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        0
                    )
                )
            }
        }

        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
    }

    private fun sortComments(comments: List<Comment>?): List<Comment> {
        if (comments == null) return emptyList()
        return when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
            "newest" -> comments.sortedByDescending { CommentItem.timestampToMillis(it.timestamp) }
            "oldest" -> comments.sortedBy { CommentItem.timestampToMillis(it.timestamp) }
            "highest_rated" -> comments.sortedByDescending { it.upvotes - it.downvotes }
            "lowest_rated" -> comments.sortedBy { it.upvotes - it.downvotes }
            else -> comments
        }
    }

    private fun resetOldState(): InteractionState {
        val oldState = interactionState
        interactionState = InteractionState.NONE
        return when (oldState) {
            InteractionState.EDIT -> {
                activity.binding.commentReplyToContainer.visibility = View.GONE
                activity.binding.commentInput.setText("")
                val imm = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(activity.binding.commentInput.windowToken, 0)
                commentWithInteraction?.editing(false)
                InteractionState.EDIT
            }
            InteractionState.REPLY -> {
                activity.binding.commentReplyToContainer.visibility = View.GONE
                activity.binding.commentInput.setText("")
                val imm = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(activity.binding.commentInput.windowToken, 0)
                commentWithInteraction?.replying(false)
                InteractionState.REPLY
            }
            else -> InteractionState.NONE
        }
    }

    fun editCallback(comment: CommentItem) {
        if (resetOldState() == InteractionState.EDIT) return
        commentWithInteraction = comment
        activity.binding.commentInput.setText(comment.comment.content)
        activity.binding.commentInput.requestFocus()
        activity.binding.commentInput.setSelection(activity.binding.commentInput.text.length)
        val imm = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(activity.binding.commentInput, InputMethodManager.SHOW_IMPLICIT)
        interactionState = InteractionState.EDIT
    }

    fun replyCallback(comment: CommentItem) {
        if (resetOldState() == InteractionState.REPLY) return
        commentWithInteraction = comment
        activity.binding.commentReplyToContainer.visibility = View.VISIBLE
        activity.binding.commentInput.requestFocus()
        activity.binding.commentInput.setSelection(activity.binding.commentInput.text.length)
        val imm = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(activity.binding.commentInput, InputMethodManager.SHOW_IMPLICIT)
        interactionState = InteractionState.REPLY
    }

    fun replyTo(comment: CommentItem, username: String) {
        if (comment.isReplying) {
            activity.binding.commentReplyToContainer.visibility = View.VISIBLE
            activity.binding.commentReplyTo.text = getString(R.string.replying_to, username)
            activity.binding.commentReplyToCancel.setOnClickListener {
                comment.replying(false)
                replyCallback(comment)
                activity.binding.commentReplyToContainer.visibility = View.GONE
            }
        } else {
            activity.binding.commentReplyToContainer.visibility = View.GONE
        }
    }

    fun viewReplyCallback(comment: CommentItem) {
        if (currentSource == CommentSource.DANOTSU) {
            lifecycleScope.launch {
                val replies = withContext(Dispatchers.IO) {
                    CommentsAPI.getRepliesFromId(comment.comment.commentId)
                }
                replies?.comments?.forEach {
                    val depth =
                        if (comment.commentDepth + 1 > comment.MAX_DEPTH) comment.commentDepth else comment.commentDepth + 1
                    val section =
                        if (comment.commentDepth + 1 > comment.MAX_DEPTH) comment.parentSection else comment.repliesSection
                    if (depth >= comment.MAX_DEPTH) comment.registerSubComment(it.commentId)
                    val newCommentItem = CommentItem(
                        it,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        depth
                    )
                    section.add(newCommentItem)
                }
            }
        } else {
            lifecycleScope.launch {
                val replies = withContext(Dispatchers.IO) {
                    TraktAPI.getReplies(comment.comment.commentId)
                }
                replies.forEach { tc ->
                    val traktComment = traktToComment(tc)
                    val depth =
                        if (comment.commentDepth + 1 > comment.MAX_DEPTH) comment.commentDepth else comment.commentDepth + 1
                    val section =
                        if (comment.commentDepth + 1 > comment.MAX_DEPTH) comment.parentSection else comment.repliesSection
                    if (depth >= comment.MAX_DEPTH) comment.registerSubComment(traktComment.commentId)
                    val newCommentItem = CommentItem(
                        traktComment,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        depth
                    )
                    section.add(newCommentItem)
                }
            }
        }
    }

    private fun showCommentRulesDialog() {
        activity.customAlertDialog().apply {
            setTitle("Commenting Rules")
                .setMessage(
                    "🚨 BREAK ANY RULE = YOU'RE GONE\n\n" +
                            "1. NO RACISM, DISCRIMINATION, OR HATE SPEECH\n" +
                            "2. NO SPAMMING OR SELF-PROMOTION\n" +
                            "3. ABSOLUTELY NO NSFW CONTENT\n" +
                            "4. ENGLISH ONLY – NO EXCEPTIONS\n" +
                            "5. NO IMPERSONATION, HARASSMENT, OR ABUSE\n" +
                            "6. NO ILLEGAL CONTENT OR EXTREME DISRESPECT TOWARDS ANY FANDOM\n" +
                            "7. DO NOT REQUEST OR SHARE REPOSITORIES/EXTENSIONS\n" +
                            "8. SPOILERS ALLOWED ONLY WITH SPOILER TAGS AND A WARNING\n" +
                            "9. NO SEXUALIZING OR INAPPROPRIATE COMMENTS ABOUT MINOR CHARACTERS\n" +
                            "10. IF IT'S WRONG, DON'T POST IT!\n\n"
                )
            setPosButton("I Understand") {
                PrefManager.setVal(PrefName.FirstComment, false)
                showTagDialogThenProcess()
            }
            setNegButton(R.string.cancel)
            show()
        }
    }

    private fun processComment() {
        var commentText = activity.binding.commentInput.text.toString()
        if (commentText.isEmpty()) {
            snackString("Comment cannot be empty")
            return
        }

        if (isSpoilerMode) {
            commentText = "||$commentText||"
            isSpoilerMode = false
            activity.binding.commentSpoiler.alpha = 0.5f
            activity.binding.commentSpoiler.setImageResource(R.drawable.ic_round_remove_red_eye_24)
        }

        activity.binding.commentInput.text.clear()
        val finalText = commentText
        lifecycleScope.launch {
            if (interactionState == InteractionState.EDIT) {
                handleEditComment(finalText)
            } else {
                handleNewComment(finalText)
                tag = null
                activity.binding.commentLabel.background = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_label_off_24,
                    null
                )
            }
            resetOldState()
        }
    }

    private suspend fun handleEditComment(commentText: String) {
        val success = withContext(Dispatchers.IO) {
            CommentsAPI.editComment(
                commentWithInteraction?.comment?.commentId ?: return@withContext false, commentText
            )
        }
        if (success) {
            updateCommentInSection(commentText)
        }
    }

    private fun updateCommentInSection(commentText: String) {
        val groups = section.groups
        groups.forEach { item ->
            if (item is CommentItem && item.comment.commentId == commentWithInteraction?.comment?.commentId) {
                updateCommentItem(item, commentText)
                snackString("Comment edited")
            }
        }
    }

    private fun updateCommentItem(item: CommentItem, commentText: String) {
        item.comment.content = commentText
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        item.comment.timestamp = dateFormat.format(System.currentTimeMillis())
        item.notifyChanged()
    }

    private suspend fun handleNewComment(commentText: String) {
        if (currentSource == CommentSource.TRAKT) {
            handleTraktNewComment(commentText)
            return
        }
        val success = withContext(Dispatchers.IO) {
            CommentsAPI.comment(
                mediaId,
                if (interactionState == InteractionState.REPLY) commentWithInteraction?.comment?.commentId else null,
                commentText,
                tag
            )
        }
        success?.let {
            if (interactionState == InteractionState.REPLY) {
                if (commentWithInteraction == null) return@let
                val section =
                    if (commentWithInteraction!!.commentDepth + 1 > commentWithInteraction!!.MAX_DEPTH) commentWithInteraction?.parentSection else commentWithInteraction?.repliesSection
                val depth =
                    if (commentWithInteraction!!.commentDepth + 1 > commentWithInteraction!!.MAX_DEPTH) commentWithInteraction!!.commentDepth else commentWithInteraction!!.commentDepth + 1
                if (depth >= commentWithInteraction!!.MAX_DEPTH) commentWithInteraction!!.registerSubComment(
                    it.commentId
                )
                section?.add(
                    if (commentWithInteraction!!.commentDepth + 1 > commentWithInteraction!!.MAX_DEPTH) 0 else section.itemCount,
                    CommentItem(
                        it,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        depth
                    )
                )
            } else {
                section.add(
                    0,
                    CommentItem(
                        it,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        section,
                        this@CommentsFragment,
                        backgroundColor,
                        0
                    )
                )
            }
        }
    }

    private suspend fun handleTraktNewComment(commentText: String) {
        if (interactionState == InteractionState.REPLY) {
            val parentId = commentWithInteraction?.comment?.commentId ?: return
            val reply = withContext(Dispatchers.IO) {
                TraktAPI.replyToComment(parentId, commentText)
            }
            reply?.let { tc ->
                val traktComment = traktToComment(tc)
                val depth = if (commentWithInteraction!!.commentDepth + 1 > commentWithInteraction!!.MAX_DEPTH)
                    commentWithInteraction!!.commentDepth else commentWithInteraction!!.commentDepth + 1
                val sec = if (commentWithInteraction!!.commentDepth + 1 > commentWithInteraction!!.MAX_DEPTH)
                    commentWithInteraction?.parentSection else commentWithInteraction?.repliesSection
                sec?.add(
                    CommentItem(
                        traktComment,
                        buildMarkwon(activity, fragment = this@CommentsFragment),
                        sec,
                        this@CommentsFragment,
                        backgroundColor,
                        depth
                    )
                )
                snackString("Replied on Trakt")
            }
        } else {
            val type = traktResult?.mediaType ?: return
            val id = traktResult?.traktId ?: return
            val posted = withContext(Dispatchers.IO) {
                TraktAPI.postComment(type, id, commentText, isSpoilerMode)
            }
            posted?.let { tc ->
                val traktComment = traktToComment(tc)
                section.add(0, CommentItem(
                    traktComment,
                    buildMarkwon(activity, fragment = this@CommentsFragment),
                    section,
                    this@CommentsFragment,
                    backgroundColor,
                    0
                ))
                snackString("Comment posted on Trakt")
            }
        }
    }
}
