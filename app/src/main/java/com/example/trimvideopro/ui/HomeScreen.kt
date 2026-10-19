package com.example.trimvideopro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trimvideopro.localization.tool
import com.example.trimvideopro.localization.toolTitle
import com.example.trimvideopro.localization.LocalAppStrings
import com.example.ui.theme.AmberAccent


/**
 * Launcher page: a hero banner plus a 2x2 grid of tool cards.
 *
 * Tapping a card (including its title) opens that tool on its own page.
 */
@Composable
fun HomeScreen(
    exportedClipCount: Int,
    onOpenTool: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(colors.primary, colors.tertiary)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Created by Moeun💞",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = strings.homeGreeting,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = strings.homeSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Section label
        Text(
            text = strings.homeTools,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        // 2x2 navigation grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ContentCut,
                    title = strings.tabTrim,
                    description = strings.featureTrimDescription,
                    accent = colors.primary,
                    testTag = "home_card_trim",
                    onClick = { onOpenTool(AppScreen.TRIM) }
                )
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ViewStream,
                    title = strings.tabSplit,
                    description = strings.featureSplitDescription,
                    accent = colors.secondary,
                    testTag = "home_card_split",
                    onClick = { onOpenTool(AppScreen.SPLIT) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ClosedCaption,
                    title = strings.tabTranscript,
                    description = strings.featureTranscriptDescription,
                    accent = colors.tertiary,
                    testTag = "home_card_transcript",
                    onClick = { onOpenTool(AppScreen.TRANSCRIPT) }
                )
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Movie,
                    title = strings.tabHistory,
                    description = strings.featureHistoryDescription,
                    accent = AmberAccent,
                    testTag = "home_card_history",
                    badgeText = if (exportedClipCount > 0) exportedClipCount.toString() else null,
                    onClick = { onOpenTool(AppScreen.HISTORY) }
                )
            }
        }

        listOf(
            listOf(AppScreen.MERGE to Icons.Default.QueuePlayNext, AppScreen.EXTRACT_AUDIO to Icons.Default.AudioFile),
            listOf(AppScreen.SPLIT_AUDIO to Icons.Default.LibraryMusic, AppScreen.AUDIO_EDITOR to Icons.Default.GraphicEq)
        ).forEach { tools ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tools.forEach { (screen, icon) ->
                    FeatureCard(
                        modifier = Modifier.weight(1f), icon = icon, title = strings.toolTitle(screen),
                        description = when (screen) {
                            AppScreen.MERGE -> strings.tool("Order and join video clips.", "រៀបលំដាប់ និងបញ្ចូលវីដេអូ។")
                            AppScreen.EXTRACT_AUDIO -> strings.tool("Save a video's sound as WAV.", "រក្សាទុកសំឡេងវីដេអូជា WAV។")
                            AppScreen.SPLIT_AUDIO -> strings.tool("Split by time or number of parts.", "បំបែកតាមពេល ឬចំនួនផ្នែក។")
                            else -> strings.tool("Echo, EQ & AI sound suggestions.", "អេកូ EQ និងការណែនាំពី AI។")
                        },
                        accent = colors.secondary, testTag = "home_card_${screen.name.lowercase()}",
                        onClick = { onOpenTool(screen) }
                    )
                }
            }
        }

        // Privacy note
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = strings.homePrivacyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null
) {
    val strings = LocalAppStrings.current

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(190.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (badgeText != null) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${strings.openTool} →",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                fontSize = 12.sp
            )
        }
    }
}
