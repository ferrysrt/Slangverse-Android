package id.slangverse.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val UPDATE_MANIFEST = "https://slangverse-research-admin.vercel.app/api/app-update"

internal data class ParticipantAppUpdate(val versionName: String, val downloadPage: String)

internal fun parseParticipantAppUpdate(manifest: String, packageName: String, installedCode: Int): ParticipantAppUpdate? {
    val json = JSONObject(manifest)
    if (!json.optBoolean("available")) return null
    val code = json.opt("versionCode")
    require(code is Int && code in 1..2_100_000_000)
    require(json.getString("packageName") == packageName)
    if (code <= installedCode) return null
    val versionName = json.getString("versionName")
    require(versionName.matches(Regex("[0-9][A-Za-z0-9.+-]{0,49}")))
    val page = URI(json.getString("downloadPage"))
    require(page.scheme == "https" && page.host != null && page.userInfo == null)
    return ParticipantAppUpdate(versionName, page.toASCIIString())
}

private suspend fun checkParticipantAppUpdate(): ParticipantAppUpdate? = withContext(Dispatchers.IO) {
    val connection = URL(UPDATE_MANIFEST).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        check(connection.responseCode == HttpURLConnection.HTTP_OK)
        val text = connection.inputStream.use { input ->
            val buffer = ByteArray(8193)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size < buffer.size)
            buffer.decodeToString(endIndex = size)
        }
        parseParticipantAppUpdate(text, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE)
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun ParticipantAppUpdateSetting() {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<ParticipantAppUpdate?>(null) }
    EditorialSetting("Check for app update", if (busy) "Checking?" else "Check") {
        if (!busy) {
            busy = true
            notice = null
            scope.launch {
                try {
                    update = checkParticipantAppUpdate()
                    if (update == null) notice = "No newer version is available."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    notice = "Could not check for updates. Try again later."
                } finally {
                    busy = false
                }
            }
        }
    }
    notice?.let { Text(it, fontSize = 12.sp) }
    update?.let { available ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Slangverse ${available.versionName} is available") },
            text = { Text("Open the download page to get the APK. Android will ask before installing it.") },
            confirmButton = {
                TextButton(onClick = {
                    update = null
                    try {
                        uriHandler.openUri(available.downloadPage)
                    } catch (_: IllegalArgumentException) {
                        notice = "Could not open the download page."
                    }
                }) { Text("Open download page") }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Later") } }
        )
    }
}
