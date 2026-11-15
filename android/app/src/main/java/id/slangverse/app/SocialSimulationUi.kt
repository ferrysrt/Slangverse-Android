package id.slangverse.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StoryIdentity(
    handle: String, onProfile: (String) -> Unit, subtitle: String? = null, avatarSize: Dp = 36.dp
) {
    val profile = SocialNarrative.profile(handle)
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "View ${profile.name}'s fictional profile") { onProfile(handle) }
        .heightIn(min = 48.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfilePicture(handle, profile.avatarId, size = avatarSize)
        Column(Modifier.weight(1f)) {
            Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(handle, fontSize = 11.sp, color = WarmEditorial.CoralDeep)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = WarmEditorial.Muted)
        }
    }
}

@Composable
internal fun StoryQuoteCard(quote: StoryQuote, onProfile: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = WarmEditorial.Paper,
        border = BorderStroke(1.dp, WarmEditorial.Sand)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("QUOTED POST · EARLIER IN THIS STORY", fontSize = 9.sp, color = WarmEditorial.Muted)
            StoryIdentity(quote.handle, onProfile, avatarSize = 32.dp)
            Text(quote.text, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
internal fun StoryReplies(replies: List<StoryReply>, onProfile: (String) -> Unit) {
    replies.forEach { reply ->
        val parent = replies.firstOrNull { it.id == reply.parentId }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = if (parent == null) 0.dp else 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (parent != null) Box(Modifier.width(2.dp).fillMaxHeight().background(WarmEditorial.Sand))
            Column(Modifier.weight(1f)) {
                StoryIdentity(reply.handle, onProfile, avatarSize = 30.dp)
                Text("Replying to ${parent?.handle ?: "the post"}", fontSize = 10.sp, color = WarmEditorial.Muted)
                Text(reply.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
internal fun StoryPlayerReply(text: String) {
    Surface(color = WarmEditorial.Card, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfilePicture("@you", size = 28.dp)
                Text("You · replying to @confuseduser", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = WarmEditorial.CoralDeep)
            }
            Text(text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
internal fun FictionalProfileDialog(handle: String, onDismiss: () -> Unit, onOpenPost: (String) -> Unit) {
    val profile = SocialNarrative.profile(handle)
    val posts = SocialFeedGame.posts.filter { it.thread.handle == handle }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(profile.name, fontFamily = WarmEditorial.Display) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfilePicture(handle, profile.avatarId, size = 80.dp,
                    contentDescription = "${profile.name}'s illustrated profile picture")
                Text("$handle · fictional character", color = WarmEditorial.CoralDeep)
                Text(profile.bio)
                Text("Story posts", fontWeight = FontWeight.Bold)
                if (posts.isEmpty()) Text("You'll meet this character in replies and quoted conversations.")
                posts.forEach { post ->
                    OutlinedButton(onClick = { onOpenPost(post.thread.id) }, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(post.context.label, fontSize = 10.sp)
                            Text(post.thread.post, fontSize = 12.sp)
                        }
                    }
                }
                Text("Scripted biography. No real account or direct messages.", fontSize = 11.sp,
                    color = WarmEditorial.Muted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back") } })
}

@Composable
internal fun ConversationInbox(updates: List<ConversationUpdate>, readIds: List<String>, onOpenPost: (String) -> Unit) {
    Text("Conversations", fontFamily = WarmEditorial.Display, fontSize = 29.sp)
    Text("Scripted story replies · no push notifications", fontSize = 12.sp, color = WarmEditorial.Muted)
    if (updates.isEmpty()) {
        Text("No updates yet. Reply to a highlighted question in the feed to see what happens next in that story.",
            fontSize = 14.sp, lineHeight = 22.sp)
    }
    updates.forEach { update ->
        OutlinedCard(onClick = { onOpenPost(update.postId) }, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = if (update.postId in readIds)
                WarmEditorial.Card else WarmEditorial.Blush)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val profile = SocialNarrative.profile(update.handle)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfilePicture(update.handle, profile.avatarId)
                    Text("${if (update.postId in readIds) "Read" else "New"} · ${profile.name} continued the story",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(update.text, fontSize = 13.sp, lineHeight = 20.sp)
                Text("Open conversation →", color = WarmEditorial.CoralDeep, fontSize = 12.sp)
            }
        }
    }
}
