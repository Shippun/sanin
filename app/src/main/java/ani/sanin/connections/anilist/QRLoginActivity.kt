package ani.sanin.connections.anilist

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import ani.sanin.client
import ani.sanin.databinding.ActivityQrLoginBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.startMainActivity
import ani.sanin.themes.ThemeManager
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QRLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrLoginBinding
    private var sessionId: String? = null
    private val poller = Handler(Looper.getMainLooper())
    private var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityQrLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.qrBack.setOnClickListener { finish() }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val relayUrl = RELAY_URL.trimEnd('/')
                val resp = client.post("$relayUrl/init")
                val body = withContext(Dispatchers.IO) { resp.text }
                val json = org.json.JSONObject(body)
                val sid = json.getString("sessionId")
                sessionId = sid

                val authUrl = "https://anilist.co/api/v2/oauth/authorize" +
                        "?client_id=45857" +
                        "&response_type=token" +
                        "&redirect_url=$relayUrl/callback?session=$sid"

                val qrBitmap = generateQrCode(authUrl, 480)
                withContext(Dispatchers.Main) {
                    binding.qrCodeImage.setImageBitmap(qrBitmap)
                    binding.qrCancel.visibility = View.VISIBLE
                    binding.qrCancel.setOnClickListener { finish() }
                    startPolling(relayUrl, sid)
                }
            } catch (e: Exception) {
                Logger.log("QRLogin: init error — ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.qrStatus.text = getString(ani.sanin.R.string.qr_connection_error)
                    binding.qrCancel.visibility = View.VISIBLE
                    binding.qrCancel.setOnClickListener { finish() }
                }
            }
        }
    }

    private fun startPolling(relayUrl: String, sid: String) {
        polling = true
        val task = object : Runnable {
            override fun run() {
                if (!polling) return
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val resp = client.get("$relayUrl/poll?session=$sid")
                        val body = resp.text
                        val json = org.json.JSONObject(body)
                        val status = json.optString("status")
                        if (status == "done") {
                            val token = json.optString("token", null)
                            if (token != null) {
                                Anilist.token = token
                                PrefManager.setVal(PrefName.AnilistToken, token)
                                polling = false
                                withContext(Dispatchers.Main) {
                                    binding.qrStatus.setText(ani.sanin.R.string.qr_success)
                                    binding.qrCancel.text =
                                        getString(ani.sanin.R.string.continue_label)
                                    binding.qrCancel.setOnClickListener {
                                        startMainActivity(this@QRLoginActivity)
                                    }
                                }
                                return@launch
                            }
                        } else if (status == "expired") {
                            polling = false
                            withContext(Dispatchers.Main) {
                                binding.qrStatus.setText(ani.sanin.R.string.qr_expired)
                                binding.qrCancel.text = getString(ani.sanin.R.string.retry)
                                binding.qrCancel.setOnClickListener {
                                    recreate()
                                }
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Logger.log("QRLogin: poll error — ${e.message}")
                    }
                    if (polling) poller.postDelayed(task, 2000)
                }
            }
        }
        poller.post(task)
    }

    override fun onDestroy() {
        polling = false
        poller.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val RELAY_URL = "https://anilist-relay.shemaus58.workers.dev"

        private fun generateQrCode(text: String, size: Int): Bitmap? {
            return try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(
                            x, y,
                            if (bitMatrix[x, y]) android.graphics.Color.BLACK
                            else android.graphics.Color.WHITE
                        )
                    }
                }
                bitmap
            } catch (e: Exception) {
                Logger.log("QRLogin: QR generation error — ${e.message}")
                null
            }
        }
    }
}
