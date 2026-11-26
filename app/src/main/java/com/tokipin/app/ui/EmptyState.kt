package com.tokipin.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tokipin.app.R
import kotlin.math.roundToInt

/**
 * Shared empty state for an empty day and an empty month in 기록한 날 (Figma 211-2990): the peek
 * rabbit behind a small glass card, as one static picture. Same rules as iOS, in dp.
 */
object EmptyStateSpec {
    const val GROUP_W = 172f
    const val GROUP_H = 120f
    const val RABBIT = 90f
    const val CARD_H = 69f
    /** The rabbit's 90×90 box inside the 172×120 group (its ears reach the top of the group). */
    const val RABBIT_X = 72f
    const val RABBIT_Y = -28f
    /** Picture centre at 30% of the height between the date/month band and the bottom buttons. */
    const val CENTER_FRACTION = 0.30f
    const val WIDTH_FRACTION = 0.44f

    /** 44% of the page width, rounded, at most 172; height keeps 172:120. */
    fun width(pageWidthDp: Float): Float = minOf(GROUP_W, (pageWidthDp * WIDTH_FRACTION).roundToInt().toFloat())

    /** Top of the picture inside an area [availableDp] tall, kept inside an 8dp margin on short screens. */
    fun top(availableDp: Float, heightDp: Float, margin: Float = 8f): Float {
        val wanted = availableDp * CENTER_FRACTION - heightDp / 2
        return wanted.coerceIn(margin, maxOf(margin, availableDp - heightDp - margin))
    }
}

/** Space the floating 오늘 / + bar takes at the bottom of the browse screen. */
@Composable
fun bottomBarReserve(): Dp {
    val density = LocalDensity.current
    return 80.dp + with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
}

@Composable
fun EmptyRabbitCard(width: Dp, modifier: Modifier = Modifier) {
    val s = width.value / EmptyStateSpec.GROUP_W
    val h = width * (EmptyStateSpec.GROUP_H / EmptyStateSpec.GROUP_W)
    Box(modifier.size(width, h)) {
        Image(
            painterResource(R.drawable.ic_peek_rabbit),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFFAFC7AC)),
            modifier = Modifier
                .offset((EmptyStateSpec.RABBIT_X * s).dp, (EmptyStateSpec.RABBIT_Y * s).dp)
                .size((EmptyStateSpec.RABBIT * s).dp),
        )
        val shape = RoundedCornerShape((18 * s).dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(width, (EmptyStateSpec.CARD_H * s).dp)
                .background(Brush.linearGradient(listOf(Color(0xFF536660), Color(0xFF3A4842), Color(0xFF2F3A35))), shape)
                .border(1.dp, Color(0x40E4ECE2), shape),
        )
    }
}

/**
 * Places [EmptyRabbitCard] centred, its centre at 30% of the height between the top of this area
 * (directly under the date or month band) and the top of the bottom buttons. [description] is
 * what TalkBack reads; nothing is written on screen.
 */
@Composable
fun EmptyStateArea(description: String, modifier: Modifier = Modifier, tag: String = "emptyState") {
    val reserve = bottomBarReserve()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = EmptyStateSpec.width(maxWidth.value)
        val h = w * EmptyStateSpec.GROUP_H / EmptyStateSpec.GROUP_W
        val top = EmptyStateSpec.top((maxHeight - reserve).value, h)
        EmptyRabbitCard(
            w.dp,
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = top.dp)
                .testTag(tag)
                .semantics { contentDescription = description },
        )
    }
}
