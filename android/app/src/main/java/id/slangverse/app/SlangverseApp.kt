package id.slangverse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm Editorial is a native Jetpack Compose implementation of the approved visual direction.
 * Feed replies drive local practice progress; profiles and content remain fictional/curated.
 * The API Training route continues to use the existing GitHub GET and live backend clients.
 */
private enum class EditorialScreen { Feed, Detective, Learn, Challenges, Progress, Profile, ApiSession }

private data class EditorialReply(val handle: String, val text: String)

private data class EditorialPost(
    val id: String,
    val displayName: String,
    val handle: String,
    val age: String,
    val hashtag: String,
    val slang: String,
    val content: String,
    val likes: Int,
    val replies: List<EditorialReply>,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

private val editorialPosts = listOf(
    EditorialPost(
        "campus", "Campus Confessions", "@campusfeed", "2h", "#CampusChaos", "cooked",
        "The presentation is tomorrow, my laptop crashed, and I didn't back up anything. I'm cooked 😭",
        247,
        listOf(
            EditorialReply("@classmate", "Please tell me you saved a copy somewhere 💀"),
            EditorialReply("@nightowl", "That deadline is going to be brutal..."),
            EditorialReply("@studybuddy", "Maybe check your cloud backup before panicking 😭")
        ),
        listOf("Feeling hungry", "In serious trouble", "Feeling confident", "Planning a meal"),
        1, "In this context, 'cooked' means being in serious trouble. The broken laptop and tomorrow's presentation provide the clues."
    ),
    EditorialPost(
        "exam", "studybro", "@studybro", "5h", "#ExamWeek", "no cap",
        "Just aced the midterm with five minutes to spare — no cap, I was stressing the whole time 😤",
        389,
        listOf(
            EditorialReply("@classmate", "Wait, actually? Tell me how you did it."),
            EditorialReply("@finalsweek", "You earned that break."),
            EditorialReply("@nightowl", "I still haven't checked my grade.")
        ),
        listOf("Making a joke", "Feeling uncertain", "Speaking honestly", "Changing the subject"),
        2, "'No cap' is used to emphasize that the speaker is telling the truth or is not exaggerating."
    ),
    EditorialPost(
        "travel", "nightowl", "@nightowl", "7h", "#WeekendVibes", "lowkey",
        "I lowkey want to drop everything and take a spontaneous weekend trip somewhere new... 🏔️",
        312,
        listOf(
            EditorialReply("@friend", "That sounds tempting, honestly."),
            EditorialReply("@wanderer", "You have been talking about a break all week."),
            EditorialReply("@classmate", "Could be a nice reset after exams.")
        ),
        listOf("Loudly announcing", "Quietly or somewhat", "Completely forgetting", "Never wanting"),
        1, "'Lowkey' often expresses a feeling or desire in a subtle or understated way."
    )
)

@Composable
fun SlangverseApp() {
    var screen by remember { mutableStateOf(EditorialScreen.Feed) }
    var feedTab by rememberSaveable { mutableStateOf("For you") }
    var likedPosts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savedPosts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var archiveExpanded by rememberSaveable { mutableStateOf(false) }
    var feedProgress by rememberSaveable(stateSaver = listSaver<FeedPracticeProgress, String>(
        save = { progress -> progress.answers.map { (id, index) -> "$id:$index" } },
        restore = { entries -> FeedPracticeProgress(entries.associate {
            it.substringBeforeLast(":") to it.substringAfterLast(":").toInt()
        }) }
    )) { mutableStateOf(FeedPracticeProgress()) }

    // Last local demo case-file metrics; not provider or research data.
    var sampleXp by remember { mutableIntStateOf(0) }
    var sampleCombo by remember { mutableIntStateOf(0) }
    var sampleCompletedCases by remember { mutableIntStateOf(0) }
    var sampleCorrectCases by remember { mutableIntStateOf(0) }
    var detectiveStartCaseId by remember { mutableStateOf("campus") }
    var glossaryQuery by rememberSaveable { mutableStateOf("") }
    var glossaryFilter by rememberSaveable { mutableStateOf("All") }
    var expandedTerm by rememberSaveable { mutableStateOf("cooked") }
    var notifications by remember { mutableStateOf(true) }
    var showPrivacy by remember { mutableStateOf(false) }

    fun launchDetective(caseId: String) {
        detectiveStartCaseId = caseId
        screen = EditorialScreen.Detective
    }

    BackHandler(screen !in listOf(EditorialScreen.Feed, EditorialScreen.Learn, EditorialScreen.Challenges, EditorialScreen.Progress, EditorialScreen.Profile)) {
        screen = EditorialScreen.Feed
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WarmEditorial.Coral,
            onPrimary = Color.White,
            background = WarmEditorial.Paper,
            onBackground = WarmEditorial.Ink,
            surface = WarmEditorial.Card,
            onSurface = WarmEditorial.Ink,
            secondary = WarmEditorial.CoralDeep
        )
    ) {
        Scaffold(containerColor = WarmEditorial.Paper, bottomBar = {
            if (screen != EditorialScreen.Detective) NavigationBar(containerColor = WarmEditorial.Ink, tonalElevation = 0.dp) {
                val tabs = listOf(
                    Triple(EditorialScreen.Feed, "Feed", Icons.Outlined.Home),
                    Triple(EditorialScreen.Learn, "Learn", Icons.Outlined.MenuBook),
                    Triple(EditorialScreen.Challenges, "Quests", Icons.Outlined.EmojiEvents),
                    Triple(EditorialScreen.Progress, "Progress", Icons.Outlined.BarChart),
                    Triple(EditorialScreen.Profile, "Profile", Icons.Outlined.PersonOutline)
                )
                tabs.forEach { (page, text, icon) ->
                    NavigationBarItem(
                        selected = screen == page,
                        onClick = { screen = page },
                        icon = { Icon(icon, contentDescription = text) },
                        label = { Text(text, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = WarmEditorial.Coral,
                            unselectedIconColor = WarmEditorial.Card,
                            unselectedTextColor = WarmEditorial.Card
                        )
                    )
                }
            }
        }) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (screen != EditorialScreen.Detective) EditorialHeader(
                    screen = screen,
                    xp = feedProgress.xp,
                    combo = feedProgress.combo,
                    onBack = { screen = EditorialScreen.Feed }
                )
                key(screen) {
                    Column(
                        Modifier.fillMaxSize()
                            .then(if (screen == EditorialScreen.Feed) Modifier else Modifier.verticalScroll(rememberScrollState()))
                            .padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (screen) {
                            EditorialScreen.Feed -> {
                                val context = FeedContext.entries.firstOrNull { it.label == feedTab }
                                val visiblePosts = if (feedTab == "Saved")
                                    SocialFeedGame.posts.filter { it.thread.id in savedPosts }
                                else SocialFeedGame.filter(context)
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    item(key = "feed-tabs") {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (listOf("For you", "News") + FeedContext.entries
                                                .filter { it != FeedContext.EventReaction }.map { it.label } + "Saved")
                                                .forEach { tab ->
                                                    FilterChip(selected = feedTab == tab,
                                                        onClick = { feedTab = tab }, label = { Text(tab) })
                                                }
                                        }
                                    }
                                    items(visiblePosts, key = { it.thread.id }) { post ->
                                        FeedSocialPost(post, feedProgress.answers[post.thread.id],
                                            liked = post.thread.id in likedPosts,
                                            saved = post.thread.id in savedPosts,
                                            onLike = { likedPosts = if (post.thread.id in likedPosts)
                                                likedPosts - post.thread.id else likedPosts + post.thread.id },
                                            onSave = { savedPosts = if (post.thread.id in savedPosts)
                                                savedPosts - post.thread.id else savedPosts + post.thread.id },
                                            onAnswer = { feedProgress = feedProgress.answer(post.thread.id, it) })
                                    }
                                    if (visiblePosts.isEmpty()) item {
                                        PaperCard {
                                            Text("No saved posts yet. Tap a bookmark to keep a conversation here.")
                                        }
                                    }
                                    item(key = "feed-finish") {
                                        PaperCard(background = WarmEditorial.Blush) {
                                            EditorialTitle(if (feedProgress.answers.size == SocialFeedGame.posts.size)
                                                "You're all caught up." else "A little context goes a long way.", 23)
                                            Text("${feedProgress.answers.size} / 10 conversations decoded · ${feedProgress.xp} practice XP",
                                                fontSize = 12.sp, color = WarmEditorial.CoralDeep)
                                            Text("Hand-authored practice · fictional accounts and events. News means reactions to fictional events.",
                                                fontSize = 11.sp, color = WarmEditorial.Muted)
                                            EditorialSecondary("VIEW PRACTICE RESULTS →") { screen = EditorialScreen.Progress }
                                        }
                                    }
                                    item(key = "connected-training") {
                                        PaperCard {
                                            EditorialEyebrow("CONNECTED TRAINING")
                                            Text("Use a generated question pack or the live backend.", fontSize = 12.sp)
                                            EditorialSecondary("PLAY API SESSION →") { screen = EditorialScreen.ApiSession }
                                        }
                                    }
                                }
                            }

                            EditorialScreen.Detective -> {
                                ContextDetective(
                                    startCaseId = detectiveStartCaseId,
                                    onExit = { screen = EditorialScreen.Feed },
                                    onSessionCompleted = { earnedXp, trailingCombo, correctCases ->
                                        sampleXp = earnedXp
                                        sampleCombo = trailingCombo
                                        sampleCompletedCases = ContextDetectiveGame.cases.size
                                        sampleCorrectCases = correctCases
                                    }
                                )
                            }

                            EditorialScreen.Learn -> {
                                EditorialSection("Glossary / Learn", "Explore one curated meaning at a time")
                                OutlinedTextField(
                                    value = glossaryQuery,
                                    onValueChange = { glossaryQuery = it },
                                    label = { Text("Search slang") },
                                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("All", "School", "Social").forEach { group ->
                                        EditorialTag(group, selected = glossaryFilter == group) { glossaryFilter = group }
                                    }
                                }
                                val filtered = editorialPosts.filter {
                                    it.slang.contains(glossaryQuery.trim(), ignoreCase = true) &&
                                        (glossaryFilter == "All" ||
                                            (glossaryFilter == "School" && it.id == "campus") ||
                                            (glossaryFilter == "Social" && it.id != "campus"))
                                }
                                if (filtered.isEmpty()) {
                                    PaperCard {
                                        Text("No matching slang. Try another search or category.",
                                            color = WarmEditorial.Muted, fontSize = 13.sp)
                                    }
                                }
                                filtered.forEach { post ->
                                    PaperCard {
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                expandedTerm = if (expandedTerm == post.slang) "" else post.slang
                                            },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            EditorialTitle(post.slang, 29)
                                            Icon(if (expandedTerm == post.slang) Icons.Outlined.ExpandLess
                                                else Icons.Outlined.ExpandMore, contentDescription = "Expand term",
                                                tint = WarmEditorial.CoralDeep)
                                        }
                                        if (expandedTerm == post.slang) {
                                            EditorialEyebrow("MEANING IN THIS GAME")
                                            Text(post.options[post.answerIndex], fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold)
                                            EditorialEyebrow("EXAMPLE IN CONTEXT")
                                            Text(post.content, fontSize = 13.sp, lineHeight = 20.sp)
                                            Text("Slang senses are provisional and vary across communities.",
                                                color = WarmEditorial.Muted, fontSize = 11.sp)
                                            EditorialPrimary("PRACTICE THIS TERM →") { launchDetective(post.id) }
                                        } else {
                                            Text(post.options[post.answerIndex],
                                                color = WarmEditorial.Muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                                EditorialResearchNote()
                            }

                            EditorialScreen.Challenges -> {
                                EditorialSection("Your quests", "Small goals, at your own pace")
                                EditorialChallenge("Decode ten conversations", "${feedProgress.answers.size} / 10",
                                    feedProgress.answers.size / 10f, "PRACTICE", "◎")
                                EditorialChallenge("Explore the nine contexts", "${feedProgress.contexts} / 9",
                                    feedProgress.contexts / 9f, "EXPLORE", "▤")
                                EditorialChallenge("Match five meanings", "${feedProgress.correct.coerceAtMost(5)} / 5",
                                    (feedProgress.correct / 5f).coerceAtMost(1f), "REVIEW", "✳")
                                EditorialPrimary("BACK TO THE CONVERSATIONS →") { screen = EditorialScreen.Feed }
                                PaperCard {
                                    TextButton(onClick = { archiveExpanded = !archiveExpanded }) {
                                        Text(if (archiveExpanded) "Hide archived Context Detective ↑"
                                            else "Archived: Context Detective · V3 ↓")
                                    }
                                    if (archiveExpanded) {
                                        EditorialEyebrow("CONTEXT DETECTIVE · V3")
                                        EditorialTitle("Read the room. Decode the slang.", 29)
                                        Text("Three short cases. Three different slang terms. Your evidence matters.", fontSize = 13.sp)
                                        EditorialPrimary("START THE CASE FILE →") { launchDetective("campus") }
                                        Text("Original three-slang demo. Its results stay separate from feed practice.",
                                            fontSize = 11.sp, color = WarmEditorial.Muted)
                                    }
                                }
                            }

                            EditorialScreen.Progress -> {
                                EditorialSection("Your practice", "Progress from the conversations you've answered")
                                PaperCard(background = WarmEditorial.Blush) {
                                    EditorialTitle("${feedProgress.xp} practice XP", 29)
                                    EditorialProgress(feedProgress.answers.size / 10f)
                                    EditorialMetric("Conversations decoded", "${feedProgress.answers.size} / 10")
                                    EditorialMetric("Meanings matched", "${feedProgress.correct} / ${feedProgress.answers.size}")
                                    EditorialMetric("Contexts explored", "${feedProgress.contexts} / 9")
                                    Text("One reward per post. Likes and bookmarks never affect your score.",
                                        fontSize = 12.sp, color = WarmEditorial.Muted)
                                }
                                feedProgress.decisions.forEach { result ->
                                    val post = SocialFeedGame.posts.first { it.thread.id == result.caseId }
                                    PaperCard {
                                        EditorialEyebrow(post.context.label.uppercase())
                                        Text(post.thread.post, fontSize = 13.sp)
                                        Text("Your reply: ${post.thread.options[result.selectedIndex]}", fontSize = 12.sp)
                                        Text("Meaning here: ${post.thread.meaning}", fontSize = 12.sp)
                                        Text(if (result.correct) "✓ Meaning matched · +${result.xpEarned} XP"
                                            else "↺ Review · +${result.xpEarned} XP", color = WarmEditorial.CoralDeep)
                                        Text(post.thread.explanation, fontSize = 12.sp)
                                    }
                                }
                                if (feedProgress.answers.isEmpty()) Text("Reply to a highlighted comment in the feed to begin.")
                                Text("Local practice only; not a measure of mastery or participant research data.",
                                    fontSize = 11.sp, color = WarmEditorial.Muted)
                                EditorialPrimary("RETURN TO FEED →") { screen = EditorialScreen.Feed }
                                if (sampleCompletedCases > 0) PaperCard {
                                    EditorialEyebrow("ARCHIVED DEMO · LAST CASE FILE")
                                    Text("$sampleCompletedCases cases · $sampleCorrectCases matched · $sampleXp XP · ending combo $sampleCombo",
                                        fontSize = 12.sp)
                                }
                            }

                            EditorialScreen.Profile -> {
                                EditorialSection("My Profile", "Your corner of Slangverse")
                                PaperCard {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        EditorialAvatar("S", WarmEditorial.Peach, 70)
                                        Column {
                                            EditorialEyebrow("LOCAL PRACTICE · DEMO PROFILE")
                                            EditorialTitle("Context Explorer", 24)
                                            Text("Internet language explorer", color = WarmEditorial.Muted,
                                                fontSize = 12.sp)
                                        }
                                    }
                                    EditorialProgress(feedProgress.answers.size / 10f)
                                }
                                PaperCard {
                                    EditorialTitle("Badge ideas · preview only", 24)
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        listOf("🏅" to "Starter", "🔥" to "Combo",
                                            "🏆" to "Decoder", "📚" to "Scholar").forEach { (icon, caption) ->
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(icon, fontSize = 26.sp)
                                                Text(caption, fontSize = 10.sp, color = WarmEditorial.Muted)
                                            }
                                        }
                                    }
                                }
                                PaperCard {
                                    EditorialTitle("Saved conversations", 23)
                                    Text("${savedPosts.size} bookmarked posts", fontSize = 12.sp)
                                    EditorialSecondary("OPEN SAVED POSTS →") {
                                        feedTab = "Saved"
                                        screen = EditorialScreen.Feed
                                    }
                                }
                                PaperCard {
                                    EditorialTitle("Explore slang", 23)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("cooked", "no cap", "lowkey").forEach { term ->
                                            EditorialTag(term) {
                                                expandedTerm = term
                                                screen = EditorialScreen.Learn
                                            }
                                        }
                                    }
                                }
                                PaperCard {
                                    EditorialTitle("Settings", 23)
                                    EditorialSetting("Notifications", if (notifications) "On" else "Off") {
                                        notifications = !notifications
                                    }
                                    EditorialSetting("Appearance", "Warm Editorial") {}
                                    EditorialSetting("Privacy & data", "Information") { showPrivacy = !showPrivacy }
                                    if (showPrivacy) {
                                        Text("This prototype does not create accounts or store real participant research data.",
                                            color = WarmEditorial.Muted, fontSize = 12.sp)
                                    }
                                }
                                EditorialResearchNote()
                            }

                            EditorialScreen.ApiSession -> {
                                EditorialSection("Connected Training", "GitHub GET or live backend")
                                RemoteTrainingPanel()
                            }
                        }
                        if (screen != EditorialScreen.Feed) Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialHeader(screen: EditorialScreen, xp: Int, combo: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(WarmEditorial.Paper)
        .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Slangverse ✳", fontFamily = WarmEditorial.Display,
                    fontSize = 29.sp, color = WarmEditorial.Ink)
                Text("A FICTIONAL SOCIAL FEED FOR SLANG PRACTICE",
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    fontSize = 8.sp, color = WarmEditorial.Muted)
            }
            if (screen !in listOf(EditorialScreen.Feed, EditorialScreen.Learn, EditorialScreen.Challenges,
                    EditorialScreen.Progress, EditorialScreen.Profile)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Feed", tint = WarmEditorial.CoralDeep)
                }
            } else {
                Icon(Icons.Outlined.AutoAwesome, "Slangverse",
                    tint = WarmEditorial.Coral, modifier = Modifier.size(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            EditorialTag("★ " + xp + " PRACTICE XP")
            EditorialTag("♨ COMBO " + combo, selected = true)
            Spacer(Modifier.weight(1f))
            Text(
                when (screen) {
                    EditorialScreen.Feed -> "FEED"
                    EditorialScreen.Detective -> "CONTEXT DETECTIVE"
                    EditorialScreen.Learn -> "LEARN"
                    EditorialScreen.Challenges -> "QUESTS"
                    EditorialScreen.Progress -> "PROGRESS"
                    EditorialScreen.Profile -> "PROFILE"
                    EditorialScreen.ApiSession -> "API SESSION"
                },
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                color = WarmEditorial.CoralDeep, letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable private fun EditorialSection(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EditorialTitle(title + " ✳", 29)
        Text(subtitle, fontSize = 12.sp, color = WarmEditorial.Muted)
    }
}

@Composable private fun EditorialTitle(value: String, size: Int) {
    Text(value, color = WarmEditorial.Ink, fontSize = size.sp,
        fontFamily = WarmEditorial.Display, lineHeight = (size + 3).sp)
}

@Composable private fun EditorialEyebrow(value: String) {
    Text(value, color = WarmEditorial.CoralDeep, fontSize = 10.sp,
        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
}

@Composable private fun PaperCard(
    modifier: Modifier = Modifier,
    background: Color = WarmEditorial.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(21.dp)
    Column(
        modifier.fillMaxWidth().shadow(3.dp, shape)
            .background(background, shape)
            .border(1.dp, WarmEditorial.Sand, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable private fun EditorialTag(value: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.background(if (selected) WarmEditorial.Peach else WarmEditorial.Card, shape)
            .border(1.dp, WarmEditorial.Sand, shape)
            .then(if (onClick == null) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) WarmEditorial.CoralDeep else WarmEditorial.Ink)
    }
}

@Composable private fun EditorialPrimary(
    label: String, enabled: Boolean = true, onClick: () -> Unit
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WarmEditorial.Coral, contentColor = Color.White),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun EditorialSecondary(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, WarmEditorial.Coral),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmEditorial.CoralDeep)
    }
}

@Composable private fun EditorialAvatar(initial: String, bg: Color, size: Int) {
    Box(Modifier.size(size.dp).background(bg, CircleShape)
        .border(2.dp, WarmEditorial.Card, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, fontFamily = WarmEditorial.Display, fontSize = (size / 2).sp,
            color = WarmEditorial.Ink)
    }
}

@Composable private fun FeedSocialPost(
    post: FeedPracticePost, answeredIndex: Int?, liked: Boolean, saved: Boolean,
    onLike: () -> Unit, onSave: () -> Unit, onAnswer: (Int) -> Unit
) {
    val thread = post.thread
    var expanded by rememberSaveable(thread.id) { mutableStateOf(false) }
    var replying by rememberSaveable(thread.id) { mutableStateOf(false) }
    var selected by rememberSaveable(thread.id) { mutableIntStateOf(-1) }
    PaperCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorialAvatar(thread.author.take(1), WarmEditorial.Peach, 44)
            Column(Modifier.weight(1f)) {
                Text(thread.author, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${thread.handle} · fictional account", fontSize = 11.sp, color = WarmEditorial.Muted)
            }
            Text(post.context.label, fontSize = 11.sp, color = WarmEditorial.CoralDeep)
        }
        Text(thread.post, color = WarmEditorial.Ink, fontSize = 18.sp, lineHeight = 26.sp)
        Text(thread.hashtag, fontSize = 12.sp, color = WarmEditorial.CoralDeep)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconToggleButton(checked = liked, onCheckedChange = { onLike() }) {
                Icon(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (liked) "Unlike post" else "Like post",
                    tint = if (liked) WarmEditorial.Coral else WarmEditorial.Muted)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, Modifier.size(18.dp))
                Text(if (expanded) " Hide replies" else " ${thread.comments.size + 1} replies", fontSize = 12.sp)
            }
            IconToggleButton(checked = saved, onCheckedChange = { onSave() }) {
                Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Unsave post" else "Save post",
                    tint = if (saved) WarmEditorial.Coral else WarmEditorial.Muted)
            }
        }
        if (expanded) {
            thread.comments.forEachIndexed { index, comment ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorialAvatar(if (index == 0) "N" else "J", WarmEditorial.Gold, 30)
                    Column {
                        Text(if (index == 0) "@naya" else "@justjo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(comment, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        HorizontalDivider(color = WarmEditorial.Sand)
        Column(Modifier.fillMaxWidth().background(WarmEditorial.Blush, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("@confuseduser · help explain", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = WarmEditorial.CoralDeep)
            Text(thread.questionComment, fontSize = 15.sp, lineHeight = 22.sp)
            if (answeredIndex == null && !replying) {
                TextButton(onClick = { replying = true }) { Text("Reply with the meaning ↓") }
            } else if (answeredIndex == null) {
                Text("Choose your interpretation", fontSize = 11.sp, color = WarmEditorial.Muted)
                thread.options.forEachIndexed { index, option ->
                    EditorialOption(index, option, selected == index, false, false) { selected = index }
                }
                EditorialPrimary("POST REPLY →", enabled = selected in thread.options.indices) { onAnswer(selected) }
            } else {
                val correct = answeredIndex == thread.correctIndex
                Text("You replied: ${thread.options[answeredIndex]}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (correct) "✓ Meaning matched · +${ContextDetectiveGame.CORRECT_XP} XP"
                    else "↺ Let's review · +${ContextDetectiveGame.PRACTICE_XP} practice XP",
                    fontSize = 12.sp, color = WarmEditorial.CoralDeep)
                if (!correct) Text("Meaning here: ${thread.meaning}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(thread.explanation, fontSize = 13.sp, lineHeight = 20.sp)
                Text("Hand-authored practice feedback", fontSize = 10.sp, color = WarmEditorial.Muted)
            }
        }
    }
}

@Composable private fun EditorialOption(
    index: Int, label: String, selected: Boolean, correct: Boolean,
    reveal: Boolean, onClick: () -> Unit
) {
    val bg = if (reveal && correct) WarmEditorial.Sage
        else if (selected) WarmEditorial.Blush else WarmEditorial.Card
    val outline = if (reveal && correct) WarmEditorial.Olive
        else if (selected) WarmEditorial.Coral else WarmEditorial.Sand
    Surface(
        onClick = onClick, enabled = !reveal,
        color = bg, border = BorderStroke(if (selected || correct) 2.dp else 1.dp, outline),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(('A' + index).toString() + ".", fontFamily = WarmEditorial.Display,
                fontSize = 19.sp, color = WarmEditorial.Ink)
            Text(label, color = WarmEditorial.Ink,
                fontSize = 13.sp, modifier = Modifier.weight(1f))
            if ((reveal && correct) || (!reveal && selected)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected",
                    tint = if (reveal) WarmEditorial.Olive else WarmEditorial.CoralDeep,
                    modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable private fun EditorialProgress(amount: Float) {
    LinearProgressIndicator(
        progress = { amount.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = WarmEditorial.Coral,
        trackColor = WarmEditorial.Sand
    )
}

@Composable private fun EditorialChallenge(
    title: String, ratio: String, progress: Float, reward: String, symbol: String
) {
    PaperCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(46.dp).background(WarmEditorial.Blush, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center) {
                Text(symbol, fontSize = 25.sp, color = WarmEditorial.CoralDeep)
            }
            Column(Modifier.weight(1f)) {
                EditorialTitle(title, 19)
                Text(ratio + " complete", fontSize = 11.sp, color = WarmEditorial.Muted)
            }
            Text("+" + reward, color = WarmEditorial.CoralDeep,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        EditorialProgress(progress)
    }
}

@Composable private fun EditorialMetric(label: String, number: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = WarmEditorial.Muted, fontSize = 12.sp)
        Text(number, fontWeight = FontWeight.Bold, color = WarmEditorial.Ink, fontSize = 14.sp)
    }
    HorizontalDivider(color = WarmEditorial.Sand)
}

@Composable private fun EditorialSetting(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text(value + " →", color = WarmEditorial.CoralDeep, fontSize = 11.sp)
    }
    HorizontalDivider(color = WarmEditorial.Sand)
}

@Composable private fun EditorialResearchNote() {
    Text("Fictional accounts and hand-authored practice. XP reflects your local replies, not measured mastery. " +
            "Connected Training labels its question sources separately.",
        fontSize = 10.sp, lineHeight = 14.sp, color = WarmEditorial.Muted)
}
