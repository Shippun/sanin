package ani.sanin.settings

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.api.NotificationType
import ani.sanin.databinding.ActivitySettingsNotificationsBinding
import ani.sanin.initActivity
import ani.sanin.media.Media
import ani.sanin.navBarHeight
import ani.sanin.notifications.TaskScheduler
import ani.sanin.notifications.anilist.AnilistNotificationWorker
import ani.sanin.notifications.comment.CommentNotificationWorker
import ani.sanin.notifications.subscription.SubscriptionHelper
import ani.sanin.notifications.subscription.SubscriptionNotificationWorker
import ani.sanin.openSettings
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.util.FocusEffectUtil
import ani.sanin.themes.ThemeManager
import ani.sanin.toast
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SettingsNotificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsNotificationsBinding
    private var isImportingLists = false
    private var isImportingStatuses = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            var curTime = PrefManager.getVal<Int>(PrefName.SubscriptionNotificationInterval)
            val timeNames = SubscriptionNotificationWorker.checkIntervals.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }.toTypedArray()
            settingsNotificationsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            notificationSettingsBack.isFocusable = true
            FocusEffectUtil.applyFocusListener(notificationSettingsBack)
            notificationSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            val aTimeNames = AnilistNotificationWorker.checkIntervals.map { it.toInt() }
            val aItems = aTimeNames.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }
            val cTimeNames = CommentNotificationWorker.checkIntervals.map { it.toInt() }
            val cItems = cTimeNames.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }
            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.subscriptions_checking_time_s,
                            timeNames[curTime]
                        ),
                        desc = getString(R.string.subscriptions_info),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle(R.string.subscriptions_checking_time)
                                singleChoiceItems(timeNames, curTime) { i ->
                                    curTime = i
                                    it.settingsTitle.text = getString(R.string.subscriptions_checking_time_s, timeNames[i])
                                    PrefManager.setVal(PrefName.SubscriptionNotificationInterval, curTime)
                                    TaskScheduler.create(context, PrefManager.getVal(PrefName.UseAlarmManager)).scheduleAllTasks(context)
                                }
                                show()
                            }
                        },
                        onLongClick = {
                            TaskScheduler.create(
                                context, PrefManager.getVal(PrefName.UseAlarmManager)
                            ).scheduleAllTasks(context)
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.view_subscriptions),
                        desc = getString(R.string.view_subscriptions_desc),
                        icon = R.drawable.ic_round_search_24,
                        onClick = {
                            val subscriptions = SubscriptionHelper.getSubscriptions()
                            SubscriptionsBottomDialog.newInstance(subscriptions).show(
                                supportFragmentManager,
                                "subscriptions"
                            )
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.import_anilist_lists_to_subscriptions),
                        desc = getString(R.string.import_anilist_lists_to_subscriptions_desc),
                        icon = R.drawable.ic_round_playlist_add_24,
                        onClick = {
                            if (isImportingLists) {
                                toast(getString(R.string.loading))
                                return@Settings
                            }
                            isImportingLists = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val userId = (Anilist.userid
                                        ?: PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull())
                                    if (userId == null) {
                                        withContext(Dispatchers.Main) { toast(getString(R.string.login_to_anilist_first)) }
                                        return@launch
                                    }

                                    val animeLists = Anilist.query.getMediaLists(true, userId)
                                    val mangaLists = Anilist.query.getMediaLists(false, userId)

                                    val selectableLists = linkedMapOf<String, ArrayList<Media>>()
                                    fun addSelectableLists(prefix: String, lists: MutableMap<String, ArrayList<Media>>) {
                                        lists.forEach { (name, media) ->
                                            if (name != "All" && name != "Favourites" && media.isNotEmpty()) {
                                                selectableLists["$prefix • $name"] = media
                                            }
                                        }
                                    }
                                    addSelectableLists(getString(R.string.anime), animeLists)
                                    addSelectableLists(getString(R.string.manga), mangaLists)

                                    if (selectableLists.isEmpty()) {
                                        withContext(Dispatchers.Main) { toast(getString(R.string.no_lists_available_to_import)) }
                                        return@launch
                                    }

                                    val titles = selectableLists.keys.toTypedArray()
                                    val selected = BooleanArray(titles.size) { false }
                                    withContext(Dispatchers.Main) {
                                        context.customAlertDialog().apply {
                                            setTitle(R.string.select_lists_to_import)
                                            multiChoiceItems(titles, selected) {}
                                            setPosButton(R.string.import_action) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val existingIds = SubscriptionHelper.getSubscriptions().keys.toMutableSet()
                                                    var importedCount = 0
                                                    titles.forEachIndexed { index, title ->
                                                        if (selected[index]) {
                                                            selectableLists[title]?.forEach { media ->
                                                                if (!existingIds.contains(media.id)) {
                                                                    existingIds.add(media.id)
                                                                    importedCount++
                                                                    SubscriptionHelper.saveSubscription(media, true)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        toast(
                                                            getString(
                                                                R.string.imported_subscriptions_count,
                                                                importedCount
                                                            )
                                                        )
                                                        isImportingLists = false
                                                    }
                                                }
                                            }
                                            setNegButton(R.string.cancel) { isImportingLists = false }
                                            show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { isImportingLists = false }
                                }
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.import_anilist_statuses_to_subscriptions),
                        desc = getString(R.string.import_anilist_statuses_to_subscriptions_desc),
                        icon = R.drawable.ic_round_playlist_add_24,
                        onClick = {
                            if (isImportingStatuses) {
                                toast(getString(R.string.loading))
                                return@Settings
                            }
                            isImportingStatuses = true
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val userId = (Anilist.userid
                                        ?: PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull())
                                    if (userId == null) {
                                        withContext(Dispatchers.Main) { toast(getString(R.string.login_to_anilist_first)) }
                                        return@launch
                                    }

                                    val animeLists = Anilist.query.getMediaLists(true, userId)
                                    val mangaLists = Anilist.query.getMediaLists(false, userId)
                                    val animeAll = animeLists["All"] ?: arrayListOf()
                                    val mangaAll = mangaLists["All"] ?: arrayListOf()

                                    if (animeAll.isEmpty() && mangaAll.isEmpty()) {
                                        withContext(Dispatchers.Main) { toast(getString(R.string.no_lists_available_to_import)) }
                                        return@launch
                                    }

                                    val statusKeys = resources.getStringArray(R.array.status)
                                    val animeStatuses = resources.getStringArray(R.array.status_anime)
                                    val mangaStatuses = resources.getStringArray(R.array.status_manga)
                                    val count = minOf(
                                        statusKeys.size,
                                        animeStatuses.size,
                                        mangaStatuses.size
                                    )
                                    val titles = ArrayList<String>(count * 2)
                                    val statusMeta = ArrayList<Pair<Boolean, String>>(count * 2)
                                    repeat(count) { index ->
                                        titles.add("${getString(R.string.anime)} • ${animeStatuses[index]}")
                                        statusMeta.add(true to statusKeys[index])
                                        titles.add("${getString(R.string.manga)} • ${mangaStatuses[index]}")
                                        statusMeta.add(false to statusKeys[index])
                                    }
                                    val selected = BooleanArray(titles.size) { false }

                                    withContext(Dispatchers.Main) {
                                        context.customAlertDialog().apply {
                                            setTitle(R.string.select_statuses_to_import)
                                            multiChoiceItems(titles.toTypedArray(), selected) {}
                                            setPosButton(R.string.import_action) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val animeSelected = mutableSetOf<String>()
                                                    val mangaSelected = mutableSetOf<String>()
                                                    statusMeta.forEachIndexed { index, (isAnime, status) ->
                                                        if (selected[index]) {
                                                            if (isAnime) animeSelected.add(status)
                                                            else mangaSelected.add(status)
                                                        }
                                                    }
                                                    val existingIds =
                                                        SubscriptionHelper.getSubscriptions().keys.toMutableSet()
                                                    var importedCount = 0
                                                    fun importMedia(
                                                        list: List<Media>,
                                                        statuses: Set<String>
                                                    ) {
                                                        list.forEach { media ->
                                                            if (media.userStatus != null &&
                                                                statuses.contains(media.userStatus)
                                                            ) {
                                                                if (!existingIds.contains(media.id)) {
                                                                    existingIds.add(media.id)
                                                                    importedCount++
                                                                    SubscriptionHelper.saveSubscription(media, true)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    importMedia(animeAll, animeSelected)
                                                    importMedia(mangaAll, mangaSelected)
                                                    withContext(Dispatchers.Main) {
                                                        toast(
                                                            getString(
                                                                R.string.imported_subscriptions_count,
                                                                importedCount
                                                            )
                                                        )
                                                        isImportingStatuses = false
                                                    }
                                                }
                                            }
                                            setNegButton(R.string.cancel) { isImportingStatuses = false }
                                            show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { isImportingStatuses = false }
                                }
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.anilist_notification_filters),
                        desc = getString(R.string.anilist_notification_filters_desc),
                        icon = R.drawable.ic_anilist,
                        onClick = {
                            val types = NotificationType.entries.map { it.name }
                            val filteredTypes =
                                PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                                    .toMutableSet()
                            val selected = types.map { filteredTypes.contains(it) }.toBooleanArray()
                            context.customAlertDialog().apply {
                                 setTitle(R.string.anilist_notification_filters)
                                 multiChoiceItems(
                                    types.map { name ->
                                        name.replace("_", " ").lowercase().replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                    } }.toTypedArray(),
                                    selected
                                ) { updatedSelected ->
                                types.forEachIndexed { index, type ->
                                    if (updatedSelected[index]) {
                                        filteredTypes.add(type)
                                    } else {
                                        filteredTypes.remove(type)
                                    }
                                }
                                    PrefManager.setVal(PrefName.AnilistFilteredTypes, filteredTypes)
                                }
                                show()
                            }
                        }

                    ),
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.anilist_notifications_checking_time,
                            aItems[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
                        ),
                        desc = getString(R.string.anilist_notifications_checking_time_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                 setTitle(R.string.subscriptions_checking_time)
                                 singleChoiceItems(
                                    aItems.toTypedArray(),
                                    PrefManager.getVal<Int>(PrefName.AnilistNotificationInterval)
                                ) { i ->
                                    PrefManager.setVal(PrefName.AnilistNotificationInterval, i)
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.anilist_notifications_checking_time,
                                            aItems[i]
                                        )
                                    TaskScheduler.create(
                                        context, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(context)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.comment_notification_checking_time,
                            cItems[PrefManager.getVal(PrefName.CommentNotificationInterval)]
                        ),
                        desc = getString(R.string.comment_notification_checking_time_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                 setTitle(R.string.subscriptions_checking_time)
                                 singleChoiceItems(
                                    cItems.toTypedArray(),
                                    PrefManager.getVal<Int>(PrefName.CommentNotificationInterval)
                                ) {  i ->
                                    PrefManager.setVal(PrefName.CommentNotificationInterval, i)
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.comment_notification_checking_time,
                                            cItems[i]
                                        )
                                    TaskScheduler.create(
                                        context, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(context)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.notification_for_checking_subscriptions),
                        desc = getString(R.string.notification_for_checking_subscriptions_desc),
                        icon = R.drawable.ic_round_notifications_active_24,
                        isChecked = PrefManager.getVal(PrefName.SubscriptionCheckingNotifications),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(
                                PrefName.SubscriptionCheckingNotifications,
                                isChecked
                            )
                        },
                        onLongClick = {
                            openSettings(context, null)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.subscription_prompt_at_end),
                        desc = getString(R.string.subscription_prompt_at_end_desc),
                        icon = R.drawable.ic_round_notifications_active_24,
                        isChecked = PrefManager.getVal(PrefName.SubscriptionPromptAtEnd),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(
                                PrefName.SubscriptionPromptAtEnd,
                                isChecked
                            )
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.notification_popup),
                        desc = getString(R.string.notification_popup_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationPopup),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(
                                PrefName.NotificationPopup,
                                isChecked
                            )
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "New Episode Airing",
                        desc = "Show popup when a new episode airs",
                        icon = R.drawable.ic_round_new_releases_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationEpisodeAiring),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NotificationEpisodeAiring, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "New Comment Reply",
                        desc = "Show popup when someone replies to your comment or activity",
                        icon = R.drawable.ic_round_comment_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationNewComment),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NotificationNewComment, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Completed Episode",
                        desc = "Show popup when an episode is marked complete",
                        icon = R.drawable.ic_round_playlist_add_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationCompletedEpisode),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NotificationCompletedEpisode, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "Completed Anime",
                        desc = "Show popup when an anime is marked complete",
                        icon = R.drawable.ic_round_playlist_play_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationCompletedAnime),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NotificationCompletedAnime, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "New Follower",
                        desc = "Show popup when someone follows you",
                        icon = R.drawable.ic_round_person_24,
                        isChecked = PrefManager.getVal(PrefName.NotificationNewFollower),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.NotificationNewFollower, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = "List Status Notification",
                        desc = "Show a popup when anime list status changes (e.g. Watching -> Completed)",
                        icon = R.drawable.ic_round_playlist_play_24,
                        isChecked = PrefManager.getVal(PrefName.ListStatusNotification),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.ListStatusNotification, isChecked)
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.use_alarm_manager_reliable),
                        desc = getString(R.string.use_alarm_manager_reliable_desc),
                        icon = R.drawable.ic_anilist,
                        isChecked = PrefManager.getVal(PrefName.UseAlarmManager),
                        switch = { isChecked, view ->
                            if (isChecked) {
                                context.customAlertDialog().apply {
                                     setTitle(R.string.use_alarm_manager)
                                     setMessage(R.string.use_alarm_manager_confirm)
                                     setPosButton(R.string.use) {
                                        PrefManager.setVal(PrefName.UseAlarmManager, true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            if (!(getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
                                                val intent =
                                                    Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM")
                                                startActivity(intent)
                                                view.settingsButton.isChecked = true
                                            }
                                        }
                                    }
                                    setNegButton(R.string.cancel) {
                                        view.settingsButton.isChecked = false
                                        PrefManager.setVal(PrefName.UseAlarmManager, false)
                                    }
                                    show()
                                }
                            } else {
                                PrefManager.setVal(PrefName.UseAlarmManager, false)
                                TaskScheduler.create(context, true).cancelAllTasks()
                                TaskScheduler.create(context, false)
                                    .scheduleAllTasks(context)
                            }
                        },
                    ),
                )
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }
}
