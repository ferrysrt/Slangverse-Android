package id.slangverse.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class StagingAccountState(private val client: StagingAccountClient) {
    var session by mutableStateOf<StagingSession?>(null)
        private set
    var progress by mutableStateOf<StagingProgress?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    suspend fun signIn(email: String, password: String) = runRequest {
        progress = null
        val signedIn = client.signIn(email, password)
        session = signedIn
        progress = client.progress(signedIn)
    }

    suspend fun refresh() = runRequest {
        val signedIn = session ?: return@runRequest
        progress = client.progress(signedIn)
    }

    fun signOut() {
        if (busy) return
        session = null
        progress = null
        notice = null
    }

    private suspend fun runRequest(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: StagingAccountError) {
            if (error.sessionExpired) {
                session = null
                progress = null
            }
            notice = error.message
        } catch (_: Exception) {
            notice = "Could not reach the staging account service. Try again."
        } finally {
            busy = false
        }
    }
}

@Composable
internal fun StagingAccountPanel(account: StagingAccountState) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Account preview", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val session = account.session
            if (session == null) {
                Text("Sign in with a staging test account.", color = WarmEditorial.Muted, fontSize = 12.sp)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") },
                    singleLine = true, enabled = !account.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
                    singleLine = true, enabled = !account.busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Button(enabled = !account.busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
                    val enteredEmail = email
                    val enteredPassword = password
                    password = ""
                    scope.launch { account.signIn(enteredEmail, enteredPassword) }
                }) { Text("Sign in") }
            } else {
                Text(session.email, fontWeight = FontWeight.SemiBold)
                account.progress?.let { Text("Account totals · ${it.answered} answered · ${it.correct} correct · ${it.xp} XP") }
                Text("Practice on this phone is stored locally during the preview.", fontSize = 12.sp, color = WarmEditorial.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !account.busy, onClick = { scope.launch { account.refresh() } }) { Text("Refresh account") }
                    TextButton(enabled = !account.busy, onClick = account::signOut) { Text("Sign out") }
                }
            }
            if (account.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            account.notice?.let { Text(it, color = WarmEditorial.CoralDeep, fontSize = 12.sp) }
        }
    }
}
