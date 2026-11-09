package com.ryanjames.lunar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.composeapp.generated.resources.lilypond_logo
import com.ryanjames.lunar.composeapp.generated.resources.pdf_logo
import com.ryanjames.lunar.library.model.ScoreViewerSupport
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.viewerSupport
import org.jetbrains.compose.resources.painterResource

const val ScoreThumbnailImageTag = "ScoreThumbnailImage"
const val ScoreThumbnailPlaceholderTag = "ScoreThumbnailPlaceholder"

@Composable
fun ScoreThumbnail(
    item: SheetMusicItem,
    cache: ScoreThumbnailCache<ImageBitmap>,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id, cache) {
        bitmap = cache.get(item)
    }

    val resolved = bitmap
    if (resolved != null) {
        Image(
            bitmap = resolved,
            contentDescription = null,
            modifier = modifier
                .testTag(ScoreThumbnailImageTag)
                .semantics { contentDescription = item.title },
            contentScale = contentScale,
        )
    } else {
        ThumbnailPlaceholder(
            item = item,
            modifier = modifier
                .testTag(ScoreThumbnailPlaceholderTag)
                .semantics { contentDescription = item.title },
        )
    }
}

@Composable
private fun ThumbnailPlaceholder(
    item: SheetMusicItem,
    modifier: Modifier,
) {
    val logo = when (item.viewerSupport) {
        ScoreViewerSupport.PDF -> Res.drawable.pdf_logo
        ScoreViewerSupport.LILYPOND -> Res.drawable.lilypond_logo
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            alpha = 0.6f,
        )
    }
}
