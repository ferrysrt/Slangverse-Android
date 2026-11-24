package id.slangverse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParticipantAppUpdateTest {
    private val packageName = "id.slangverse.app.staging"

    @Test
    fun onlyReportsNewerVersionsForTheInstalledPackage() {
        assertNull(parseParticipantAppUpdate("""{"available":false}""", packageName, 14))
        assertNull(parseParticipantAppUpdate(manifest(14), packageName, 14))
        assertNull(parseParticipantAppUpdate(manifest(13), packageName, 14))
        assertEquals(ParticipantAppUpdate("0.8.5", "https://example.org/update"),
            parseParticipantAppUpdate(manifest(15), packageName, 14))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInsecureDownloadPage() {
        parseParticipantAppUpdate(manifest(15, "http://example.org/update"), packageName, 14)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnotherApplicationPackage() {
        parseParticipantAppUpdate(manifest(15), "id.slangverse.app", 14)
    }

    private fun manifest(code: Int, url: String = "https://example.org/update") =
        """{"available":true,"versionCode":$code,"versionName":"0.8.5","packageName":"$packageName","downloadPage":"$url"}"""
}
