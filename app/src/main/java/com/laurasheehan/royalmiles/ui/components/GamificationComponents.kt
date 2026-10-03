package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.ComebackGoldSoft

/**
 * A badge lights up once, gold and shimmering, while it's new (and in its celebration), then
 * settles into the theme's own quieter colours. Locked badges are an outline with a padlock.
 */
@Composable
fun BadgeChip(badge: Badge, unlocked: Boolean, modifier: Modifier = Modifier, fresh: Boolean = false) {
    val glowing = unlocked && fresh
    val ink = when {
        glowing -> Color(0xFF3A2A00)
        unlocked -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .let {
                when {
                    glowing -> it.shimmer(listOf(ComebackGoldSoft, ComebackGold, BlushPink, ComebackGoldSoft))
                    unlocked -> it.background(MaterialTheme.colorScheme.primaryContainer)
                    else -> it.background(Color.Transparent)
                }
            }
            .border(
                width = 1.dp,
                color = when {
                    glowing -> ComebackGold
                    unlocked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(16.dp),
        )
        Text(text = badge.title, style = MaterialTheme.typography.labelSmall, color = ink)
    }
}
