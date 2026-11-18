package id.slangverse.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

class MixedPublishedFeedTest {
    private fun fixtureText() = checkNotNull(javaClass.getResource("/mixed-pack.json")).readText(Charsets.UTF_8)
    private fun fixture() = JSONObject(fixtureText())
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun parse(json: JSONObject = fixture()): StaticBankSession =
        parseMixedBankSession(json.toString(), json.getString("packId"))
    private fun reject(change: (JSONObject) -> Unit) {
        val json = fixture().also(change)
        assertThrows(ApiException::class.java) { parse(json) }
    }
    private fun catalogJson(count: Int = 1): JSONObject = JSONObject("""
        {"schemaVersion":2,"source":"github-static","generationSource":"llm","datasetVersion":"fixture-v2",
         "bankVersion":"fixture-bank","status":"ready","candidateCount":2,"expertValidated":false,
         "senses":[{"senseId":"cooked-trouble","term":"cooked","sha256":"${"c".repeat(64)}"}],"packs":[]}
    """).put("packs", JSONArray((1..count).map { index ->
        JSONObject().put("packId", "mixed-$index").put("sha256", "a".repeat(64)).put("questionCount", 10)
    }))
    private fun legacyFixture(): JSONObject = fixture().put("schemaVersion", 1).put("senseId", "cooked-trouble").also { json ->
        json.remove("packId")
        val questions = json.getJSONArray("questions")
        for (i in 0 until questions.length()) {
            val q = questions.getJSONObject(i)
            q.put("contextId", "legacy-$i").put("slang", "cooked").put("targetMeaning", "in serious trouble")
            q.getJSONArray("options").put(q.getInt("correctIndex"), "in serious trouble")
        }
    }

    @Test fun staticMixedFixtureParsesReferencesMetadataRepliesAndExactContentHash() {
        val text = fixtureText()
        val loaded = parseMixedBankSession(text, "mixed-fixture", hash(text))
        assertEquals("mixed-fixture", loaded.session.packId)
        assertEquals("mixed-fixture", loaded.session.senseId)
        assertEquals("github-static-llm", loaded.session.source)
        assertEquals("normalized-unreviewed", loaded.session.referenceStatus)
        assertEquals("fixture-v2", loaded.session.catalogVersion)
        assertEquals(hash(text), loaded.session.contentVersion)
        assertEquals(10, loaded.answers.size)
        val expectedSenseIds = fixture().getJSONArray("references").let { refs ->
            (0 until refs.length()).map { index -> refs.getJSONObject(index).getString("senseId") }.toSet()
        }
        assertEquals(expectedSenseIds, loaded.session.questions.map { it.senseId }.toSet())
        assertEquals(mapOf("easy" to 5, "medium" to 3, "hard" to 2),
            loaded.session.questions.groupingBy { it.difficulty }.eachCount())
        val posts = loaded.toFeedPosts()
        assertEquals(9, posts.map { it.context }.distinct().size)
        posts.zip(loaded.session.questions).forEach { (post, q) ->
            val answer = loaded.answers.getValue(q.id)
            assertEquals(q.targetMeaning, q.options[answer.correctIndex])
            assertEquals(answer.correctIndex, post.thread.correctIndex)
            assertEquals(q.post.comments.map { it.text }, SocialNarrative.story(post).replies.map { it.text })
            assertEquals(q.post.avatarId, post.avatarId)
            assertEquals("mixed-fixture/${hash(text)}/${q.id}", post.thread.id)
            assertTrue(post.published)
            assertTrue(post.sourceLabel.endsWith("normalized-unreviewed"))
        }
    }

    @Test fun mixedCatalogParsesDescriptorsAndPreservesLegacyConnectedTrainingSenses() {
        val catalog = parseBankCatalog(catalogJson().toString(), requireLlm = true)
        assertEquals(listOf(ApiPack("mixed-1", "a".repeat(64), 10)), catalog.packs)
        assertEquals("cooked-trouble", catalog.senses.single().senseId)
        assertEquals("cooked", catalog.senses.single().term)
        assertTrue(ApiCatalog("legacy", 0, emptyList()).packs.isEmpty())
        val legacy = JSONObject("""{"schemaVersion":1,"generationSource":"llm","senses":[
            {"senseId":"cooked-trouble","term":"cooked"}]}""")
        assertTrue(parseBankCatalog(legacy.toString(), true).packs.isEmpty())
    }

    @Test fun mixedCatalogRejectsInvalidDescriptorsDuplicatesAndOrigins() {
        for ((field, value) in listOf("packId" to "../escape", "packId" to "cooked-trouble",
                "packId" to "mixed-unsafe--id", "packId" to "mixed-${"a".repeat(80)}",
                "sha256" to "bad-hash", "questionCount" to 9)) {
            val json = catalogJson()
            json.getJSONArray("packs").getJSONObject(0).put(field, value)
            assertThrows(ApiException::class.java) { parseBankCatalog(json.toString(), true) }
        }
        val duplicate = catalogJson()
        duplicate.getJSONArray("packs").put(duplicate.getJSONArray("packs").getJSONObject(0))
        assertThrows(ApiException::class.java) { parseBankCatalog(duplicate.toString(), true) }
        for ((field, value) in listOf("schemaVersion" to 3, "source" to "backend", "generationSource" to "mock",
                "expertValidated" to true, "datasetVersion" to "", "bankVersion" to "")) {
            assertThrows(ApiException::class.java) { parseBankCatalog(catalogJson().put(field, value).toString(), true) }
        }
    }

    @Test fun mixedParsingReportsMalformedJsonAndMissingFieldsAsApiErrors() {
        assertThrows(ApiException::class.java) { parseMixedBankSession("not-json", "mixed-fixture") }
        assertThrows(ApiException::class.java) { parseBankCatalog("not-json", true) }
        for (field in listOf("promptVersion", "generatorModel", "generatedAt", "references", "questions"))
            reject { it.remove(field) }
        reject { it.getJSONArray("questions").getJSONObject(0).remove("options") }
        reject { it.put("schemaVersion", 3) }
        assertThrows(ApiException::class.java) { parseMixedBankSession(fixtureText(), "mixed-wrong") }
        assertThrows(ApiException::class.java) { parseBankSession(fixtureText(), "mixed-fixture", true) }
    }

    @Test fun mixedQuestionsMustCrossReferenceTheirOwnSenseTermMeaningAndAnswerKey() {
        for ((field, value) in listOf("senseId" to "missing-sense", "senseId" to "no-cap-truth",
                "slang" to "no cap", "targetMeaning" to "some other meaning", "correctIndex" to 1))
            reject { it.getJSONArray("questions").getJSONObject(0).put(field, value) }
        reject { it.getJSONArray("questions").getJSONObject(0).getJSONArray("options").put(0, "a wrong key") }
        reject { it.getJSONArray("questions").getJSONObject(0).remove("senseId") }
        reject { it.getJSONArray("questions").getJSONObject(0).remove("targetMeaning") }
        reject { it.getJSONArray("questions").getJSONObject(0).getJSONArray("options").put(0, "In serious trouble.") }
    }

    @Test fun mixedBindsTheTermToExactlyOneSurfaceAndMarksReplyTargets() {
        val replyTarget = fixture()
        val q = replyTarget.getJSONArray("questions").getJSONObject(0)
        q.put("questionTarget", "reply").put("targetHandle", "@friend")
            .put("question", "What does 'cooked' mean in @friend's reply?")
        q.getJSONObject("post").put("content", "My assignment vanished just before the deadline. I'm done.")
        q.getJSONObject("post").getJSONArray("comments").getJSONObject(0).put("text", "You're totally cooked, mate.")
        val loaded = parse(replyTarget)
        assertEquals("@friend", loaded.toFeedPosts().first().termTargetHandle)
        assertEquals("reply", loaded.session.questions.first().questionTarget)

        reject { it.getJSONArray("questions").getJSONObject(0).put("questionTarget", "reply") }
        reject { it.getJSONArray("questions").getJSONObject(0).put("questionTarget", "thread") }
        reject { it.getJSONArray("questions").getJSONObject(0).remove("questionTarget") }
        reject { it.getJSONArray("questions").getJSONObject(0).put("targetHandle", "@someone_else") }
        reject {
            val first = it.getJSONArray("questions").getJSONObject(0)
            first.put("question", "What does 'cooked' mean here?")
        }
        reject {
            val post = it.getJSONArray("questions").getJSONObject(0).getJSONObject("post")
            post.getJSONArray("comments").getJSONObject(0).put("text", "You're totally cooked, mate.")
        }
        reject {
            it.getJSONArray("questions").getJSONObject(0).getJSONObject("post")
                .put("content", "My assignment vanished. I'm cooked and so are you, cooked.")
        }
    }

    @Test fun mixedV7AllowsRepeatedTermsButRequiresAnUnambiguousTargetSurface() {
        val v7 = fixture().put("promptVersion", "mixed-v7")
        val q = v7.getJSONArray("questions").getJSONObject(0)
        val post = q.getJSONObject("post")
        val comments = post.getJSONArray("comments")
        comments.getJSONObject(0).put("text", "You are cooked, cooked!")
        q.put("questionComment", "Why cooked?")
        assertEquals("post", parse(v7).session.questions.first().questionTarget)

        q.put("questionTarget", "reply").put("targetHandle", "@friend")
            .put("question", "What does cooked mean in @friend's reply?")
        assertEquals("@friend", parse(v7).toFeedPosts().first().termTargetHandle)
        // v8 inherits the same repeat-and-bind contract.
        v7.put("promptVersion", "mixed-v8")
        assertEquals("@friend", parse(v7).toFeedPosts().first().termTargetHandle)
        v7.put("promptVersion", "mixed-v7")
        fun invalid(change: (JSONObject) -> Unit) {
            val copy = JSONObject(v7.toString())
            change(copy.getJSONArray("questions").getJSONObject(0))
            assertThrows(ApiException::class.java) { parse(copy) }
        }
        invalid { it.put("targetHandle", "") }
        invalid { it.put("targetHandle", "@missing") }
        invalid { it.put("questionTarget", "thread") }
        invalid { it.getJSONObject("post").getJSONArray("comments").getJSONObject(0).put("text", "No term here") }
        invalid { it.getJSONObject("post").getJSONArray("comments").put(JSONObject().put("handle", "@friend").put("text", "Different text")) }
        invalid { it.getJSONObject("post").getJSONArray("comments").put(JSONObject().put("handle", "@FRIEND").put("text", "Different text")) }
        invalid { it.put("question", "What does cooked mean in @friend_extra's reply?") }
        invalid { it.put("questionTarget", "post").put("targetHandle", "@student").put("question", "What does this poster mean?") }
        invalid { it.put("questionTarget", "post").put("targetHandle", "@friend").put("question", "What does cooked mean in this post?") }
        invalid { it.put("questionTarget", "post").put("targetHandle", "@student").put("question", "What does cooked mean in this post?")
            .getJSONObject("post").put("content", "No term here") }
        // Older mixed packs retain their exact-once rule, despite the same data being valid in v7.
        v7.put("promptVersion", "mixed-v5")
        assertThrows(ApiException::class.java) { parse(v7) }
    }

    @Test fun mixedRejectsDuplicateQuestionsReferencesAndIncompleteProvenance() {
        reject {
            val questions = it.getJSONArray("questions")
            questions.getJSONObject(1).put("id", questions.getJSONObject(0).getString("id"))
        }
        reject { it.getJSONArray("references").put(it.getJSONArray("references").getJSONObject(0)) }
        reject { it.getJSONArray("references").getJSONObject(0).put("referenceSources", JSONArray()) }
        reject { it.getJSONArray("references").getJSONObject(0).remove("sourceCandidateId") }
        reject { it.getJSONArray("references").getJSONObject(0).put("normalizedDefinition", "") }
        for ((field, value) in listOf("url" to "file:///fixture", "contentSha256" to "invalid", "quote" to ""))
            reject { it.getJSONArray("references").getJSONObject(0).getJSONArray("referenceSources").getJSONObject(0).put(field, value) }
    }

    @Test fun mixedRejectsDuplicateOrBlankAnswerKeysAndOutOfRangeIndexes() {
        reject { it.getJSONArray("questions").getJSONObject(0).getJSONArray("options").put(1, "IN SERIOUS TROUBLE.") }
        reject { it.getJSONArray("questions").getJSONObject(0).getJSONArray("options").put(1, "!!!") }
        reject { it.getJSONArray("questions").getJSONObject(0).put("correctIndex", 4) }
        reject { it.getJSONArray("questions").getJSONObject(0).put("correctIndex", -1) }
    }

    @Test fun mixedRequiresTenQuestionsAndFiveThreeTwoDifficulties() {
        reject { it.getJSONArray("questions").remove(9) }
        reject { it.getJSONArray("questions").put(it.getJSONArray("questions").getJSONObject(0)) }
        reject { it.getJSONArray("questions").getJSONObject(0).put("difficulty", "medium") }
        reject { it.getJSONArray("questions").getJSONObject(0).put("difficulty", "unsupported") }
    }

    @Test fun mixedRequiresTwoActualTermsNotMerelyTwoReferences() {
        reject { json ->
            val questions = json.getJSONArray("questions")
            for (i in 0 until questions.length()) {
                val q = questions.getJSONObject(i)
                q.put("slang", "cooked").put("senseId", "cooked-trouble").put("targetMeaning", "in serious trouble")
                q.getJSONArray("options").put(q.getInt("correctIndex"), "in serious trouble")
            }
        }
    }

    @Test fun mixedAllowsRepeatedContextIdsAndCategoriesButRejectsUnsupportedCategories() {
        val repeated = fixture()
        for (i in 0 until 10) repeated.getJSONArray("questions").getJSONObject(i)
            .put("contextId", "same-context").put("contextCategory", "work")
        assertEquals(10, parse(repeated).session.questions.size)
        assertEquals(setOf(FeedContext.Work), parse(repeated).toFeedPosts().map { it.context }.toSet())
        for (category in listOf("", "gaming", "unknown-category", "news", "personal-experience"))
            reject { it.getJSONArray("questions").getJSONObject(0).put("contextCategory", category) }
    }

    @Test fun legacyStillRequiresOneTermAndTenDistinctContexts() {
        val legacy = legacyFixture()
        assertEquals(10, parseBankSession(legacy.toString(), "cooked-trouble", true).session.questions.size)
        val duplicateContext = JSONObject(legacy.toString())
        duplicateContext.getJSONArray("questions").getJSONObject(1).put("contextId", "legacy-0")
        assertThrows(ApiException::class.java) { parseBankSession(duplicateContext.toString(), "cooked-trouble", true) }
        legacy.getJSONArray("questions").getJSONObject(1).put("slang", "no cap")
        assertThrows(ApiException::class.java) { parseBankSession(legacy.toString(), "cooked-trouble", true) }
    }

    @Test fun mixedNeverLoadsMockOrAnUnmatchedHash() {
        reject { it.put("generationSource", "mock") }
        reject { it.put("generationSource", "unknown") }
        reject { it.put("referenceStatus", "expert-validated") }
        assertThrows(ApiException::class.java) { parseMixedBankSession(fixtureText(), "mixed-fixture", "0".repeat(64)) }
        val text = fixtureText()
        assertThrows(ApiException::class.java) { parseMixedBankSession("$text ", "mixed-fixture", hash(text)) }
    }

    @Test fun selectionPrefersOnlyMixedPacksAndBoundsBothFormatsAtTwelve() {
        val catalog = parseBankCatalog(catalogJson(20).toString(), true)
        val mixed = selectPublishedPacks(catalog)
        assertEquals((1..12).map { "mixed-$it" }, mixed.map { it.id })
        assertTrue(mixed.all { it.legacyTerm == null && it.sha256 == "a".repeat(64) })
        val sixSenses = (1..6).map { ApiSense("legacy-$it", "term-$it", "provisional", "b".repeat(64)) }
        val legacy = catalog.copy(packs = emptyList(), senses = sixSenses)
        assertEquals(sixSenses.map { it.senseId }, selectPublishedPacks(legacy).map { it.id })
        assertEquals(sixSenses.map { it.term }, selectPublishedPacks(legacy).map { it.legacyTerm })
        assertTrue(selectPublishedPacks(legacy).all { it.sha256 == "b".repeat(64) })
        assertEquals(12, selectPublishedPacks(legacy.copy(senses = (1..20).map {
            ApiSense("legacy-$it", "term-$it", "provisional")
        })).size)
    }

    @Test fun mergeShufflesTheWholeFeedDeterministicallyWithoutChangingPerPackKeys() {
        val first = parse()
        val second = parse(fixture().put("packId", "mixed-second"))
        val sessions = listOf(first, second)
        val original = sessions.flatMap { it.toFeedPosts() }
        val shuffled = mergePublishedFeed(sessions, Random(42))
        assertEquals(original.shuffled(Random(42)), shuffled)
        assertEquals(shuffled, mergePublishedFeed(listOf(parse(), parse(fixture().put("packId", "mixed-second"))), Random(42)))
        assertNotEquals(original.map { it.thread.id }, shuffled.map { it.thread.id })
        assertEquals(original.toSet(), shuffled.toSet())
        assertEquals(20, shuffled.map { it.thread.id }.distinct().size)
        assertEquals(10, first.session.questions.size)
        assertThrows(IllegalArgumentException::class.java) { mergePublishedFeed(listOf(first, first), Random(42)) }
    }

    @Test fun savedOrderRestoresWithoutAnyRandomnessAndRefreshCanCreateANewOrder() {
        val sessions = listOf(parse(), parse(fixture().put("packId", "mixed-second")))
        val first = mergePublishedFeed(sessions, Random(42))
        val noShuffle = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("Restoring order must not shuffle")
        }
        assertEquals(first, mergePublishedFeed(sessions.reversed(), noShuffle, first.map { it.thread.id }))
        val refreshed = mergePublishedFeed(sessions, Random(84))
        assertNotEquals(first.map { it.thread.id }, refreshed.map { it.thread.id })
        assertEquals(refreshed, mergePublishedFeed(sessions, Random(84), listOf("stale-id")))
    }

    @Test fun ambiguousChoicesRejectTheWholeReplacementWithCountAndKeepPreviousProgress() {
        val valid = parse()
        val questions = valid.session.questions.toMutableList()
        questions[0] = questions[0].copy(options = listOf("in serious trouble", "in serious trouble.", "doing well", "taking a break"))
        questions[1] = questions[1].copy(options = listOf("He said “yes.”", "He said “yes”", "Other", "Third"))
        val invalid = valid.copy(session = valid.session.copy(questions = questions))
        val oldPosts = mergePublishedFeed(listOf(valid), Random(42))
        var progress = FeedPracticeProgress(posts = oldPosts).answer(oldPosts.first().thread.id, 0)
        val before = progress
        val error = assertThrows(ApiException::class.java) {
            progress = progress.copy(posts = mergePublishedFeed(listOf(invalid), Random(42)))
        }
        assertTrue(error.message!!.contains("2"))
        assertTrue(error.message!!.contains("q1"))
        assertTrue(error.message!!.contains("q2"))
        assertEquals(before, progress)
        assertEquals(oldPosts.size, progress.posts.size)
        assertEquals(1, progress.completedCount)
        assertEquals(10, mergePublishedFeed(listOf(valid), Random(42), oldPosts.map { it.thread.id }).size)
    }

    @Test fun collidingRawIdsAcrossPacksAndHashRevisionsCannotShareAnswersOrFarmXp() {
        val first = parse()
        val secondJson = fixture().put("packId", "mixed-second")
        val q = secondJson.getJSONArray("questions").getJSONObject(0)
        q.put("options", JSONArray(listOf("feeling hungry", "in serious trouble", "doing well", "taking a break")))
            .put("correctIndex", 1)
        val second = parse(secondJson)
        val posts = mergePublishedFeed(listOf(first, second), Random(42))
        val a = first.toFeedPosts().first().thread
        val b = second.toFeedPosts().first().thread
        assertEquals(first.session.questions.first().id, second.session.questions.first().id)
        assertNotEquals(a.id, b.id)
        assertNotEquals(a.correctIndex, b.correctIndex)
        val answered = FeedPracticeProgress(posts = posts).answer(a.id, 0).answer(b.id, 0)
        assertEquals(95, answered.xp)
        assertEquals(2, answered.completedCount)
        assertEquals(answered, answered.answer(a.id, 1).answer(b.id, 1))
        assertEquals(80, gradeStaticAnswer(first.answers, "q1", 0, 0, 0).xpEarned)
        assertEquals(15, gradeStaticAnswer(second.answers, "q1", 0, 0, 0).xpEarned)
        val revision = parse(fixture().put("revision", "new bytes"))
        val replaced = answered.copy(posts = mergePublishedFeed(listOf(revision, second), Random(42)))
        assertEquals(15, replaced.xp)
        assertEquals(1, replaced.completedCount)
        assertEquals(answered.xp, answered.copy(posts = mergePublishedFeed(listOf(first, second), Random(84))).xp)
    }

    @Test fun successfulLoaderFetchesAllSixLegacyPacksOrAtMostTwelveMixedPacks() = runBlocking {
        val catalog = parseBankCatalog(catalogJson(20).toString(), true)
        val mixedRequests = mutableListOf<PublishedPackRequest>()
        val mixed = loadPublishedSessions(catalog) { request ->
            mixedRequests += request
            parse(fixture().put("packId", request.id))
        }
        assertEquals(12, mixedRequests.size)
        assertEquals(120, mergePublishedFeed(mixed, Random(42)).size)
        val legacyCatalog = catalog.copy(packs = emptyList(), senses = (1..6).map {
            ApiSense("legacy-$it", "cooked", "provisional", "b".repeat(64))
        })
        val legacyRequests = mutableListOf<PublishedPackRequest>()
        val legacy = loadPublishedSessions(legacyCatalog) { request ->
            legacyRequests += request
            parseBankSession(legacyFixture().put("senseId", request.id).toString(), request.id, true, request.legacyTerm)
        }
        assertEquals((1..6).map { "legacy-$it" }, legacyRequests.map { it.id })
        assertEquals(60, mergePublishedFeed(legacy, Random(42)).size)
    }

    @Test fun failedMixedLoadReturnsNoPartialReplacementAndNeverFallsBackToLegacy() {
        val catalog = parseBankCatalog(catalogJson(3).toString(), true)
        val loadedBefore = parse().toFeedPosts()
        var progress = FeedPracticeProgress(posts = loadedBefore).answer(loadedBefore.first().thread.id, 0)
        val before = progress
        val requests = mutableListOf<PublishedPackRequest>()
        val error = assertThrows(ApiException::class.java) {
            runBlocking {
                val sessions = loadPublishedSessions(catalog) { request ->
                    requests += request
                    if (request.id == "mixed-2") throw ApiException("checksum mismatch")
                    parse(fixture().put("packId", request.id))
                }
                progress = progress.copy(posts = mergePublishedFeed(sessions, Random(42)))
            }
        }
        assertEquals(listOf("mixed-1", "mixed-2"), requests.map { it.id })
        assertTrue(requests.all { it.legacyTerm == null })
        assertTrue(error.message!!.contains("mixed-2"))
        assertEquals(before, progress)
    }

    @Test fun emptyOrMockCatalogsFailExplicitlyAndCancellationPropagates() {
        val empty = parseBankCatalog(catalogJson(0).put("senses", JSONArray()).toString(), true)
        assertThrows(ApiException::class.java) { runBlocking { loadPublishedSessions(empty) { error("Must not fetch") } } }
        assertThrows(ApiException::class.java) {
            runBlocking { loadPublishedSessions(empty.copy(generationSource = "mock")) { error("Must not fetch") } }
        }
        val cancelled = CancellationException("cancelled refresh")
        val error = assertThrows(CancellationException::class.java) {
            runBlocking { loadPublishedSessions(parseBankCatalog(catalogJson().toString(), true)) { throw cancelled } }
        }
        assertSame(cancelled, error)
    }

    /**
     * Guards the recorded, immutable mixed-v8 attempt: the very bytes that were validated upstream
     * must also pass this client's stricter parser before anything is published. Kept as a specific
     * record instead of a moving target, because that attempt can never be regenerated.
     */
    @Test fun recordedMixedV8AttemptParsesInThisClientBeforeAnyPublish() {
        val pack = File(System.getProperty("slangverse.repositoryRoot"),
            "backend/experiments/mixed-20261118t133133119z-49697039-2c5d-4d6a-86fb-2ebfcc0d9fa3/pack.json")
        Assume.assumeTrue("Recorded mixed-v8 attempt is not present in this checkout", pack.isFile)
        val text = pack.readText(Charsets.UTF_8)
        val loaded = parseMixedBankSession(text, "mixed-20261118t133133119z-49697039-2c5d-4d6a-86fb-2ebfcc0d9fa3")
        assertEquals("mixed-v8", JSONObject(text).getString("promptVersion"))
        assertEquals(10, loaded.answers.size)
        assertEquals(mapOf("easy" to 5, "medium" to 3, "hard" to 2),
            loaded.session.questions.groupingBy { it.difficulty }.eachCount())
        assertEquals(10, mergePublishedFeed(listOf(loaded)).size)
        // Observation recorded in docs: this attempt binds every term to the post body, so the reply
        // highlight has nothing to show. Not asserted as a contract - a later pack may mix targets.
        assertTrue(loaded.toFeedPosts().all { post -> post.termTargetHandle == null })
    }
}
