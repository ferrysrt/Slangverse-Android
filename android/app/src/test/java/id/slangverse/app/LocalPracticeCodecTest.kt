package id.slangverse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPracticeCodecTest {

    @Test
    fun roundTripsAnswersInTheOrderTheyWereGiven() {
        val snapshot = LocalPracticeSnapshot(
            answers = listOf("pack-a/1/q1" to 2, "pack-a/1/q2" to 0, "pack-b/1/q7" to 3),
            liked = listOf("pack-a/1/q1"),
            saved = listOf("pack-b/1/q7", "pack-a/1/q2")
        )

        val restored = LocalPracticeCodec.decode(LocalPracticeCodec.encode(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun missingOrUnreadableStateGivesAnEmptySnapshot() {
        val empty = LocalPracticeSnapshot()

        assertEquals(empty, LocalPracticeCodec.decode(null))
        assertEquals(empty, LocalPracticeCodec.decode(""))
        assertEquals(empty, LocalPracticeCodec.decode("   "))
        assertEquals(empty, LocalPracticeCodec.decode("{not json"))
        assertEquals(empty, LocalPracticeCodec.decode("[]"))
    }

    @Test
    fun aSnapshotFromAnotherVersionIsIgnored() {
        val older = """{"version":0,"answers":[{"post":"p","selected":1}],"liked":["p"],"saved":[]}"""

        assertEquals(LocalPracticeSnapshot(), LocalPracticeCodec.decode(older))
    }

    @Test
    fun brokenEntriesAreDroppedInsteadOfWalkingTheFeed() {
        val raw = """
            {"version":1,"answers":[
              {"post":"good","selected":2},
              {"post":"","selected":1},
              {"post":"dup","selected":0},
              {"post":"dup","selected":3},
              {"post":"bad-index","selected":9},
              {"post":"negative","selected":-1},
              {"post":"no-selection"},
              "not an object"
            ],"liked":["a","a","","b"],"saved":null}
        """.trimIndent()

        val restored = LocalPracticeCodec.decode(raw)

        assertEquals(listOf("good" to 2, "dup" to 0), restored.answers)
        assertEquals(listOf("a", "b"), restored.liked)
        assertTrue(restored.saved.isEmpty())
    }

    @Test
    fun oversizedStateIsCappedSoOneBadFileCannotGrowForever() {
        val answers = (0 until 6_000).map { "post-$it" to it % 4 }
        val liked = (0 until 25_000).map { "post-$it" }

        val restored = LocalPracticeCodec.decode(
            LocalPracticeCodec.encode(LocalPracticeSnapshot(answers, liked))
        )

        assertEquals(5_000, restored.answers.size)
        assertEquals(20_000, restored.liked.size)
        assertEquals("post-0" to 0, restored.answers.first())
    }
}
