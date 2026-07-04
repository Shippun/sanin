package ani.dantotsu.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.dantotsu.R

private val CORE_TABS = listOf("home", "anime", "discovery", "library")
private val ALL_ITEMS = listOf("home", "anime", "discovery", "library", "calendar", "avatar")
private val TAB_ICONS = mapOf(
    "home" to R.drawable.ic_round_home_24,
    "anime" to R.drawable.ic_round_movie_filter_24,
    "discovery" to R.drawable.ic_round_filter_list_24,
    "library" to R.drawable.ic_round_library_books_24,
    "calendar" to R.drawable.ic_round_calendar_today_24,
    "avatar" to R.drawable.ic_round_person_24
)
private val TAB_LABELS = mapOf(
    "home" to "Home",
    "anime" to "Anime",
    "discovery" to "Discovery",
    "library" to "Library"
)

@Composable
fun NavigationPills(
    viewModel: NavigationPillsViewModel,
    modifier: Modifier = Modifier,
    onCalendarClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isExpanded by viewModel.isExpanded.collectAsState()
    var containerFocused by remember { mutableStateOf(false) }
    var highlightIndex by remember { mutableStateOf(currentTab) }

    val pillHeight by animateDpAsState(
        targetValue = if (isExpanded) 56.dp else 48.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "pillHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(pillHeight)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 6.dp)
                .focusable()
                .onFocusChanged { containerFocused = it.isFocused }
                .navigationPillFocusEffect(containerFocused, "pulseglow")
                .onKeyEvent { event ->
                    if (containerFocused && event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.DirectionRight -> {
                                val next = (highlightIndex + 1).coerceAtMost(ALL_ITEMS.size - 1)
                                highlightIndex = next
                                if (ALL_ITEMS[next] in CORE_TABS) {
                                    val tabIdx = CORE_TABS.indexOf(ALL_ITEMS[next])
                                    if (tabIdx >= 0 && tabIdx != currentTab) {
                                        // highlight only, don't activate
                                    }
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                val prev = (highlightIndex - 1).coerceAtLeast(0)
                                highlightIndex = prev
                                true
                            }
                            Key.DirectionUp -> false
                            Key.DirectionDown -> false
                            Key.Enter, Key.DirectionCenter -> {
                                val item = ALL_ITEMS[highlightIndex]
                                when (item) {
                                    "calendar" -> onCalendarClick?.invoke()
                                    "avatar" -> onAvatarClick?.invoke()
                                    else -> {
                                        val tabIdx = CORE_TABS.indexOf(item)
                                        if (tabIdx >= 0) viewModel.setTab(tabIdx)
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ALL_ITEMS.forEachIndexed { index, item ->
                val isCoreTab = item in CORE_TABS
                val isHighlighted = highlightIndex == index
                val isActive = isCoreTab && CORE_TABS.indexOf(item) == currentTab

                val tabWidth by animateDpAsState(
                    targetValue = if (isExpanded && isCoreTab) 88.dp else if (isCoreTab) 48.dp else 44.dp,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
                    label = "itemWidth"
                )

                val shape = if (item == "avatar") CircleShape else RoundedCornerShape(50)

                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(if (item == "avatar") 40.dp else 48.dp)
                        .background(
                            color = when {
                                isActive -> Color.White.copy(alpha = 0.15f)
                                isHighlighted && isCoreTab -> Color.White.copy(alpha = 0.08f)
                                else -> Color.Transparent
                            },
                            shape = shape
                        )
                        .clickable {
                            highlightIndex = index
                            when (item) {
                                "calendar" -> onCalendarClick?.invoke()
                                "avatar" -> onAvatarClick?.invoke()
                                else -> {
                                    val tabIdx = CORE_TABS.indexOf(item)
                                    if (tabIdx >= 0) viewModel.setTab(tabIdx)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = TAB_ICONS[item] ?: R.drawable.ic_round_home_24),
                        contentDescription = item,
                        tint = when {
                            isActive -> Color(0xFF87CEEB)
                            isHighlighted && !isCoreTab -> Color(0xFF87CEEB).copy(alpha = 0.7f)
                            else -> Color.White.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(if (isExpanded && isCoreTab) 22.dp else 20.dp)
                    )
                }
            }
        }
    }
}