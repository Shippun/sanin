package ani.sanin.media.anime

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.BottomSheetDialogFragment
import ani.sanin.databinding.ItemSubtitleSyncBinding
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
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

class SubtitleSyncDialogFragment : BottomSheetDialogFragment() {
    private var _binding: ani.sanin.databinding.BottomSheetSubtitleSyncBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var currentOffset: Long = 0L
    private var updateJob: Job? = null
    private lateinit var adapter: SyncAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ani.sanin.databinding.BottomSheetSubtitleSyncBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentOffset = PrefManager.getVal<Long>(PrefName.SubtitleDelay)
        setupViews()
        startUpdateLoop()
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
                    if (::adapter.isInitialized) {
                        adapter.updatePlayerPosition(getPlayerPosition())
                    }
                }
            }
        })

        applySyncButtonFocus(binding.syncSubtractMore)
        applySyncButtonFocus(binding.syncSubtract)
        applySyncButtonFocus(binding.syncOffsetInput)
        applySyncButtonFocus(binding.syncAdd)
        applySyncButtonFocus(binding.syncAddMore)
        applySyncButtonFocus(binding.syncCancel)
        applySyncButtonFocus(binding.syncReset)
        applySyncButtonFocus(binding.syncApply)

        val buttonChange = 100L
        val buttonChangeMore = 1000L

        binding.syncSubtractMore.setOnClickListener { changeBy(-buttonChangeMore) }
        binding.syncSubtract.setOnClickListener { changeBy(-buttonChange) }
        binding.syncAdd.setOnClickListener { changeBy(buttonChange) }
        binding.syncAddMore.setOnClickListener { changeBy(buttonChangeMore) }
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
            adapter = SyncAdapter(cues, getPlayerPosition())
            binding.subtitleSyncRecycler.adapter = adapter
            scrollToCurrentCue()
        }

        updateStatusText()
    }

    private fun applySyncButtonFocus(button: View) {
        val borderWidthPx = 3f
        button.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
                val borderDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(android.graphics.Color.TRANSPARENT)
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
                    buttonSavedForegrounds[v] = v.foreground
                    v.foreground = borderDrawable
                }
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    v.foreground = buttonSavedForegrounds.remove(v)
                }
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }

    private val buttonSavedForegrounds = mutableMapOf<View, android.graphics.drawable.Drawable?>()

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
                if (::adapter.isInitialized) {
                    adapter.updatePlayerPosition(getPlayerPosition())
                }
                delay(250)
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
        initialPosition: Long
    ) : RecyclerView.Adapter<SyncAdapter.CueViewHolder>() {
        private var playerPositionMs: Long = initialPosition

        fun updatePlayerPosition(pos: Long) {
            val oldPos = playerPositionMs
            playerPositionMs = pos
            val adjustedPos = pos - currentOffset

            cues.forEachIndexed { index, cue ->
                val wasPlaying = oldPos - currentOffset in cue.startTimeMs..<(cue.startTimeMs + cue.durationMs)
                val isPlaying = adjustedPos in cue.startTimeMs..<(cue.startTimeMs + cue.durationMs)
                if (wasPlaying != isPlaying || index == getLatestActiveIndex()) {
                    notifyItemChanged(index)
                }
            }
        }

        private fun getLatestActiveIndex(): Int {
            val adjustedPos = playerPositionMs - currentOffset
            return cues.indexOfLast { adjustedPos >= it.startTimeMs }.coerceAtLeast(0)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CueViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemBinding = ItemSubtitleSyncBinding.inflate(inflater, parent, false)
            applySyncCardFocus(itemBinding.root)
            return CueViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: CueViewHolder, position: Int) {
            val cue = cues[position]
            val adjustedPos = playerPositionMs - currentOffset
            val isPlaying = adjustedPos in cue.startTimeMs..<(cue.startTimeMs + cue.durationMs)

            holder.textView.text = cue.text
            holder.textView.setTextColor(if (isPlaying) -0x1 else -0x555556)

            val showProgress = isPlaying
            holder.progressBar.isInvisible = !showProgress
            if (showProgress) {
                val progressValue = ((adjustedPos - cue.startTimeMs) * 1000f / cue.durationMs).roundToInt()
                    .coerceIn(0, 1000)
                ObjectAnimator.ofInt(
                    holder.progressBar, "progress",
                    holder.progressBar.progress, progressValue
                ).apply {
                    interpolator = DecelerateInterpolator()
                }.start()
            } else {
                holder.progressBar.progress = 0
            }

            holder.cardView.setCardBackgroundColor(if (isPlaying) -0x22000001 else 0)
        }

        override fun getItemCount(): Int = cues.size
    }

    inner class CueViewHolder(val itemBinding: ItemSubtitleSyncBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        val textView: TextView = itemBinding.subtitleSyncText
        val progressBar: ProgressBar = itemBinding.subtitleSyncProgress
        val cardView: androidx.cardview.widget.CardView = itemBinding.root
    }

    private fun applySyncCardFocus(cardView: View) {
        val defaultRadius = 18f
        val borderWidthPx = 3f
        cardView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val primaryColor = FocusEffectUtil.getPrimaryColor(v.context)
                val borderDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(android.graphics.Color.TRANSPARENT)
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
                    savedForegrounds[v] = v.foreground
                    v.foreground = borderDrawable
                }
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    v.foreground = savedForegrounds.remove(v)
                }
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
        if (cardView.isFocused) {
            cardView.onFocusChangeListener?.onFocusChange(cardView, true)
        }
    }

    private val savedForegrounds = mutableMapOf<View, android.graphics.drawable.Drawable?>()
}
