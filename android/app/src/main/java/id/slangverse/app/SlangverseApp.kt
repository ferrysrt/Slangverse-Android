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
import kotlinx.coroutines.CancellationException

/**
 * Warm Editorial is a native Jetpack Compose implementation of the approved visual direction.
 * Feed replies drive local practice progress; profiles and content remain fictional/curated.
 * The API Training route continues to use the existing GitHub GET and live backend clients.
 */
private enum class EditorialScreen { Feed, Detective, Learn, Challenges, Progress, Profile, ApiSession, Activity, Conversation }

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
    var screen by rememberSaveable { mutableStateOf(EditorialScreen.Feed) }
    var feedTab by rememberSaveable { mutableStateOf("For you") }
    var likedPosts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savedPosts by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var archiveExpanded by rememberSaveable { mutableStateOf(false) }
    var feedProgress by rememberSaveable(stateSaver = listSaver<FeedPracticeProgress, String>(
        save = { progress -> progress.answers.map { (id, index) -> "$id:$index" } },
        restore = { entries -> FeedPracticeProgress(entries.associate {
            it.substringBeforeLast(":") to it.substringAfterLast(":").toInt()
        }, posts = emptyList()) }
    )) { mutableStateOf(FeedPracticeProgress(posts = emptyList())) }

    var bankRoot by rememberSaveable { mutableStateOf(PublishedFeedDefaults.BANK_ROOT) }
    var offlineSample by rememberSaveable { mutableStateOf(false) }
    var bankRefresh by rememberSaveable { mutableIntStateOf(0) }
    var loadedBankRoot by rememberSaveable { mutableStateOf("") }
    var loadedBankRefresh by rememberSaveable { mutableIntStateOf(-1) }
    var publishedOrder by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var bankLoading by remember { mutableStateOf(false) }
    var bankError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bankRoot, bankRefresh, offlineSample) {
        bankError = null
        if (offlineSample) {
            bankLoading = false
            feedProgress = feedProgress.copy(posts = SocialFeedGame.posts)
            return@LaunchedEffect
        }
        bankLoading = true
        // Never retain the explicit sample when returning to the published source.
        if (feedProgress.posts.any { !it.published }) feedProgress = feedProgress.copy(posts = emptyList())
        try {
            val bank = GithubStaticBank(bankRoot)
            val catalog = bank.fetchCatalog(requireLlm = true)
            val sessions = bank.fetchPublishedSessions(catalog)
            val savedOrder = if (loadedBankRoot == bankRoot && loadedBankRefresh == bankRefresh)
                publishedOrder else emptyList()
            val posts = mergePublishedFeed(sessions, savedOrder = savedOrder)
            feedProgress = feedProgress.copy(posts = posts)
            publishedOrder = posts.map { it.thread.id }
            loadedBankRoot = bankRoot
            loadedBankRefresh = bankRefresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val retained = if (feedProgress.posts.isNotEmpty())
                " Showing previously loaded published questions in their existing order." else " No sample was loaded."
            bankError = "Published questions unavailable: ${error.message ?: "check your connection"}.$retained Use Refresh to retry."
        } finally {
            bankLoading = false
        }
    }

    // Last local demo case-file metrics; not provider or research data.
    var sampleXp by remember { mutableIntStateOf(0) }
    var sampleCombo by remember { mutableIntStateOf(0) }
    var sampleCompletedCases by remember { mutableIntStateOf(0) }
    var sampleCorrectCases by remember { mutableIntStateOf(0) }
    var detectiveStartCaseId by remember { mutableStateOf("campus") }
    var glossaryQuery by rememberSaveable { mutableStateOf("") }
    var glossaryFilter by rememberSaveable { mutableStateOf("All") }
    var expandedTerm by rememberSaveable { mutableStateOf("cooked") }
    var profileHandle by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var readUpdateIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val conversationUpdates = SocialNarrative.updates(feedProgress)
    val unreadUpdates = conversationUpdates.count { it.postId !in readUpdateIds }

    fun openConversation(postId: String) {
        focusedPostId = postId
        screen = EditorialScreen.Conversation
    }

    fun openContext(context: FeedContext) {
        feedTab = context.label
        screen = EditorialScreen.Feed
    }

    var showPrivacy by remember { mutableStateOf(false) }

    fun launchDetective(caseId: String) {
        detectiveStartCaseId = caseId
        screen = EditorialScreen.Detective
    }

    BackHandler(screen !in listOf(EditorialScreen.Feed, EditorialScreen.Learn, EditorialScreen.Challenges, EditorialScreen.Progress, EditorialScreen.Profile)) {
        screen = EditorialScreen.Feed
    }

    CompositionLocalProvider(
        LocalStoryProfiles provides feedProfiles(feedProgress.posts), LocalStoryPosts provides feedProgress.posts
    ) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = WarmEditorial.Coral,
            // Navy on coral measures 5.4:1; white on coral measured 2.9:1.
            onPrimary = WarmEditorial.Ink,
            background = WarmEditorial.Paper,
            onBackground = WarmEditorial.Ink,
            surface = WarmEditorial.Card,
            onSurface = WarmEditorial.Ink,
            secondary = WarmEditorial.CoralDeep
        )
    ) {
        profileHandle?.let { handle ->
            FictionalProfileDialog(handle,
                onDismiss = { profileHandle = null },
                onOpenPost = { id -> profileHandle = null; openConversation(id) })
        }
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
                    onBack = { screen = EditorialScreen.Feed },
                    unreadUpdates = unreadUpdates,
                    onInbox = { screen = EditorialScreen.Activity }
                )
                key(screen, focusedPostId) {
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
                                    feedProgress.posts.filter { it.thread.id in savedPosts }
                                else feedProgress.posts.filter { context == null || it.context == context }
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    item(key = "published-source") {
                                        PublishedBankControls(bankRoot, bankLoading, bankError, offlineSample,
                                            onRoot = {
                                                if (offlineSample) feedProgress = feedProgress.copy(posts = emptyList())
                                                bankRoot = it; offlineSample = false; bankRefresh++
                                            },
                                            onRefresh = { bankRefresh++ },
                                            onMode = {
                                                feedProgress = feedProgress.copy(posts = emptyList())
                                                offlineSample = it; feedTab = "For you"
                                            })
                                    }
                                    item(key = "feed-tabs") {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            (listOf("For you", "News") + FeedContext.entries
                                                .filter { it != FeedContext.EventReaction }.map { it.label } + "Saved")
                                                .forEach { tab ->
                                                    FilterChip(selected = feedTab == tab,
                                                        onClick = { feedTab = tab },
                                                        label = { Text(tab, fontSize = 12.sp) },
                                                        // Material's default selected fill is lavender, the only purple in this palette.
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = WarmEditorial.Peach,
                                                            selectedLabelColor = WarmEditorial.CoralDeep,
                                                            labelColor = WarmEditorial.Ink))
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
                                            onAnswer = { feedProgress = feedProgress.answer(post.thread.id, it) },
                                            onProfile = { profileHandle = it }, onContext = ::openContext)
                                    }
                                    if (visiblePosts.isEmpty()) item {
                                        PaperCard {
                                            Text(if (feedProgress.posts.isEmpty()) "Load a published question pack to start."
                                                else if (feedTab == "Saved") "No saved posts in this feed yet. Tap a bookmark to keep a conversation here."
                                                else "No conversations in this context in the loaded feed. Choose For you to see all conversations.")
                                        }
                                    }
                                    if (feedProgress.posts.isNotEmpty()) item(key = "feed-finish") {
                                        PaperCard(background = WarmEditorial.Blush) {
                                            EditorialTitle(if (feedProgress.completedCount == feedProgress.posts.size)
                                                "You're all caught up." else "A little context goes a long way.", 23)
                                            Text("${feedProgress.completedCount} / ${feedProgress.posts.size} conversations decoded · ${feedProgress.xp} practice XP",
                                                fontSize = 12.sp, color = WarmEditorial.CoralDeep)
                                            Text(if (offlineSample) "Hand-authored practice · fictional accounts and events."
                                                else "Published LLM questions · local static grading · fictional conversations.",
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

                            EditorialScreen.Activity -> {
                                ConversationInbox(conversationUpdates, readUpdateIds, onOpenPost = { id ->
                                    if (id !in readUpdateIds) readUpdateIds = readUpdateIds + id
                                    openConversation(id)
                                })
                            }

                            EditorialScreen.Conversation -> {
                                val post = feedProgress.posts.firstOrNull { it.thread.id == focusedPostId }
                                if (post != null) {
                                    FeedSocialPost(post, feedProgress.answers[post.thread.id],
                                        liked = post.thread.id in likedPosts, saved = post.thread.id in savedPosts,
                                        onLike = { likedPosts = if (post.thread.id in likedPosts)
                                            likedPosts - post.thread.id else likedPosts + post.thread.id },
                                        onSave = { savedPosts = if (post.thread.id in savedPosts)
                                            savedPosts - post.thread.id else savedPosts + post.thread.id },
                                        onAnswer = { feedProgress = feedProgress.answer(post.thread.id, it) },
                                        onProfile = { profileHandle = it }, onContext = ::openContext,
                                        initiallyExpanded = true)
                                }
                                EditorialSecondary("BACK TO FEED →") { screen = EditorialScreen.Feed }
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
                                EditorialChallenge("Decode ten conversations", "${feedProgress.completedCount.coerceAtMost(10)} / 10",
                                    (feedProgress.completedCount / 10f).coerceAtMost(1f), "PRACTICE", "◎")
                                EditorialChallenge("Explore this feed’s contexts", "${feedProgress.contexts} / ${feedProgress.contextCount}",
                                    feedProgress.contexts.toFloat() / feedProgress.contextCount.coerceAtLeast(1), "EXPLORE", "▤")
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
                                EditorialSection("Your practice", "Loaded feed · locally graded practice")
                                PaperCard(background = WarmEditorial.Blush) {
                                    EditorialTitle("${feedProgress.xp} practice XP", 29)
                                    EditorialProgress(feedProgress.completedCount.toFloat() / feedProgress.posts.size.coerceAtLeast(1))
                                    EditorialMetric("Conversations decoded", "${feedProgress.completedCount} / ${feedProgress.posts.size}")
                                    EditorialMetric("Meanings matched", "${feedProgress.correct} / ${feedProgress.completedCount}")
                                    EditorialMetric("Contexts explored", "${feedProgress.contexts} / ${feedProgress.contextCount}")
                                    Text("One reward per post. Likes and bookmarks never affect your score.",
                                        fontSize = 12.sp, color = WarmEditorial.Muted)
                                }
                                feedProgress.decisions.forEach { result ->
                                    val post = feedProgress.posts.first { it.thread.id == result.caseId }
                                    val displayedOptions = displayAnswerOptions(post.thread.options)
                                    PaperCard {
                                        EditorialEyebrow(post.context?.label?.uppercase() ?: "OTHER CONTEXT")
                                        Text(post.thread.post, fontSize = 13.sp)
                                        if (displayedOptions == null) {
                                            Text("This question has ambiguous answer choices and is unavailable for review.", fontSize = 12.sp)
                                        } else {
                                            Text("Your reply: ${displayedOptions[result.selectedIndex]}", fontSize = 12.sp)
                                            Text("Meaning here: ${displayedOptions[post.thread.correctIndex]}", fontSize = 12.sp)
                                        }
                                        Text(if (result.correct) "✓ Meaning matched · +${result.xpEarned} XP"
                                            else "↺ Review · +${result.xpEarned} XP", color = WarmEditorial.CoralDeep)
                                        Text(post.thread.explanation, fontSize = 12.sp)
                                    }
                                }
                                if (feedProgress.completedCount == 0) Text("Reply to a highlighted comment in the feed to begin.")
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
                                        ProfilePicture("@you", size = 70.dp)
                                        Column {
                                            EditorialEyebrow("LOCAL PRACTICE · DEMO PROFILE")
                                            EditorialTitle("Context Explorer", 24)
                                            Text("Internet language explorer", color = WarmEditorial.Muted,
                                                fontSize = 12.sp)
                                        }
                                    }
                                    EditorialProgress(feedProgress.completedCount.toFloat() / feedProgress.posts.size.coerceAtLeast(1))
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
                                    EditorialSetting("Conversation updates", "$unreadUpdates unread") {
                                        screen = EditorialScreen.Activity
                                    }
                                    EditorialSetting("Appearance", "Warm Editorial") {}
                                    EditorialSetting("Privacy & data", "Information") { showPrivacy = !showPrivacy }
                                    if (showPrivacy) {
                                        Text("This prototype does not create accounts or store real participant research data.",
                                            color = WarmEditorial.Muted, fontSize = 12.sp)
                                        Text("Profile illustrations: Open Peeps by Pablo Stanley (CC0), rendered with DiceBear (MIT). These depict fictional characters.",
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
}

@Composable
private fun EditorialHeader(
    screen: EditorialScreen, xp: Int, combo: Int, onBack: () -> Unit,
    unreadUpdates: Int, onInbox: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(WarmEditorial.Paper)
        .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Slangverse ✳", fontFamily = WarmEditorial.Display,
                    fontSize = 24.sp, color = WarmEditorial.Ink)
                Text("A FICTIONAL SOCIAL FEED FOR SLANG PRACTICE",
                    fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
                    fontSize = 8.sp, color = WarmEditorial.Muted)
            }
            if (screen !in listOf(EditorialScreen.Feed, EditorialScreen.Learn, EditorialScreen.Challenges,
                    EditorialScreen.Progress, EditorialScreen.Profile)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Back to Feed", tint = WarmEditorial.CoralDeep)
                }
            }
            IconButton(onClick = onInbox) {
                BadgedBox(badge = { if (unreadUpdates > 0) Badge { Text(unreadUpdates.toString()) } }) {
                    Icon(Icons.Outlined.Notifications, "Conversation updates, $unreadUpdates unread",
                        tint = WarmEditorial.CoralDeep)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            EditorialTag("★ " + xp + " PRACTICE XP")
            EditorialTag("♨ COMBO " + combo, selected = true)
            // "FEED" on the feed screen labelled the tab the user is already looking at.
            if (screen != EditorialScreen.Feed) {
                Spacer(Modifier.weight(1f))
                Text(
                    when (screen) {
                        EditorialScreen.Detective -> "CONTEXT DETECTIVE"
                        EditorialScreen.Learn -> "LEARN"
                        EditorialScreen.Challenges -> "QUESTS"
                        EditorialScreen.Progress -> "PROGRESS"
                        EditorialScreen.Profile -> "PROFILE"
                        EditorialScreen.ApiSession -> "API SESSION"
                        EditorialScreen.Activity -> "UPDATES"
                        EditorialScreen.Conversation -> "THREAD"
                        EditorialScreen.Feed -> "FEED"
                    },
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = WarmEditorial.CoralDeep, letterSpacing = 0.5.sp
                )
            }
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().shadow(2.dp, shape)
            .background(background, shape)
            .border(1.dp, WarmEditorial.Sand, shape).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
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
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
        // Deep coral instead of the brand coral: white text needs 5.1:1, not 2.9:1.
        colors = ButtonDefaults.buttonColors(containerColor = WarmEditorial.CoralDeep, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp)
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

@Composable private fun FeedSocialPost(
    post: FeedPracticePost, answeredIndex: Int?, liked: Boolean, saved: Boolean,
    onLike: () -> Unit, onSave: () -> Unit, onAnswer: (Int) -> Unit,
    onProfile: (String) -> Unit, onContext: (FeedContext) -> Unit,
    // Collapse context replies unless the slang itself lives inside one; the question card stays visible.
    initiallyExpanded: Boolean = post.termTargetHandle != null
) {
    val thread = post.thread
    val displayedOptions = displayAnswerOptions(thread.options)
    val story = SocialNarrative.story(post)
    var expanded by rememberSaveable(thread.id) { mutableStateOf(initiallyExpanded) }
    var replying by rememberSaveable(thread.id) { mutableStateOf(false) }
    var selected by rememberSaveable(thread.id) { mutableIntStateOf(-1) }
    PaperCard {
        StoryIdentity(thread.handle, onProfile, story.time.ifBlank { null }, avatarSize = 40.dp, emphasized = true)
        Text(thread.post, color = WarmEditorial.Ink, fontSize = 16.sp, lineHeight = 23.sp)
        story.quote?.let { StoryQuoteCard(it, onProfile) }
        // The pack often repeats the hashtag inside the post body; printing the chip twice is noise.
        if (thread.hashtag.isNotBlank() && !thread.post.contains(thread.hashtag, ignoreCase = true)) {
            TextButton(onClick = { post.context?.let(onContext) }, enabled = post.context != null,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text(thread.hashtag, fontSize = 12.sp, color = WarmEditorial.CoralDeep)
            }
        }
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
        if (expanded) StoryReplies(story.replies, onProfile, post.termTargetHandle)
        HorizontalDivider(color = WarmEditorial.Sand)
        Column(Modifier.fillMaxWidth().background(WarmEditorial.Blush, RoundedCornerShape(12.dp)).padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StoryIdentity("@confuseduser", onProfile, "Replying to ${post.termTargetHandle ?: thread.handle}", avatarSize = 30.dp)
            Text(thread.questionComment, fontSize = 15.sp, lineHeight = 21.sp)
            if (displayedOptions == null) {
                Text("This question has ambiguous answer choices and is unavailable for practice.", fontSize = 12.sp)
            } else if (answeredIndex == null && !replying) {
                EditorialPrimary("Reply with the meaning") { replying = true }
            } else if (answeredIndex == null) {
                Text("Choose your interpretation", fontSize = 12.sp, color = WarmEditorial.Muted)
                displayedOptions.forEachIndexed { index, option ->
                    EditorialOption(index, option, selected == index, false, false) { selected = index }
                }
                EditorialPrimary("POST REPLY →", enabled = selected in thread.options.indices) { onAnswer(selected) }
            } else {
                StoryPlayerReply(displayedOptions[answeredIndex])
            }
        }
        if (answeredIndex != null && displayedOptions != null) {
            val correct = answeredIndex == thread.correctIndex
            LearningFeedbackPanel(
                correct = correct,
                xp = if (correct) ContextDetectiveGame.CORRECT_XP else ContextDetectiveGame.PRACTICE_XP,
                meaning = displayedOptions[thread.correctIndex], explanation = thread.explanation,
                sourceLabel = post.sourceLabel)
            if (story.followUp.isNotBlank()) {
                HorizontalDivider(color = WarmEditorial.Sand)
                Text("Later in this story · scripted follow-up", fontSize = 12.sp,
                    lineHeight = 18.sp, color = WarmEditorial.Muted)
                StoryIdentity(story.followUpHandle, onProfile, avatarSize = 32.dp)
                Text(story.followUp, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable private fun EditorialOption(
    index: Int, label: String, selected: Boolean, correct: Boolean,
    reveal: Boolean, onClick: () -> Unit
) {
    val marked = selected || (reveal && correct)
    // A 2dp border on a peach card was invisible; a selected answer now floods the row.
    val bg = when {
        reveal && correct -> WarmEditorial.Sage
        selected -> WarmEditorial.CoralDeep
        else -> WarmEditorial.Card
    }
    val outline = when {
        reveal && correct -> WarmEditorial.Olive
        selected -> WarmEditorial.CoralDeep
        else -> WarmEditorial.Sand
    }
    val content = if (selected && !reveal) Color.White else WarmEditorial.Ink
    Surface(
        onClick = onClick, enabled = !reveal,
        color = bg, border = BorderStroke(if (marked) 2.dp else 1.dp, outline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(('A' + index).toString() + ".", fontFamily = WarmEditorial.Display,
                fontSize = 16.sp, color = content)
            Text(label, color = content,
                fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            if (marked) {
                Icon(Icons.Outlined.CheckCircle,
                    contentDescription = if (reveal) "Correct answer" else "Selected",
                    tint = if (reveal) WarmEditorial.Olive else Color.White,
                    modifier = Modifier.size(22.dp))
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
