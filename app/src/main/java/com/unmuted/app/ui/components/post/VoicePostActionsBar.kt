package com.unmuted.app.ui.components.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun VoicePostActionsBar(
    post: VoicePost,
    reaction: UserPostReaction?,
    primaryAccent: Color,
    isClapback: Boolean,
    haptics: StudioHaptics,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenComments: () -> Unit,
    onEcho: () -> Unit,
    onBookmark: () -> Unit,
    onShareClick: () -> Unit,
    onQuoteClapback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val vote = reaction?.voteEnum() ?: VoteType.NONE
    val echoed = reaction?.echoed == true
    val bookmarked = reaction?.bookmarked == true

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EngagementButton(
            icon = if (vote == VoteType.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            label = post.likeCount.toString(),
            active = vote == VoteType.LIKE,
            activeColor = ElectricCoral,
            contentDesc = "Like voice",
            tag = "like_btn_${post.id}",
            onClick = {
                haptics(HapticCue.REACTION)
                onLike()
            }
        )
        EngagementButton(
            icon = if (vote == VoteType.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
            label = post.dislikeCount.toString(),
            active = vote == VoteType.DISLIKE,
            activeColor = AcousticError,
            contentDesc = "Dislike voice",
            tag = "dislike_btn_${post.id}",
            onClick = {
                haptics(HapticCue.REACTION)
                onDislike()
            }
        )
        EngagementButton(
            icon = Icons.Filled.ChatBubbleOutline,
            label = post.commentCount.toString(),
            active = false,
            activeColor = StudioWhite,
            contentDesc = "Open comments",
            tag = "comment_btn_${post.id}",
            onClick = onOpenComments
        )
        EngagementButton(
            icon = Icons.Filled.Repeat,
            label = post.echoCount.toString(),
            active = echoed,
            activeColor = SignalMint,
            contentDesc = "Echo repost",
            tag = "echo_btn_${post.id}",
            onClick = {
                haptics(HapticCue.REACTION)
                onEcho()
            }
        )
        if (onQuoteClapback != null && !post.audioMediaId.isNullOrBlank()) {
            EngagementButton(
                icon = Icons.Filled.FormatQuote,
                label = "Clap",
                active = isClapback,
                activeColor = SignalCyan,
                contentDesc = "Voice Quote Clapback",
                tag = "clapback_btn_${post.id}",
                onClick = {
                    haptics(HapticCue.REACTION)
                    onQuoteClapback()
                }
            )
        }
        IconButton(
            onClick = {
                haptics(HapticCue.REACTION)
                onBookmark()
            },
            modifier = Modifier.size(40.dp).testTag("bookmark_btn_${post.id}")
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Save voice",
                tint = if (bookmarked) primaryAccent else TitaniumMuted,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(
            onClick = {
                haptics(HapticCue.REACTION)
                onShareClick()
            },
            modifier = Modifier.size(40.dp).testTag("share_btn_${post.id}")
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share voice take",
                tint = TitaniumMuted,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun EngagementButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (active) activeColor else TitaniumMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) activeColor else TitaniumMuted
        )
    }
}
