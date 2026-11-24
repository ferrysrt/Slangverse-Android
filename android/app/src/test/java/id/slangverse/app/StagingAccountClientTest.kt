package id.slangverse.app

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingAccountClientTest {
    @Test
    fun configurationRequiresProjectOriginAndPublishableKey() {
        val key = "sb_publishable_example"
        assertEquals("https://example.supabase.co", StagingAccountConfig.create(" https://example.supabase.co/ ", key)?.url)
        assertNull(StagingAccountConfig.create("", ""))
        assertNull(StagingAccountConfig.create("http://example.supabase.co", key))
        assertNull(StagingAccountConfig.create("https://example.supabase.co.invalid", key))
        assertNull(StagingAccountConfig.create("https://example.supabase.co/redirect", key))
        assertNull(StagingAccountConfig.create("https://example.supabase.co", "sb_secret_example"))
    }

    @Test
    fun progressUsesTheSignedInUsersAccessToken() = runBlocking {
        var requests = 0
        val client = StagingAccountClient { path, body, token ->
            when (requests++) {
                0 -> {
                    assertEquals("/auth/v1/token?grant_type=password", path)
                    assertNull(token)
                    assertEquals("learner@example.test", JSONObject(body).getString("email"))
                    assertEquals(" password ", JSONObject(body).getString("password"))
                    signedIn
                }
                1 -> {
                    assertEquals("/rest/v1/rpc/my_practice_progress", path)
                    assertEquals("{}", body)
                    assertEquals("header.payload.signature", token)
                    totals
                }
                else -> error("Unexpected request")
            }
        }
        val session = client.signIn(" learner@example.test ", " password ")
        assertEquals("learner@example.test", session.email)
        assertEquals(StagingProgress(2, 1, 95), client.progress(session))
        assertEquals(2, requests)
    }

    @Test
    fun rejectedSignInDoesNotExposeResponseBody() {
        val client = StagingAccountClient { _, _, _ -> StagingResponse(400, "private provider response") }
        val error = assertThrows(StagingAccountError::class.java) {
            runBlocking { client.signIn("learner@example.test", "password") }
        }
        assertEquals("Sign-in failed. Check the test account and password.", error.message)
        assertFalse(error.sessionExpired)
    }

    @Test
    fun missingSchemaAllowsRefreshAndExpiredSessionClearsProgress() = runBlocking {
        var progressResponse = StagingResponse(404, "private provider response")
        val state = StagingAccountState(StagingAccountClient { path, _, _ ->
            if (path.startsWith("/auth/")) signedIn else progressResponse
        })
        state.signIn("learner@example.test", "password")
        assertEquals("learner@example.test", state.session?.email)
        assertEquals("The staging database setup has not been applied yet.", state.notice)
        assertNull(state.progress)

        progressResponse = totals
        state.refresh()
        assertEquals(StagingProgress(2, 1, 95), state.progress)
        assertNull(state.notice)

        progressResponse = StagingResponse(401, "private provider response")
        state.refresh()
        assertNull(state.session)
        assertNull(state.progress)
        assertEquals("Session expired. Sign in again.", state.notice)
        assertFalse(state.busy)
    }

    @Test
    fun signOutClearsSessionAndDoesNotFetchProgressAgain() = runBlocking {
        var requests = 0
        val state = StagingAccountState(StagingAccountClient { path, _, _ ->
            requests++
            if (path.startsWith("/auth/")) signedIn else totals
        })
        state.signIn("learner@example.test", "password")
        assertTrue(state.progress != null)
        state.signOut()
        state.refresh()
        assertNull(state.session)
        assertNull(state.progress)
        assertNull(state.notice)
        assertEquals(2, requests)
    }

    private val signedIn = StagingResponse(
        200,
        """{"access_token":"header.payload.signature","user":{"email":"learner@example.test"}}"""
    )
    private val totals = StagingResponse(200, """[{"answered":2,"correct":1,"xp":95}]""")
}
