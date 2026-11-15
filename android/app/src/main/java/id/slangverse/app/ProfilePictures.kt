package id.slangverse.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Only bundled IDs are accepted. Character handles always keep their canonical face. */
internal fun resolveProfilePictureId(requestedId: String? = null, handle: String = ""): String {
    val normalized = handle.trim().lowercase(Locale.ROOT)
    ProfilePictureCatalog.handles[normalized]?.let { return it }
    if (requestedId != null && requestedId in ProfilePictureCatalog.ids) return requestedId
    if (normalized.isEmpty()) return "default"
    // FNV-1a over UTF-8; mirrored in backend/src/profilePictures.mjs.
    var hash = 2166136261u
    normalized.toByteArray(Charsets.UTF_8).forEach {
        hash = (hash xor (it.toInt() and 255).toUInt()) * 16777619u
    }
    return ProfilePictureCatalog.fallbackIds[(hash % ProfilePictureCatalog.fallbackIds.size.toUInt()).toInt()]
}

@Composable
internal fun ProfilePicture(
    handle: String, avatarId: String? = null, size: Dp = 36.dp,
    contentDescription: String? = null
) {
    val resolved = resolveProfilePictureId(avatarId, handle)
    Image(painterResource(ProfilePictureCatalog.drawable(resolved)), contentDescription,
        modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}
