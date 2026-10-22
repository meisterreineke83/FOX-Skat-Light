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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * One card as a framed tile showing suit symbol and rank, e.g. "♥10".
 * Selected cards are drawn inverted, cards that cannot be played are dimmed.
 */
@Composable
fun CardTile(
    card: Card?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LightThemeTokens.colors
    val frame = if (dimmed) colors.contentSecondary else colors.content
    Box(
        modifier = modifier
            .height(3.5f.gridUnitsAsDp())
            .let { if (card != null) it.border(1.dp, frame) else it }
            .background(if (selected) colors.content else colors.background)
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (card != null) {
            LightText(
                text = card.toString(),
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                lighten = dimmed && !selected,
                color = if (selected) colors.background else null,
                maxLines = 1,
            )
        }
    }
}

/** The hand in rows of [CARDS_PER_ROW] tiles. */
@Composable
fun HandView(
    cards: List<Card>,
    selected: Set<Card>,
    playable: (Card) -> Boolean,
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

/** A labelled card slot in the trick area, e.g. "LINKS" above the card that seat played. */
@Composable
fun TrickSlot(label: String, card: Card?, highlight: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LightText(text = label, variant = LightTextVariant.Detail, lighten = !highlight, maxLines = 1)
        CardTile(
            card = card,
            selected = highlight && card != null,
            modifier = Modifier
                .width(5f.gridUnitsAsDp())
                .padding(top = 0.25f.gridUnitsAsDp()),
        )
    }
}
