package com.example.ebook.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Common spacing values used throughout the app
 */
object AppSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/**
 * Common corner radius values used throughout the app
 */
object AppCornerRadius {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val circle = 999.dp // Use this for circles
}

/**
 * Common elevation values used throughout the app
 */
object AppElevation {
    val none = 0.dp
    val xs = 1.dp
    val sm = 2.dp
    val md = 4.dp
    val lg = 8.dp
    val xl = 16.dp
}

/**
 * A stylized section card with optional header
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    elevation: Dp = AppElevation.sm,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(AppCornerRadius.md)
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(AppCornerRadius.md)
    ) {
        if (title != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        top = AppSpacing.md,
                        bottom = AppSpacing.sm
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * A stylized header for section or page titles
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = AppSpacing.md,
                vertical = AppSpacing.lg
            ),
            contentAlignment = Alignment.Center
        ) {
            if (subtitle != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AppSpacing.xs)
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A simple divider with some spacing
 */
@Composable
fun SpacedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    verticalPadding: Dp = AppSpacing.md
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        color = color,
        content = { Box(modifier = Modifier.height(thickness)) }
    )
}

/**
 * A badge for showing status or small pieces of information
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    showBorder: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppCornerRadius.circle),
        color = backgroundColor,
        contentColor = contentColor,
        border = if (showBorder) {
            BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
        } else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = AppSpacing.sm,
                vertical = AppSpacing.xxs
            )
        )
    }
}

/**
 * A custom tag for books or other items
 */
@Composable
fun BookTag(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppCornerRadius.xs),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = AppSpacing.xs,
                vertical = AppSpacing.xxs
            )
        )
    }
} 