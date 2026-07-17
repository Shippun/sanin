package ani.sanin.media.anime

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.databinding.BottomSheetSubtitleSyncBinding
import ani.sanin.databinding.ItemSubtitleSyncBinding
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SyncCue(
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long = 3000L
)

class SubtitleSyncDialogFragment : DialogFragment() {
    private var _binding: BottomSheetSubtitleSyncBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var currentOffset: Long = 0L
    private var updateJob: Job? = null
    private var adapter: SyncAdapter? = null
    private var prevPlayingIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Translucent_NoTitleBar)
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
            w.navigationBarColor = Color.TRANSPARENT
        }
        GlassEffectManager.applyGlassToSheet(binding.syncRoot, GlassComponent.SubtitleSync, 16f)
        animateEntry()
    }

    private fun animateEntry() {
        val density = resources.displayMetrics.density
        binding.syncRoot.apply {
            pivotY = 0f
            pivotX = width / 2f
            rotationX = 10f
            translationY = 40f * density
            scaleY = 0.96f
            alpha = 0.8f
        }
        binding.syncRoot.post {
            val lift = ObjectAnimator.ofFloat(binding.syncRoot, View.TRANSLATION_Y, 0f).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
            }
            val tilt = ObjectAnimator.ofFloat(binding.syncRoot, View.ROTATION_X, 0f).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
            }
            val scale = ObjectAnimator.ofFloat(binding.syncRoot, View.SCALE_Y, 1f).apply {
                duration = 280
                interpolator = OvershootInterpolator(1.5f)
            }
            val fade = ObjectAnimator.ofFloat(binding.syncRoot, View.ALPHA, 1f).apply {
                duration = 200
            }
            AnimatorSet().apply {
                playTogether(lift, tilt, scale, fade)
                start()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSubtitleSyncBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentOffset = PrefManager.getVal<Long>(PrefName.SubtitleDelay)
        setupViews()
        startUpdateLoop()
    }

    private fun onCueClicked(cue: SyncCue) {
        val playerPos = getPlayerPosition()
        currentOffset = playerPos - cue.startTimeMs
        binding.syncOffsetInput.setText(currentOffset.toString())
    }

    private fun setupViews() {
        binding.syncOffsetInput.setText(currentOffset.toString())
        binding.syncOffsetInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toLongOrNull()?.let { offset ->
                    currentOffset = offset
                    updateStatusText()
                }
            }
        })

        applySyncFocus(binding.syncSubtractMore)
        applySyncFocus(binding.syncSubtract)
        applySyncFocus(binding.syncOffsetInput)
        applySyncFocus(binding.syncAdd)
        applySyncFocus(binding.syncAddMore)
        applySyncFocus(binding.syncCancel)
        applySyncFocus(binding.syncReset)
        applySyncFocus(binding.syncApply)

        binding.syncOffsetInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.syncOffsetInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.syncSubtractMore.setOnClickListener { changeBy(-1000L) }
        binding.syncSubtract.setOnClickListener { changeBy(-100L) }
        binding.syncAdd.setOnClickListener { changeBy(100L) }
        binding.syncAddMore.setOnClickListener { changeBy(1000L) }
        binding.syncApply.setOnClickListener { applyOffset() }
        binding.syncReset.setOnClickListener { resetOffset() }
        binding.syncCancel.setOnClickListener { dismiss() }

        binding.subtitleSyncRecycler.layoutManager = LinearLayoutManager(requireContext())

        val cues = (requireActivity() as? ExoplayerView)?.getSyncCues() ?: emptyList()

        if (cues.isEmpty()) {
            binding.noSubtitlesNotice.isVisible = true
            binding.subtitleSyncRecycler.isVisible = false
        } else {
            binding.noSubtitlesNotice.isVisible = false
            binding.subtitleSyncRecycler.isVisible = true
            adapter = SyncAdapter(cues, getPlayerPosition(), onCueClick = ::onCueClicked)
            binding.subtitleSyncRecycler.adapter = adapter
            scrollToCurrentCue()
        }

        updateStatusText()
    }

    private fun applySyncFocus(view: View) {
        val borderWidthPx = 3f
        val savedFg = mutableMapOf<View, android.graphics.drawable.Drawable?>()
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
                val border = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP, borderWidthPx,
                            v.resources.displayMetrics
                        ).toInt(),
                        primaryColor
                    )
                    setCornerRadius(16f * v.resources.displayMetrics.density)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    savedFg[v] = v.foreground
                    v.foreground = border
                }
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    v.foreground = savedFg.remove(v)
                }
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }

    private fun changeBy(delta: Long) {
        val current = (binding.syncOffsetInput.text?.toString()?.toLongOrNull() ?: 0) + delta
        binding.syncOffsetInput.setText(current.toString())
    }

    private fun applyOffset() {
        PrefManager.setVal(PrefName.SubtitleDelay, currentOffset)
        PrefManager.setVal(PrefName.SubtitleSyncEnabled, currentOffset != 0L)
        (requireActivity() as? ExoplayerView)?.applySubtitleOffset(currentOffset)
        dismiss()
    }

    private fun resetOffset() {
        currentOffset = 0L
        binding.syncOffsetInput.setText("0")
        PrefManager.setVal(PrefName.SubtitleDelay, 0L)
        PrefManager.setVal(PrefName.SubtitleSyncEnabled, false)
        (requireActivity() as? ExoplayerView)?.applySubtitleOffset(0L)
        dismiss()
    }

    private fun getPlayerPosition(): Long {
        return (requireActivity() as? ExoplayerView)?.getPlayerPosition() ?: 0L
    }

    private fun updateStatusText() {
        val status = when {
            currentOffset > 0L -> "Offset: +${currentOffset}ms - Subtitles appear later"
            currentOffset < 0L -> "Offset: ${currentOffset}ms - Subtitles appear earlier"
            else -> "Offset: 0ms - In sync"
        }
        binding.syncStatusText.text = status
    }

    private fun scrollToCurrentCue() {
        val position = getPlayerPosition()
        val cues = (requireActivity() as? ExoplayerView)?.getSyncCues() ?: return
        val index = cues.indexOfLast { position >= it.startTimeMs }
        if (index >= 0) {
            binding.subtitleSyncRecycler.post {
                binding.subtitleSyncRecycler.scrollToPosition(index)
            }
        }
    }

    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                adapter?.updatePlayerPosition(getPlayerPosition())
                delay(500)
            }
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        _binding = null
        super.onDestroy()
    }

    inner class SyncAdapter(
        private val cues: List<SyncCue>,
        initialPosition: Long,
        private val onCueClick: (SyncCue) -> Unit = {}
    ) : RecyclerView.Adapter<CueViewHolder>() {
        private var playerPositionMs: Long = initialPosition

        fun updatePlayerPosition(pos: Long) {
            val oldPos = playerPositionMs
            playerPositionMs = pos
            val adjustedPos = pos - currentOffset

            var newPlayingIndex = -1
            cues.forEachIndexed { index, cue ->
                val isPlaying = adjustedPos in cue.startTimeMs..<(cue.startTimeMs + cue.durationMs)
                if (isPlaying) newPlayingIndex = index
            }

            if (prevPlayingIndex != newPlayingIndex) {
                if (prevPlayingIndex >= 0) notifyItemChanged(prevPlayingIndex)
                if (newPlayingIndex >= 0) notifyItemChanged(newPlayingIndex)
                prevPlayingIndex = newPlayingIndex
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CueViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemBinding = ItemSubtitleSyncBinding.inflate(inflater, parent, false)
            applyCardFocus(itemBinding.root)
            return CueViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: CueViewHolder, position: Int) {
            val cue = cues[position]
            val adjustedPos = playerPositionMs - currentOffset
            val isPlaying = adjustedPos in cue.startTimeMs..<(cue.startTimeMs + cue.durationMs)

            holder.textView.text = cue.text
            holder.textView.setTextColor(if (isPlaying) Color.WHITE else -0x555556)

            val showProgress = isPlaying && isPlaying
            holder.progressBar.isVisible = showProgress
            if (showProgress) {
                val progressValue = ((adjustedPos - cue.startTimeMs) * 1000f / cue.durationMs).roundToInt()
                    .coerceIn(0, 1000)
                if (holder.progressBar.progress != progressValue) {
                    holder.progressBar.progress = progressValue
                }
            } else {
                holder.progressBar.progress = 0
            }

            holder.cardView.setCardBackgroundColor(if (isPlaying) -0x33000001 else 0)
            holder.cardView.setOnClickListener { onCueClick(cue) }
        }

        override fun getItemCount(): Int = cues.size
    }

    inner class CueViewHolder(val itemBinding: ItemSubtitleSyncBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        val textView: TextView = itemBinding.subtitleSyncText
        val progressBar: ProgressBar = itemBinding.subtitleSyncProgress
        val cardView: androidx.cardview.widget.CardView = itemBinding.root
    }

    private fun applyCardFocus(cardView: View) {
        val defaultRadius = 18f
        val borderWidthPx = 3f
        val savedFg = mutableMapOf<View, android.graphics.drawable.Drawable?>()
        cardView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
                val border = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP, borderWidthPx,
                            v.resources.displayMetrics
                        ).toInt(),
                        primaryColor
                    )
                    setCornerRadius(defaultRadius * v.resources.displayMetrics.density)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    savedFg[v] = v.foreground
                    v.foreground = border
                }
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    v.foreground = savedFg.remove(v)
                }
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }
}
