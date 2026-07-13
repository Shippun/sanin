package ani.sanin.media.comments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import ani.sanin.databinding.DialogCommentZoomBinding
import ani.sanin.loadImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentZoomDialog : DialogFragment() {
    private var _binding: DialogCommentZoomBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommentZoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return

        binding.zoomUserName.text = args.getString("username")

        val timestamp = args.getString("timestamp") ?: ""
        binding.zoomUserTime.text = formatTimestamp(timestamp)
        binding.zoomCommentText.text = args.getString("content")
        binding.zoomVotes.text = args.getString("votes")
        val tag = args.getString("tag")
        if (tag != null) {
            binding.zoomTag.visibility = View.VISIBLE
            binding.zoomTag.text = tag
        } else {
            binding.zoomTag.visibility = View.GONE
        }
        args.getString("avatarUrl")?.let { binding.zoomUserAvatar.loadImage(it) }

        binding.zoomClose.setOnClickListener { dismiss() }
        binding.root.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setGravity(Gravity.CENTER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 25
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.5f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(timestamp)
            val diff = System.currentTimeMillis() - (parsed?.time ?: 0)
            val days = diff / (24 * 60 * 60 * 1000)
            val hours = diff / (60 * 60 * 1000) % 24
            val minutes = diff / (60 * 1000) % 60
            when {
                days > 0 -> "${days}d"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "now"
            }
        } catch (_: Exception) {
            "now"
        }
    }

    companion object {
        fun newInstance(
            username: String,
            timestamp: String,
            content: String,
            votes: String,
            tag: String?,
            avatarUrl: String?
        ): CommentZoomDialog {
            val args = Bundle().apply {
                putString("username", username)
                putString("timestamp", timestamp)
                putString("content", content)
                putString("votes", votes)
                putString("tag", tag)
                putString("avatarUrl", avatarUrl)
            }
            val dialog = CommentZoomDialog()
            dialog.arguments = args
            return dialog
        }
    }
}
