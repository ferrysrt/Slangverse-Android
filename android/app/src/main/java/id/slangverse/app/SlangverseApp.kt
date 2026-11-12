package id.slangverse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// HANDOFF STARTER: mock data, local-only state, no real LLM or backend connection.
// All social accounts and interactions here are fictional.
private val Background = Color(0xFF181326)
private val Surface = Color(0xFF2C2540)
private val Raised = Color(0xFF362B4C)
private val Mint = Color(0xFF74E5BF)
private val Coral = Color(0xFFFF7F87)
private val Gold = Color(0xFFFFC66B)
private val Muted = Color(0xFFBDB2CF)
private val White = Color(0xFFF9F5FF)

private data class Reply(val handle: String, val message: String)
private data class Post(
    val displayName: String,
    val username: String,
    val ago: String,
    val hashtag: String,
    val message: String,
    val replies: List<Reply>,
    val likes: String = "1.8K",
    val replyCount: String = "204"
)
private val campus = Post(
    "Campus Confessions", "@campusfeed", "2h", "#CampusChaos",
    "Deadline tonight, laptop crashed, and my project isn't saved. I'm cooked. 😭",
    listOf(
        Reply("@studybuddy", "bro the lecturer's gonna grill you tomorrow 😭"),
        Reply("@nightowl", "please tell me auto-save was ON 💀"),
        Reply("@finalsweek", "that deadline isn't gonna wait for anyone...")
    )
)
private val gaming = Post(
    "Gaming Lobby", "@gaminglobby", "35m", "#GamingMoments",
    "We were up 4–0 and still lost 4–5. We're cooked. 😭",
    listOf(
        Reply("@scoreboard", "from 4–0 to 4–5 is actually diabolical 😭"),
        Reply("@teammate", "we're never living down that comeback 💀"),
        Reply("@friend2", "wait how did that match collapse so badly?")
    ), "860", "86"
)
private val choices = listOf("Feeling exhausted", "In serious trouble", "Very excited", "Feeling hungry")
private val replyChoices = listOf("They're getting ready to eat", "They're in serious trouble", "They played extremely well", "They're about to celebrate")
private enum class Screen { Feed, Explore, Community, Decode, ReplyBattle, Result, Rank, Profile, ApiSession }

@Composable
fun SlangverseApp() {
    var screen by remember { mutableStateOf(Screen.Feed) }
    var choice by remember { mutableIntStateOf(-1) }
    var submitted by remember { mutableStateOf(false) }
    var streak by remember { mutableIntStateOf(3) }
    fun openQuiz(destination: Screen) { choice = -1; submitted = false; screen = destination }
    BackHandler(screen !in listOf(Screen.Feed, Screen.Explore, Screen.Rank, Screen.Profile)) { screen = Screen.Feed }
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Mint, onPrimary = Background, background = Background, surface = Surface, onSurface = White)
    ) {
        Scaffold(containerColor = Background, bottomBar = {
            if (screen in listOf(Screen.Feed, Screen.Explore, Screen.Community, Screen.Rank, Screen.Profile)) {
                NavigationBar(containerColor = Color(0xFF211B34)) {
                    listOf(
                        Triple(Screen.Feed, "Feed", Icons.Outlined.Home),
                        Triple(Screen.Explore, "Explore", Icons.Outlined.Explore),
                        Triple(Screen.Rank, "Rank", Icons.Outlined.EmojiEvents),
                        Triple(Screen.Profile, "Profile", Icons.Outlined.PersonOutline)
                    ).forEach { (tab, label, icon) ->
                        NavigationBarItem(
                            selected = screen == tab || (screen == Screen.Community && tab == Screen.Explore),
                            onClick = { screen = tab },
                            icon = { Icon(icon, label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = Raised)
                        )
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (screen == Screen.Feed) "◎ SLANGVERSE" else when (screen) {
                            Screen.Explore -> "Trending & Explore"; Screen.Community -> "Campus Hub"; Screen.Decode -> "Decode the Slang";
                            Screen.ReplyBattle -> "Reply Battle"; Screen.Result -> "Quest Complete"; Screen.Rank -> "Top Decoders";
                            Screen.Profile -> "My Profile"; Screen.ApiSession -> "API Training"; else -> "Slangverse"
                        }, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(when (screen) {
                            Screen.Feed -> "Decode the internet."; Screen.Explore -> "Curated fictional trending quests";
                            Screen.Community -> "Community Quest"; Screen.Decode -> "Question 02 / 10";
                            Screen.ReplyBattle -> "Thread 03 / 05"; Screen.Result -> "Reward unlocked";
                            Screen.Rank -> "Demo leaderboard"; Screen.Profile -> "Your slang journey"; Screen.ApiSession -> "10-context HTTP session"
                        }, fontSize = 11.sp, color = Muted)
                    }
                    IconButton(onClick = { screen = if (screen == Screen.Feed) Screen.Explore else Screen.Feed }) {
                        Icon(if (screen == Screen.Feed) Icons.Outlined.Search else Icons.Outlined.ArrowBack, contentDescription = "Navigate", tint = White)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (screen) {
                        Screen.ApiSession -> RemoteTrainingPanel()
                        Screen.Feed -> {
                            Panel(Surface) {
                                Text("API-CONNECTED TRAINING", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Try a real HTTP session with 10 curated contexts. Scoring and answer keys stay on Node.js.", color = Muted, fontSize = 12.sp)
                                PrimaryButton("PLAY API SESSION →") { screen = Screen.ApiSession }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("For You", Mint, Background); Chip("Following", Raised, Muted); Chip("✦ Daily", Raised, Muted)
                            }
                            Panel(Raised) {
                                Text("✦ TRENDING IN SLANGVERSE", color = Gold, fontSize = 11.sp)
                                Text("#CampusChaos", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text("Decode 5 posts from campus life · +100 XP", fontSize = 12.sp, color = Muted)
                                Button(onClick = { screen = Screen.Community }, colors = ButtonDefaults.buttonColors(containerColor = Mint)) { Text("START QUEST", color = Background) }
                            }
                            PostCard(campus, showAction = true, onAction = { openQuiz(Screen.Decode) })
                            Panel(Surface) { Text("🔥 Daily Quest  ·  2 / 5", fontWeight = FontWeight.Bold); Text("Decode five posts to earn your reward", color = Muted, fontSize = 12.sp); LinearProgressIndicator(progress = { .4f }, color = Mint, modifier = Modifier.fillMaxWidth()) }
                            PostCard(gaming, onAction = { openQuiz(Screen.ReplyBattle) }, showAction = true)
                        }
                        Screen.Explore -> {
                            listOf("#CampusChaos" to "Campus deadlines & daily drama", "#GamingMoments" to "Clutch plays & chat reactions", "#RelationshipTalk" to "DMs and mixed signals", "#MemeCulture" to "Internet humor & memes").forEachIndexed { idx, (tag, desc) ->
                                Panel(if (idx == 0) Raised else Surface, modifier = Modifier.fillMaxWidth().clickable { screen = Screen.Community }) {
                                    Text("0${idx+1} · SLANGVERSE QUEST", color = Gold, fontSize = 11.sp)
                                    Text(tag, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                    Text(desc, color = Muted, fontSize = 12.sp)
                                    Text("5 posts · +100 XP →", color = Mint, fontSize = 12.sp)
                                }
                            }
                            Text("These are curated fictional topics, not real-world trending data.", color = Muted, fontSize = 11.sp)
                        }
                        Screen.Community -> {
                            Panel(Raised) { Text("🎓 CAMPUS HUB", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Homework emergencies. Late-night panic. Slang everywhere.", color = Muted, fontSize = 13.sp) }
                            Panel(Surface) { Text("#CampusChaos · Quest", fontWeight = FontWeight.Bold); Text("2 decoded · 3 posts left", color = Muted); LinearProgressIndicator(progress = {.4f}, color = Mint, modifier = Modifier.fillMaxWidth()) }
                            listOf("@campusfeed · cooked" to "Laptop crashed at the worst time.", "@librarylife · locked in" to "Studying all night for finals.", "@classdrama · lowkey" to "Someone just confessed in class...").forEachIndexed { i, pair ->
                                Panel(Surface, Modifier.fillMaxWidth().clickable { openQuiz(if (i == 2) Screen.ReplyBattle else Screen.Decode) }) {
                                    Text(pair.first, fontWeight = FontWeight.Bold)
                                    Text(pair.second, color = Muted, fontSize = 12.sp)
                                    Text(if (i < 2) "✓ COMPLETED" else "TAP TO DECODE →", color = if (i < 2) Mint else Coral, fontSize = 11.sp)
                                }
                            }
                            PrimaryButton("CONTINUE CAMPUS QUEST") { openQuiz(Screen.Decode) }
                        }
                        Screen.Decode, Screen.ReplyBattle -> {
                            LinearProgressIndicator(progress = {if(screen==Screen.Decode) .2f else .6f}, color = Mint, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("🔥 COMBO ×$streak", Raised, Coral); Chip("+130 PTS", Raised, Mint)
                            }
                            PostCard(if (screen == Screen.Decode) campus else gaming, showHint = true, showAction = false)
                            Text(if(screen==Screen.Decode) "What does 'cooked' mean here?" else "Which reply explains it best?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            (if (screen == Screen.Decode) choices else replyChoices).forEachIndexed { idx, label ->
                                val isSelected = choice == idx
                                SurfaceCard(onClick = { if (!submitted) choice = idx }, selected = isSelected || (submitted && idx == 1), highlight = submitted && idx == 1) {
                                    Text("${'A'+idx}   $label", color = if (submitted && idx == 1) Background else White)
                                }
                            }
                            if (submitted) Text("'Cooked' implies serious trouble in this context. This is sample feedback, not expert-validated.", color = Mint, fontSize = 12.sp)
                            PrimaryButton(if(submitted) "VIEW REWARDS →" else "SUBMIT ANSWER", enabled = choice >= 0, coral = true) {
                                if (submitted) screen = Screen.Result else { submitted = true; if (choice == 1) streak += 1 else streak = 0 }
                            }
                        }
                        Screen.Result -> {
                            Panel(Raised) { Text("🏆 QUEST COMPLETE!", color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("#CampusChaos · Sample completion", color = Muted) }
                            Panel(Surface) { Text("+420 XP", color = Mint, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Level 7 · Context Detective", fontSize = 12.sp, color = Muted); LinearProgressIndicator(progress = {.75f}, color = Mint, modifier = Modifier.fillMaxWidth()) }
                            Panel(Surface) { Text("🔥 BADGE UNLOCKED", color = Gold); Text("No Cap!", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Five correct answers in a row (demo reward).", color = Muted, fontSize = 12.sp) }
                            PrimaryButton("EXPLORE NEXT QUEST") { screen = Screen.Explore }
                        }
                        Screen.Rank -> {
                            Panel(Raised) { Text("♛ WEEKLY TOP DECODERS", color = Gold, fontWeight = FontWeight.Bold); listOf("1 · SlangNinja   2,450 pts", "2 · MemeMaster  2,180 pts", "3 · ContextPro  1,960 pts").forEach { Text(it) } }
                            Panel(Surface) { Text("Your rank · #12", fontWeight = FontWeight.Bold); Text("1,230 pts · Sample ranking", color = Mint) }
                            Text("Demo only. Competitive ranking requires identical questions and server-verified answers.", color = Muted, fontSize = 12.sp)
                        }
                        Screen.Profile -> {
                            Panel(Raised) { Text("@context.detective", fontWeight = FontWeight.Bold, fontSize = 21.sp); Text("Level 7 · Slang Explorer", color = Mint); LinearProgressIndicator(progress = {.725f}, color = Mint, modifier = Modifier.fillMaxWidth()) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("24 decoded", Surface, White); Chip("6 badges", Surface, White); Chip("3d streak", Surface, White) }
                            listOf("No Cap! · 5 correct combo", "Detective · 5 context types", "Trend Hunter · 3 daily quests", "Slang Scholar · 20 slang decoded").forEach { Panel(Surface) { Text(it, color = White) } }
                            Panel(Surface) { Text("♧ Saved slang", color = Mint); Text("cooked · ghosted · rizz · lowkey", color = Muted) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post, showAction: Boolean = false, showHint: Boolean = false, onAction: () -> Unit = {}) {
    var expanded by remember(post.username) { mutableStateOf(false) }
    var liked by remember(post.username) { mutableStateOf(false) }
    var bookmarked by remember(post.username) { mutableStateOf(false) }
    Panel(Surface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(42.dp).background(Raised, CircleShape), contentAlignment = Alignment.Center) { Text(post.displayName.take(1), fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) {
                Text(post.displayName + " ✓", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${post.username} · ${post.ago}", color = Muted, fontSize = 11.sp)
            }
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options", tint = Muted)
        }
        Text(post.message, fontSize = 16.sp, lineHeight = 23.sp)
        Text("${post.hashtag}  ·  Fictional community post", color = Mint, fontSize = 11.sp)
        HorizontalDivider(color = Color(0xFF504360))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SocialAction(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if(liked) "Liked" else post.likes) { liked = !liked }
            SocialAction(Icons.Outlined.ChatBubbleOutline, post.replyCount) { expanded = !expanded }
            SocialAction(Icons.Outlined.Repeat, "87") { expanded = true }
            SocialAction(if(bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "") { bookmarked = !bookmarked }
        }
        HorizontalDivider(color = Color(0xFF504360))
        Text("Replies · fictional conversation", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        post.replies.take(if(expanded) 3 else 1).forEach { reply ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).background(Raised, CircleShape), contentAlignment = Alignment.Center) { Text(reply.handle.take(2).last().uppercase(), fontSize = 11.sp) }
                Column {
                    Text(reply.handle, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(reply.message, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if(expanded) "Hide extra replies ↑" else if(showHint) "Need a clue? Read 2 more replies ↓" else "View more replies ↓", color = Mint, fontSize = 12.sp)
        }
        if (showHint) Text("✧ Comments are indirect contextual clues, not direct answers.", color = Muted, fontSize = 10.sp)
        if (showAction) PrimaryButton("DECODE THIS POST") { onAction() }
    }
}
@Composable private fun SocialAction(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick=onClick, contentPadding = PaddingValues(2.dp)) { Icon(icon, null, tint=Muted, modifier=Modifier.size(17.dp)); if (text.isNotEmpty()) Text(" $text", color=Muted, fontSize=10.sp) }
}
@Composable private fun Panel(bg: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(bg, RoundedCornerShape(18.dp)).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
@Composable private fun Chip(label: String, bg: Color, text: Color) { Surface(color=bg, shape=RoundedCornerShape(25.dp)) { Text(label, color=text, fontSize=11.sp, modifier=Modifier.padding(horizontal=12.dp, vertical=9.dp)) } }
@Composable private fun PrimaryButton(label: String, enabled: Boolean = true, coral: Boolean = false, onClick: () -> Unit) {
    Button(onClick=onClick, enabled=enabled, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp), colors=ButtonDefaults.buttonColors(containerColor=if(coral) Coral else Mint, contentColor=Background)) { Text(label, fontWeight=FontWeight.Bold) }
}
@Composable private fun SurfaceCard(onClick: () -> Unit, selected: Boolean, highlight: Boolean, content: @Composable RowScope.() -> Unit) {
    Surface(onClick=onClick, color=if(highlight) Mint else if(selected) Raised else Surface, border=BorderStroke(if(selected)2.dp else 1.dp, if(selected)Mint else Raised), shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment=Alignment.CenterVertically, content=content)
    }
}
