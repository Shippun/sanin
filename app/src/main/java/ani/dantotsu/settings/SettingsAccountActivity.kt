package ani.dantotsu.settings

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.discord.Discord
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.databinding.ActivitySettingsAccountsBinding
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.navBarHeight
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.others.CustomBottomDialog
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.startMainActivity
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.customAlertDialog
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ani.dantotsu.util.LocalRelayServer
import java.net.NetworkInterface
import java.net.URLEncoder

class SettingsAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAccountsBinding
    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@SettingsAccountActivity)
    }
    private var pollJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this

        binding = ActivitySettingsAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAccountsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            accountSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

            settingsAccountHelp.setOnClickListener {
                CustomBottomDialog.newInstance().apply {
                    setTitleText(context.getString(R.string.account_help))
                    addView(
                        TextView(it.context).apply {
                            val markWon = Markwon.builder(it.context)
                                .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                            markWon.setMarkdown(this, context.getString(R.string.full_account_help))
                        }
                    )
                }.show(supportFragmentManager, "dialog")
            }

            fun reload() {
                if (Anilist.token != null) {
                    settingsAnilistLogin.setText(R.string.logout)
                    settingsAnilistLogin.setOnClickListener {
                        Anilist.removeSavedToken()
                        restartMainActivity.isEnabled = true
                        reload()
                    }
                    settingsAnilistUsername.visibility = View.VISIBLE
                    settingsAnilistUsername.text = Anilist.username
                    settingsAnilistAvatar.loadImage(Anilist.avatar)
                    settingsAnilistAvatar.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val anilistLink = getString(
                            R.string.anilist_link,
                            PrefManager.getVal<String>(PrefName.AnilistUserName)
                        )
                        openLinkInBrowser(anilistLink)
                    }

                    if (Anilist.bg != null) {
                        settingsAnilistBanner.visibility = View.VISIBLE
                        settingsAnilistScrim.visibility = View.VISIBLE
                        settingsAnilistBanner.loadImage(Anilist.bg)
                    } else {
                        settingsAnilistBanner.visibility = View.GONE
                        settingsAnilistScrim.visibility = View.GONE
                    }
                    
                    val daysLeft = Anilist.getTokenExpiryDays()
                    if (daysLeft != null) {
                        settingsAnilistTokenExpiry.visibility = View.VISIBLE
                        settingsAnilistTokenExpiry.text = when {
                            daysLeft <= 0 -> "Reconnect Now"
                            else -> "Reconnect in $daysLeft days"
                        }
                        settingsAnilistTokenExpiry.setOnClickListener {
                            Anilist.loginIntent(context)
                        }
                    } else {
                        settingsAnilistTokenExpiry.visibility = View.GONE
                    }

                    settingsMALLoginRequired.visibility = View.GONE
                    settingsMALLogin.visibility = View.VISIBLE
                    settingsMALUsername.visibility = View.VISIBLE

                    if (MAL.token != null) {
                        settingsMALLogin.setText(R.string.logout)
                        settingsMALLogin.setOnClickListener {
                            MAL.removeSavedToken()
                            restartMainActivity.isEnabled = true
                            reload()
                        }
                        settingsMALUsername.visibility = View.VISIBLE
                        settingsMALUsername.text = MAL.username
                        settingsMALAvatar.loadImage(MAL.avatar)
                        settingsMALAvatar.setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            openLinkInBrowser(getString(R.string.myanilist_link, MAL.username))
                        }
                    } else {
                        settingsMALAvatar.setImageResource(R.drawable.ic_round_person_24)
                        settingsMALUsername.visibility = View.GONE
                        settingsMALLogin.setText(R.string.login)
                        settingsMALLogin.setOnClickListener {
                            MAL.loginIntent(context)
                        }
                    }
                } else {
                    settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                    settingsAnilistUsername.visibility = View.GONE
                    settingsAnilistTokenExpiry.visibility = View.GONE
                    settingsAnilistBanner.visibility = View.GONE
                    settingsAnilistScrim.visibility = View.GONE
                    settingsRecyclerView.visibility = View.GONE
                    settingsAnilistLogin.setText(R.string.login)
                    settingsAnilistLogin.setOnClickListener {
                        customAlertDialog().apply {
                            setTitle("Login Method")
                            singleChoiceItems(
                                arrayOf("Browser", "QR Code"),
                                onItemSelected = { index ->
                                    when (index) {
                                        0 -> Anilist.loginIntent(context)
                                         1 -> showQrLoginDialog()
                                    }
                                }
                            )
                        }.show()
                    }
                    settingsMALLoginRequired.visibility = View.VISIBLE
                    settingsMALLogin.visibility = View.GONE
                    settingsMALUsername.visibility = View.GONE
                }

                if (Discord.token != null) {
                    val id = PrefManager.getVal(PrefName.DiscordId, null as String?)
                    val avatar = PrefManager.getVal(PrefName.DiscordAvatar, null as String?)
                    val username = PrefManager.getVal(PrefName.DiscordUserName, null as String?)
                    if (id != null && avatar != null) {
                        settingsDiscordAvatar.loadImage("https://cdn.discordapp.com/avatars/$id/$avatar.png")
                        settingsDiscordAvatar.setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            val discordLink = getString(R.string.discord_link, id)
                            openLinkInBrowser(discordLink)
                        }
                    }
                    settingsDiscordUsername.visibility = View.VISIBLE
                    settingsDiscordUsername.text =
                        username ?: Discord.token?.replace(Regex("."), "*")
                    settingsDiscordLogin.setText(R.string.logout)
                    settingsDiscordLogin.setOnClickListener {
                        Discord.removeSavedToken(context)
                        restartMainActivity.isEnabled = true
                        reload()
                    }

                    settingsPresenceSwitcher.visibility = View.VISIBLE
                    var initialStatus = when (PrefManager.getVal<String>(PrefName.DiscordStatus)) {
                        "online" -> R.drawable.discord_status_online
                        "idle" -> R.drawable.discord_status_idle
                        "dnd" -> R.drawable.discord_status_dnd
                        "invisible" -> R.drawable.discord_status_invisible
                        else -> R.drawable.discord_status_online
                    }
                    settingsPresenceSwitcher.setImageResource(initialStatus)

                    val zoomInAnimation =
                        AnimationUtils.loadAnimation(context, R.anim.bounce_zoom)
                    settingsPresenceSwitcher.setOnClickListener {
                        var status = "online"
                        initialStatus = when (initialStatus) {
                            R.drawable.discord_status_online -> {
                                status = "idle"
                                R.drawable.discord_status_idle
                            }

                            R.drawable.discord_status_idle -> {
                                status = "dnd"
                                R.drawable.discord_status_dnd
                            }

                            R.drawable.discord_status_dnd -> {
                                status = "invisible"
                                R.drawable.discord_status_invisible
                            }

                            R.drawable.discord_status_invisible -> {
                                status = "online"
                                R.drawable.discord_status_online
                            }

                            else -> R.drawable.discord_status_online
                        }

                        PrefManager.setVal(PrefName.DiscordStatus, status)
                        settingsPresenceSwitcher.setImageResource(initialStatus)
                        settingsPresenceSwitcher.startAnimation(zoomInAnimation)
                    }
                    settingsPresenceSwitcher.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        DiscordDialogFragment().show(supportFragmentManager, "dialog")
                        true
                    }
                } else {
                    settingsPresenceSwitcher.visibility = View.GONE
                    settingsDiscordAvatar.setImageResource(R.drawable.ic_round_person_24)
                    settingsDiscordUsername.visibility = View.GONE
                    settingsDiscordLogin.setText(R.string.login)
                    settingsDiscordLogin.setOnClickListener {
                        Discord.warning(context)
                            .show(supportFragmentManager, "dialog")
                    }
                }
            }
            reload()
        }
        binding.settingsRecyclerView.adapter = SettingsAdapter(
            arrayListOf(
                Settings(
                    type = 2,
                    name = getString(R.string.enable_rpc),
                    desc = getString(R.string.enable_rpc_desc),
                    icon = R.drawable.interests_24,
                    isChecked = PrefManager.getVal(PrefName.rpcEnabled),
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.rpcEnabled, isChecked)
                    },
                    isVisible = Discord.token != null
                ),
                Settings(
                    type = 1,
                    name = getString(R.string.anilist_settings),
                    desc = getString(R.string.alsettings_desc),
                    icon = R.drawable.ic_anilist,
                    onClick = {
                        lifecycleScope.launch {
                            Anilist.query.getUserData()
                            startActivity(Intent(context, AnilistSettingsActivity::class.java))
                        }
                    },
                    isActivity = true
                ),
                Settings(
                    type = 2,
                    name = getString(R.string.comments_button),
                    desc = getString(R.string.comments_button_desc),
                    icon = R.drawable.ic_round_comment_24,
                    isChecked = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1,
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.CommentsEnabled, if (isChecked) 1 else 2)
                        reload()
                    },
                    isVisible = Anilist.token != null
                ),
            )
        )
        binding.settingsRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

    }

    private fun showQrLoginDialog() {
        lifecycleScope.launch {
            try {
                val localIp = withContext(Dispatchers.IO) {
                    NetworkInterface.getNetworkInterfaces()?.asSequence()
                        ?.flatMap { it.inetAddresses.asSequence() }
                        ?.firstOrNull {
                            !it.isLoopbackAddress && it.hostAddress?.contains('.') == true
                        }?.hostAddress ?: return@withContext null
                }
                if (localIp == null) {
                    snackString("Could not detect local IP — check WiFi connection")
                    return@launch
                }
                val port = 8080 + (0..1000).random()
                val server = LocalRelayServer(port, localIp)
                withContext(Dispatchers.IO) { server.start() }

                val qrUrl = "http://$localIp:$port/auth"
                val qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${URLEncoder.encode(qrUrl, "UTF-8")}"

                val ctx = this@SettingsAccountActivity
                val iv = android.widget.ImageView(ctx).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    loadImage(qrImageUrl)
                    layoutParams = android.view.ViewGroup.LayoutParams(400, 400)
                }
                val tv = android.widget.TextView(ctx).apply {
                    text = "Scan with your phone (must be on same WiFi)"
                    gravity = android.view.Gravity.CENTER
                    setPadding(24, 0, 24, 0)
                }
                val browserBtn = com.google.android.material.button.MaterialButton(ctx).apply {
                    text = "Open Browser"
                    setOnClickListener { Anilist.loginIntent(ctx) }
                }
                val cancelBtn = com.google.android.material.button.MaterialButton(ctx).apply {
                    text = "Cancel"
                    setOnClickListener {
                        pollJob?.cancel(); pollJob = null
                        server.stop()
                    }
                }
                val ll = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(24, 24, 24, 24)
                    addView(iv)
                    addView(tv, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(browserBtn, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(cancelBtn, android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                customAlertDialog().apply {
                    setTitle("QR Login")
                    setCustomView(ll)
                    setNegButton("Close") {
                        pollJob?.cancel(); pollJob = null
                        server.stop()
                    }
                }.show()

                pollJob = lifecycleScope.launch {
                    while (isActive) {
                        delay(2000)
                        val token = withContext(Dispatchers.IO) {
                            try {
                                val s = java.net.Socket("127.0.0.1", port)
                                val req = "GET /api/poll HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
                                s.getOutputStream().write(req.toByteArray())
                                val resp = s.getInputStream().bufferedReader().readText()
                                s.close()
                                val body = resp.substringAfter("\r\n\r\n")
                                val json = org.json.JSONObject(body)
                                if (json.optString("status") == "authorized") json.optString("token", null) else null
                            } catch (_: Exception) { null }
                        }
                        if (token != null) {
                            Anilist.token = token
                            PrefManager.setVal(PrefName.AnilistToken, token)
                            server.stop()
                            snackString("Logged in!")
                            restartActivity()
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                snackString("QR login error: ${e.message}")
            }
        }
    }

    private fun restartActivity() {
        val intent = Intent(this, this.javaClass)
        finish()
        startActivity(intent)
    }

    fun reload() {
        snackString(getString(R.string.restart_app_extra))
    }
}