package ani.dantotsu.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.databinding.DialogUserAgentBinding
import ani.dantotsu.databinding.FragmentLoginBinding
import ani.dantotsu.loadImage
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.settings.SettingsAccountActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.settings.saving.internal.PreferenceKeystore
import ani.dantotsu.settings.saving.internal.PreferencePackager
import ani.dantotsu.toast
import ani.dantotsu.util.Logger
import ani.dantotsu.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var pollJob: kotlinx.coroutines.Job? = null
    private val relayClient = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rescueMode = ani.dantotsu.settings.saving.PrefManager.getVal<Boolean>(ani.dantotsu.settings.saving.PrefName.RescueMode)
        if (rescueMode) {
            binding.loginButton.text = getString(R.string.login)
            (binding.loginButton as com.google.android.material.button.MaterialButton).setIconResource(R.drawable.ic_myanimelist)
            binding.loginButton.setOnClickListener { ani.dantotsu.connections.mal.MAL.loginIntent(requireActivity()) }
        } else {
            binding.loginButton.setOnClickListener { Anilist.loginIntent(requireActivity()) }
        }
        binding.loginDiscord.setOnClickListener { openLinkInBrowser(getString(R.string.discord)) }
        binding.loginGithub.setOnClickListener { openLinkInBrowser(getString(R.string.github)) }
        binding.loginTelegram.setOnClickListener { openLinkInBrowser(getString(R.string.telegram)) }

        val oauthUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=14959&response_type=token"

        binding.loginQrButton.setOnClickListener {
            val relayUrl = SettingsAccountActivity.relayBaseUrl
            if (relayUrl.isNullOrBlank()) {
                val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${URLEncoder.encode(oauthUrl, "UTF-8")}"
                binding.loginQrCode.loadImage(qrUrl)
                binding.loginQrCode.visibility = View.VISIBLE
                binding.loginTokenInput.visibility = View.VISIBLE
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val sessionId = withContext(Dispatchers.IO) { createRelaySession(relayUrl) }
                    if (sessionId == null) {
                        toast("Failed to create QR session — check relay URL")
                        return@launch
                    }
                    val authUrl = "$relayUrl/auth/$sessionId"
                    val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${URLEncoder.encode(authUrl, "UTF-8")}"
                    binding.loginQrCode.loadImage(qrUrl)
                    binding.loginQrCode.visibility = View.VISIBLE
                    binding.loginTokenInput.visibility = View.GONE

                    pollJob = viewLifecycleOwner.lifecycleScope.launch {
                        while (isActive) {
                            delay(2000)
                            val token = withContext(Dispatchers.IO) { pollRelaySession(relayUrl, sessionId) }
                            if (token != null) {
                                PrefManager.setVal(PrefName.AnilistToken, token)
                                if (Anilist.getSavedToken()) {
                                    toast("Login successful")
                                    restartApp()
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    toast("QR login error: ${e.message}")
                }
            }
        }
        binding.loginTokenSubmit.setOnClickListener {
            val token = binding.loginTokenEditText.text?.toString()?.trim()
            if (!token.isNullOrBlank()) {
                PrefManager.setVal(PrefName.AnilistToken, token)
                if (Anilist.getSavedToken()) {
                    toast("Login successful")
                    restartApp()
                } else {
                    toast("Invalid token")
                }
            } else {
                toast("Enter a token")
            }
        }

        val openDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val jsonString =
                            requireActivity().contentResolver.openInputStream(uri)?.readBytes()
                                ?: throw Exception("Error reading file")
                        val name =
                            DocumentFile.fromSingleUri(requireActivity(), uri)?.name ?: "settings"
                        //.sani is encrypted, .ani is not
                        if (name.endsWith(".sani")) {
                            passwordAlertDialog { password ->
                                if (password != null) {
                                    val salt = jsonString.copyOfRange(0, 16)
                                    val encrypted = jsonString.copyOfRange(16, jsonString.size)
                                    val decryptedJson = try {
                                        PreferenceKeystore.decryptWithPassword(
                                            password,
                                            encrypted,
                                            salt
                                        )
                                    } catch (e: Exception) {
                                        toast("Incorrect password")
                                        return@passwordAlertDialog
                                    }
                                    if (PreferencePackager.unpack(decryptedJson))
                                        restartApp()
                                } else {
                                    toast("Password cannot be empty")
                                }
                            }
                        } else if (name.endsWith(".ani")) {
                            val decryptedJson = jsonString.toString(Charsets.UTF_8)
                            if (PreferencePackager.unpack(decryptedJson))
                                restartApp()
                        } else {
                            toast("Invalid file type")
                        }
                    } catch (e: Exception) {
                        Logger.log(e)
                        toast("Error importing settings")
                    }
                }
            }

        binding.importSettingsButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }
    }

    private fun passwordAlertDialog(callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater).apply {
            userAgentTextBox.hint = "Password"
            subtitle.visibility = View.VISIBLE
            subtitle.text = getString(R.string.enter_password_to_decrypt_file)
        }

        requireActivity().customAlertDialog().apply {
            setTitle("Enter Password")
            setCustomView(dialogView.root)
            setPosButton(R.string.ok) {
                val editText = dialogView.userAgentTextBox
                if (editText.text?.isNotBlank() == true) {
                    editText.text?.toString()?.trim()?.toCharArray(password)
                    callback(password)
                } else {
                    toast("Password cannot be empty")
                }
            }
            setNegButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
        }.show()


    }

    private fun restartApp() {
        val intent = Intent(requireActivity(), requireActivity().javaClass)
        requireActivity().finish()
        startActivity(intent)
    }

    override fun onDestroyView() {
        pollJob?.cancel()
        pollJob = null
        super.onDestroyView()
    }

    private fun createRelaySession(relayUrl: String): String? {
        try {
            val sessionId = UUID.randomUUID().toString().take(12)
            val body = "{\"id\":\"$sessionId\"}".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$relayUrl/api/session")
                .post(body)
                .build()
            val response = relayClient.newCall(request).execute()
            return if (response.isSuccessful) sessionId else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun pollRelaySession(relayUrl: String, sessionId: String): String? {
        try {
            val request = Request.Builder()
                .url("$relayUrl/api/poll/$sessionId")
                .get()
                .build()
            val response = relayClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body!!.string())
            if (json.optBoolean("authorized")) {
                return json.optString("token", null)
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

}