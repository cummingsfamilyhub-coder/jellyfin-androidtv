package org.jellyfin.androidtv.ui.mobile

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.composable.AsyncImage

@Composable
internal fun VesperProfileAvatar(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3E2C79), Color(0xFF1B3457))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            name.take(1).uppercase(),
            style = TextStyle(
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                url = imageUrl,
                scaleType = ImageView.ScaleType.CENTER_CROP,
            )
        }
    }
}
