package io.github.foxskat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import io.github.foxskat.core.Card

/** Height of one row in lists, in LP3 grid units. */
const val LIST_ROW_UNITS = 4f

/** Cards per row in the hand; twelve cards (after picking up the Skat) fill two rows. */
const val CARDS_PER_ROW = 6

/**
 * Variation selector that asks for the plain text glyph of a suit symbol instead of the wide
 * colour emoji, so that "♣10" fits on a card.
 */
private const val TEXT_STYLE = "\uFE0E"

/** Height of a card tile in grid units. */
const val CARD_HEIGHT_UNITS = 5f

/**
 * Common frame for every screen: Light theme, top bar with back button,
 * content area and an optional bottom bar.
 */
@Composable
fun ToolScaffold(
    title: String?,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    rightButton: LightTopBarButton? = null,
    bottomBar: List<LightBottomBarItem?>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val themeColors by LightThemeController.colors.collectAsState()
    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            LightTopBar(
                leftButton = onBack?.let { LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = it) },
                center = when {
                    title == null -> null
                    subtitle.isNullOrBlank() -> LightTopBarCenter.Text(title)
                    else -> LightTopBarCenter.TwoLineDetail(title, subtitle)
                },
                rightButton = rightButton,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = content,
            )
            if (bottomBar != null) {
                LightBottomBar(
                    items = bottomBar,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

/** A list row with a main line and an optional lighter second line. */
@Composable
fun TwoLineRow(
    title: String,
    detail: String?,
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LIST_ROW_UNITS.gridUnitsAsDp())
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it }
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            LightText(
                text = title,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                LightText(
                    text = detail,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            LightText(text = trailing, variant = LightTextVariant.Copy)
        }
    }
}

/** Upper-case menu entry like in LightOS, with an optional value on the right. */
@Composable
fun MenuItem(label: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = label, variant = LightTextVariant.Copy, lighten = !enabled)
        Spacer(modifier = Modifier.weight(1f))
        if (value != null) {
            LightText(text = value, variant = LightTextVariant.Copy, lighten = true)
        }
    }
}

/** Thin full-width line to separate sections. */
@Composable
fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp())
            .height(1.dp)
            .background(LightThemeTokens.colors.content),
    )
}

/** Lighter explanatory text. */
@Composable
fun HintText(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Paragraph,
        lighten = true,
        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
    )
}

/** Corner radius of a card tile in grid units. */
private const val CARD_CORNER_UNITS = 0.4f

/**
 * One card as a framed tile with rounded corners showing suit symbol and rank, e.g. "♥10".
 * Selected cards are drawn inverted, cards that cannot be played are dimmed, and trumps
 * carry a solid bar along the bottom edge.
 */
@Composable
fun CardTile(
    card: Card?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    trump: Boolean = false,
    variant: LightTextVariant = LightTextVariant.Subheading,
    onClick: (() -> Unit)? = null,
) {
    val colors = LightThemeTokens.colors
    val frame = if (dimmed) colors.contentSecondary else colors.content
    val shape = RoundedCornerShape(CARD_CORNER_UNITS.gridUnitsAsDp())
    Box(
        modifier = modifier
            .height(CARD_HEIGHT_UNITS.gridUnitsAsDp())
            .clip(shape)
            .let { if (card != null) it.border(1.5.dp, frame, shape) else it }
            .background(if (selected) colors.content else colors.background)
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (card != null) {
            LightText(
                text = card.suit.symbol + TEXT_STYLE + card.rank.label,
                variant = variant,
                align = TextAlign.Center,
                lighten = dimmed && !selected,
                color = if (selected) colors.background else null,
                maxLines = 1,
            )
            if (trump) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(0.5f.gridUnitsAsDp())
                        .background(if (selected) colors.background else frame),
                )
            }
        }
    }
}

/** The hand in rows of [CARDS_PER_ROW] tiles. */
@Composable
fun HandView(
    cards: List<Card>,
    selected: Set<Card>,
    playable: (Card) -> Boolean,
    trump: (Card) -> Boolean,
    onTap: ((Card) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.5f.gridUnitsAsDp()),
    ) {
        for (row in cards.chunked(CARDS_PER_ROW)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.25f.gridUnitsAsDp())) {
                for (i in 0 until CARDS_PER_ROW) {
                    val card = row.getOrNull(i)
                    CardTile(
                        card = card,
                        selected = card != null && card in selected,
                        dimmed = card != null && !playable(card),
                        trump = card != null && trump(card),
                        onClick = if (card != null && onTap != null) ({ onTap(card) }) else null,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 0.25f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

/** What a seat shows in the middle of the table: a played card, a word said in bidding, or nothing. */
sealed interface SeatSlot {
    data class Played(val card: Card?, val wonTrick: Boolean) : SeatSlot
    data class Word(val text: String) : SeatSlot
}

/**
 * One player at the table: the name (inverted for the declarer, underlined while it is
 * that player's turn), below it the slot and a short detail such as "3 Stiche" or "Vorhand".
 */
@Composable
fun SeatView(
    name: String,
    declarer: Boolean,
    active: Boolean,
    slot: SeatSlot,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CARD_CORNER_UNITS.gridUnitsAsDp()))
                .background(if (declarer) colors.content else colors.background)
                .padding(horizontal = 0.5f.gridUnitsAsDp()),
        ) {
            LightText(
                text = name.uppercase(),
                variant = LightTextVariant.Paragraph,
                underline = active,
                color = if (declarer) colors.background else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (slot) {
            is SeatSlot.Played -> CardTile(
                card = slot.card,
                selected = slot.wonTrick && slot.card != null,
                variant = LightTextVariant.Heading,
                modifier = Modifier
                    .width(6f.gridUnitsAsDp())
                    .padding(top = 0.25f.gridUnitsAsDp())
                    .let { if (slot.card == null) it.dashedSlot(colors.contentSecondary, CARD_CORNER_UNITS.gridUnitsAsDp()) else it },
            )
            is SeatSlot.Word -> LightText(
                text = slot.text,
                variant = LightTextVariant.Subheading,
                align = TextAlign.Center,
                maxLines = 1,
            )
        }
        LightText(text = detail, variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
    }
}

/** Faint outline for an empty place on the table where a card will land. */
private fun Modifier.dashedSlot(color: Color, corner: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
    )
}
