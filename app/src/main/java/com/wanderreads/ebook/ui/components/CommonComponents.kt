package com.wanderreads.ebook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wanderreads.ebook.ui.theme.AppAnimationSpec

/**
 * A stylized primary button with hover and press animations
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val shadowElevation by animateFloatAsState(
        targetValue = when {
            isPressed -> 0f
            isHovered -> 8f
            else -> 4f
        },
        animationSpec = AppAnimationSpec.subtleHoverEffect,
        label = "Button Shadow Animation"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier.shadow(if (enabled) shadowElevation.dp else 0.dp, shape),
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        if (icon != null && !loading) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(text = text)
    }
}

/**
 * A stylized secondary button with hover and press animations
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val alpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0.9f,
        animationSpec = AppAnimationSpec.subtleHoverEffect,
        label = "Button Alpha Animation"
    )
    
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) alpha else 0.6f),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(text = text)
    }
}

/**
 * A stylized outlined button with hover animation
 */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val borderWidth by animateFloatAsState(
        targetValue = if (isHovered) 2f else 1f,
        animationSpec = AppAnimationSpec.subtleHoverEffect,
        label = "Border Animation"
    )
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        border = BorderStroke(
            width = borderWidth.dp,
            color = if (enabled) {
                if (isHovered) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.outline
            } else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(text = text)
    }
}

/**
 * Section header with optional action button
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            if (actionText != null && onActionClick != null) {
                OutlinedButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = CircleShape
                ) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        Divider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Stylized message card for errors, warnings, info, etc.
 */
@Composable
fun MessageCard(
    message: String,
    messageType: MessageType = MessageType.INFO,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(true) }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(animationSpec = tween(durationMillis = 300))
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = messageType.backgroundColor(),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = messageType.icon(),
                    contentDescription = null,
                    tint = messageType.contentColor(),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageType.contentColor(),
                    modifier = Modifier.weight(1f)
                )
                
                if (onDismiss != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            visible = false
                            onDismiss()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(8.dp),
                        border = BorderStroke(1.dp, messageType.contentColor().copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = messageType.contentColor()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state display for when data is not available
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(24.dp))
                
                AppPrimaryButton(
                    text = actionText,
                    onClick = onActionClick
                )
            }
        }
    }
}

enum class MessageType {
    INFO, WARNING, ERROR, SUCCESS;
    
    @Composable
    fun backgroundColor(): Color {
        return when (this) {
            INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            SUCCESS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        }
    }
    
    @Composable
    fun contentColor(): Color {
        return when (this) {
            INFO -> MaterialTheme.colorScheme.onPrimaryContainer
            WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            ERROR -> MaterialTheme.colorScheme.onErrorContainer
            SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    
    fun icon(): ImageVector {
        return when (this) {
            INFO -> Icons.Default.ArrowForward
            WARNING, ERROR -> Icons.Default.Warning
            SUCCESS -> Icons.Default.ArrowForward
        }
    }
} 