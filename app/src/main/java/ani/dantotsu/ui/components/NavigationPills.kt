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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.dantotsu.R

private val TAB_ORDER = listOf("home", "anime", "discovery", "library")
private val TAB_ICONS = mapOf(
    "home" to R.drawable.ic_round_home_24,
    "anime" to R.drawable.ic_round_movie_filter_24,
    "discovery" to R.drawable.ic_round_filter_list_24,
    "library" to R.drawable.ic_round_library_books_24
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
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isExpanded by viewModel.isExpanded.collectAsState()

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
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TAB_ORDER.forEachIndexed { index, tab ->
                val isActive = currentTab == index
                NavigationPill(
                    tab = tab,
                    label = TAB_LABELS[tab] ?: tab,
                    isActive = isActive,
                    isExpanded = isExpanded,
                    onClick = { viewModel.setTab(index) }
                )
            }
        }
    }
}

@Composable
fun NavigationPill(
    tab: String,
    label: String,
    isActive: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val iconRes = TAB_ICONS[tab] ?: R.drawable.ic_round_home_24
    var isFocused by remember { mutableStateOf(false) }

    val pillWidth by animateDpAsState(
        targetValue = if (isExpanded) 88.dp else 48.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessVeryLow),
        label = "pillWidth"
    )

    Box(
        modifier = Modifier
            .width(pillWidth)
            .fillMaxHeight()
            .background(
                color = if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) Color(0xFF87CEEB).copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .navigationPillFocusEffect(isFocused, "glow")
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (isActive) Color(0xFF87CEEB) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isActive) Color(0xFF87CEEB) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
