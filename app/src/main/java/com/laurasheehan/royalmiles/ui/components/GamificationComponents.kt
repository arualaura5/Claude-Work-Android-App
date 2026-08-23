package com.laurasheehan.royalmiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.core.gamification.Level
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.ComebackGoldSoft
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleLight
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim

private val xpShimmerColors = listOf(RoyalPurple, BlushPink, ComebackGold, RoyalPurpleLight, RoyalPurple)

@Composable
fun XpBar(totalXp: Int, level: Level, xpToNextLevel: Int?, modifier: Modifier = Modifier) {
    val progress = if (xpToNextLevel == null) {
        1f
    } else {
        val span = (totalXp + xpToNextLevel) - level.xpRequired
        if (span <= 0) 1f else ((totalXp - level.xpRequired).toFloat() / span).coerceIn(0f, 1f)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Lvl ${level.number} · ${level.title}", style = MaterialTheme.typography.titleMedium)
            Text("$totalXp XP", style = MaterialTheme.typography.titleMedium, color = ComebackGold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(ShimmerSilverDim.copy(alpha = 0.25f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(14.dp)
                    .shimmer(xpShimmerColors),
            )
        }
        Text(
            text = if (xpToNextLevel != null) "$xpToNextLevel XP to next level" else "Max level reached — legend status",
            style = MaterialTheme.typography.labelSmall,
            color = ShimmerSilverDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun StreakChip(currentStreak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                Brush.horizontalGradient(listOf(BlushPink, ComebackGold)),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color.White)
        Text(
            text = if (currentStreak == 1) "1 day streak" else "$currentStreak day streak",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun BadgeChip(badge: Badge, unlocked: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .let {
                if (unlocked) it.shimmer(listOf(ComebackGoldSoft, ComebackGold, BlushPink, ComebackGoldSoft))
                else it.background(Color.Transparent)
            }
            .border(
                width = 1.dp,
                color = if (unlocked) ComebackGold else ShimmerSilverDim.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
            contentDescription = null,
            tint = if (unlocked) Color(0xFF3A2A00) else ShimmerSilverDim,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) Color(0xFF3A2A00) else ShimmerSilverDim,
        )
    }
}
