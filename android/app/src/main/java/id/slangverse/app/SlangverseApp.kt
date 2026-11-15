package id.slangverse.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm Editorial is a native Jetpack Compose implementation of the approved visual direction.
 * Decorative feed, missions, profile and glossary are clearly demo/curated data.
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
                    xp = sampleXp,
                    combo = sampleCombo,
                    onBack = { screen = EditorialScreen.Feed }
                )
                key(screen) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (screen) {
                            EditorialScreen.Feed -> {
                                PaperCard(background = WarmEditorial.Blush) {
                                    EditorialEyebrow("CONTEXT DETECTIVE · V3")
                                    EditorialTitle("Read the room. Decode the slang.", 29)
                                    Text("Three short cases. Three different slang terms. Your evidence matters.",
                                        color = WarmEditorial.Muted, fontSize = 13.sp)
                                    EditorialPrimary("START THE CASE FILE →") { launchDetective("campus") }
                                }
                                EditorialSection("Your Feed", "Social stories made for contextual practice")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EditorialTag("For you", selected = true)
                                    EditorialTag("Fictional topics")
                                    EditorialTag("✦ Daily")
                                }
                                editorialPosts.forEach { post ->
                                    EditorialSocialPost(
                                        post = post, showComments = false,
                                        onDecode = { launchDetective(post.id) }
                                    )
                                }
                                PaperCard {
                                    EditorialEyebrow("CONNECTED TRAINING")
                                    EditorialTitle("Try the real question pipeline", 22)
                                    Text("Play with GitHub GET question packs or the Node.js live backend. Sources remain clearly labeled.",
                                        color = WarmEditorial.Muted, fontSize = 12.sp)
                                    EditorialPrimary("PLAY API SESSION →") { screen = EditorialScreen.ApiSession }
                                }
                                EditorialResearchNote()
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
                                EditorialSection("Challenges", "Small steps. Stronger instincts.")
                                PaperCard(background = WarmEditorial.Blush) {
                                    EditorialEyebrow("✦ TODAY'S CONTEXT QUEST")
                                    EditorialTitle("Read between the lines.", 29)
                                    Text("Decode a fictional social post by identifying its contextual clues.",
                                        fontSize = 12.sp, color = WarmEditorial.Muted)
                                    EditorialPrimary("START CHALLENGE →") { launchDetective("campus") }
                                }
                                EditorialChallenge("Complete a case file",
                                    sampleCompletedCases.toString() + " / 3",
                                    sampleCompletedCases / 3f, "PRACTICE", "♧")
                                EditorialChallenge("Decode two cases correctly",
                                    sampleCorrectCases.coerceAtMost(2).toString() + " / 2",
                                    (sampleCorrectCases / 2f).coerceAtMost(1f), "REVIEW", "◎")
                                EditorialChallenge("Explore three slang terms",
                                    sampleCompletedCases.toString() + " / 3",
                                    sampleCompletedCases / 3f, "3 SLANG", "▤")
                                Text("Practice goals reflect only the most recent completed demo case file.",
                                    color = WarmEditorial.Muted, fontSize = 11.sp)
                                EditorialResearchNote()
                            }

                            EditorialScreen.Progress -> {
                                EditorialSection("Your Progress", "Based on your last completed sample case file")
                                PaperCard {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        EditorialAvatar("S", WarmEditorial.Peach, 62)
                                        Column {
                                            EditorialEyebrow("LOCAL DEMO · NOT RESEARCH DATA")
                                            EditorialTitle("Context Detective", 25)
                                            Text(sampleXp.toString() + " practice XP from last case file",
                                                color = WarmEditorial.Muted, fontSize = 12.sp)
                                        }
                                    }
                                    EditorialProgress(sampleCompletedCases / 3f)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        PaperCard {
                                            EditorialTitle(sampleCompletedCases.toString() + " / 3", 19)
                                            Text("Cases resolved", color = WarmEditorial.Muted, fontSize = 11.sp)
                                        }
                                    }
                                    Box(Modifier.weight(1f)) {
                                        PaperCard {
                                            EditorialTitle(sampleCorrectCases.toString() + " / 3", 19)
                                            Text("Meanings matched", color = WarmEditorial.Muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                                PaperCard {
                                    EditorialTitle("Last case file", 23)
                                    EditorialMetric("Different slang practiced", sampleCompletedCases.toString())
                                    EditorialMetric("Meanings matched", sampleCorrectCases.toString())
                                    EditorialMetric("Practice XP", sampleXp.toString())
                                    EditorialMetric("Ending combo", sampleCombo.toString())
                                    Text(if (sampleCompletedCases == 0)
                                            "Complete a three-case sample to see your practice results."
                                         else "These are in-memory practice values, not persistence or measured language mastery.",
                                        fontSize = 11.sp, color = WarmEditorial.Muted)
                                }
                                EditorialPrimary("PLAY CONTEXT DETECTIVE →") { launchDetective("campus") }
                            }

                            EditorialScreen.Profile -> {
                                EditorialSection("My Profile", "Your corner of Slangverse")
                                PaperCard {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        EditorialAvatar("S", WarmEditorial.Peach, 70)
                                        Column {
                                            EditorialEyebrow("LOCAL PRACTICE · DEMO PROFILE")
                                            EditorialTitle("Context Detective", 24)
                                            Text("Internet language explorer", color = WarmEditorial.Muted,
                                                fontSize = 12.sp)
                                        }
                                    }
                                    EditorialProgress(sampleCompletedCases / 3f)
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
                        Spacer(Modifier.height(18.dp))
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
                Text("LEARN INTERNET SLANG THROUGH CONTEXTS",
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
            EditorialTag("★ " + xp + " SAMPLE XP")
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

@Composable private fun EditorialSocialPost(
    post: EditorialPost,
    showComments: Boolean,
    onDecode: (() -> Unit)? = null
) {
    var expanded by remember(post.id) { mutableStateOf(showComments) }
    var liked by remember(post.id) { mutableStateOf(false) }
    var saved by remember(post.id) { mutableStateOf(false) }
    PaperCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorialAvatar(post.displayName.take(1), WarmEditorial.Peach, 44)
            Column(Modifier.weight(1f)) {
                Text(post.displayName, fontFamily = WarmEditorial.Display, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.handle + " · " + post.age + " ago", fontSize = 11.sp,
                    color = WarmEditorial.Muted)
            }
            Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = WarmEditorial.Muted)
        }
        val highlight = buildAnnotatedString {
            val at = post.content.indexOf(post.slang, ignoreCase = true)
            if (at >= 0) {
                append(post.content.substring(0, at))
                withStyle(SpanStyle(background = WarmEditorial.Peach, fontWeight = FontWeight.Bold)) {
                    append(post.content.substring(at, at + post.slang.length))
                }
                append(post.content.substring(at + post.slang.length))
            } else append(post.content)
        }
        Text(highlight, fontFamily = WarmEditorial.Display, color = WarmEditorial.Ink,
            fontSize = 19.sp, lineHeight = 26.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(post.hashtag, color = WarmEditorial.CoralDeep,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Fictional social account", fontSize = 10.sp, color = WarmEditorial.Muted)
        }
        HorizontalDivider(color = WarmEditorial.Sand)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { liked = !liked }) {
                Icon(if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like", tint = if (liked) WarmEditorial.Coral else WarmEditorial.Muted,
                    modifier = Modifier.size(17.dp))
                Text(" " + (post.likes + if (liked) 1 else 0),
                    color = WarmEditorial.Muted, fontSize = 11.sp)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments",
                    tint = WarmEditorial.Muted, modifier = Modifier.size(17.dp))
                Text(" " + post.replies.size, color = WarmEditorial.Muted, fontSize = 11.sp)
            }
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.Repeat, contentDescription = "View conversation",
                    tint = WarmEditorial.Muted, modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = { saved = !saved }) {
                Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save post", tint = if (saved) WarmEditorial.Coral else WarmEditorial.Muted)
            }
        }
        if (showComments || expanded) {
            HorizontalDivider(color = WarmEditorial.Sand)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Comments", fontFamily = WarmEditorial.Display, fontSize = 18.sp)
                Text("Context clues", color = WarmEditorial.CoralDeep, fontSize = 11.sp)
            }
            post.replies.take(if (expanded) 3 else 1).forEach { reply ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorialAvatar(reply.handle.trimStart('@').take(1).uppercase(),
                        WarmEditorial.Gold, 30)
                    Column {
                        Text(reply.handle, color = WarmEditorial.Ink, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Text(reply.text, color = WarmEditorial.Ink, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide extra replies ↑" else "Need a clue? Read more replies ↓",
                    color = WarmEditorial.CoralDeep, fontSize = 11.sp)
            }
        }
        if (onDecode != null) {
            EditorialSecondary("DECODE THIS POST →", onDecode)
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
    Text("Slangverse visual prototype. Feed accounts, XP and challenge metrics are demonstration data. " +
            "Source labels and real scoring are handled separately in Connected Training.",
        fontSize = 10.sp, lineHeight = 14.sp, color = WarmEditorial.Muted)
}
