package com.ryanjames.lunar.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.ui.LunarTooltip

/**
 * A screen title with an optional italic subtitle. The title gets extra bottom spacing so the
 * descenders of the display face clear the subtitle line.
 */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val tokens = lunarTokens()
    Column(modifier = modifier) {
        Text(
            text = title,
            style = tokens.typography.title,
            color = tokens.colors.ink,
            modifier = Modifier
                .semantics { heading() }
                .padding(bottom = if (subtitle == null) 0.dp else tokens.spacing.sm),
        )
        subtitle?.let {
            Text(
                text = it,
                style = tokens.typography.subtitle,
                color = tokens.colors.muted,
            )
        }
    }
}

/** A letter-spaced capitals label for section headings, column headers and metadata keys. */
@Composable
fun SmallCapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = lunarTokens().colors.muted,
) {
    Text(
        text = text.uppercase(),
        style = lunarTokens().typography.label,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Single-line text that ellipsizes when it does not fit. When it is cut short, the full text is
 * available in a tooltip on hover or long-press.
 */
@Composable
fun TruncatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = lunarTokens().typography.body,
    color: Color = lunarTokens().colors.ink,
) {
    var truncated by remember(text) { mutableStateOf(false) }
    LunarTooltip(
        text = text,
        modifier = modifier,
        enabled = truncated,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> truncated = layout.hasVisualOverflow },
        )
    }
}
