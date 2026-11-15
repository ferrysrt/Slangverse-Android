package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class ProfilePicturesTest {
    @Test fun castHasDistinctBundledPortraitsAcrossAllIdentitySurfaces() {
        val profiles = SocialNarrative.profiles.values
        assertEquals(profiles.size, profiles.map { it.avatarId }.distinct().size)
        profiles.forEach { profile ->
            assertTrue(profile.avatarId in ProfilePictureCatalog.ids)
            assertEquals(profile.avatarId, resolveProfilePictureId(handle = profile.handle))
            assertEquals(profile.avatarId, resolveProfilePictureId("guest_01", profile.handle))
            assertEquals(profile.avatarId, resolveProfilePictureId(handle = " ${profile.handle.uppercase()} "))
        }
        assertEquals(22, ProfilePictureCatalog.ids.size)
        assertEquals(22, ProfilePictureCatalog.ids.map { ProfilePictureCatalog.drawable(it) }.distinct().size)
    }

    @Test fun generatedHandlesAcceptOnlyBundledIdsAndOldPacksHaveStableFallbacks() {
        assertEquals("guest_03", resolveProfilePictureId("guest_03", "@newperson"))
        // Shared expected values also verified by the backend tests.
        assertEquals("guest_03", resolveProfilePictureId(handle = "@someone-new"))
        assertEquals("guest_08", resolveProfilePictureId(handle = "@visitor"))
        assertEquals(resolveProfilePictureId(handle = "@visitor"), resolveProfilePictureId("https://example.com/face.png", "@visitor"))
        assertEquals(resolveProfilePictureId(handle = "@visitor"), resolveProfilePictureId("../../secret", "@visitor"))
        assertEquals("default", resolveProfilePictureId())
        assertEquals(ProfilePictureCatalog.drawable("default"), ProfilePictureCatalog.drawable("unknown"))
    }
}
