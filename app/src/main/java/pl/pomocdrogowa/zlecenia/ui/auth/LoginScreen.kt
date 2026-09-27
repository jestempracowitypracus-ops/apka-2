package pl.pomocdrogowa.zlecenia.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.ui.common.toUserMessage

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    // null = sprawdzamy, true = pierwsze uruchomienie (brak dyspozytora)
    var firstRun by remember { mutableStateOf<Boolean?>(null) }
    var registerMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        firstRun = runCatching { !Repository.isSetupDone() }.getOrDefault(false)
        if (firstRun == true) registerMode = true
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            info = null
            runCatching { block() }.onFailure { info = it.toUserMessage() }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.LocalShipping,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text("Pomoc Drogowa", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (registerMode) "Pierwsze uruchomienie – załóż konto dyspozytora" else "Zaloguj się",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        if (registerMode) {
            Field(name, { name = it }, "Imię i nazwisko", Modifier.fillMaxWidth())
            Field(phone, { phone = it }, "Telefon", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
        }
        Field(email, { email = it }, "E-mail", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
        Field(password, { password = it }, "Hasło", Modifier.fillMaxWidth(), password = true)

        info?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

        if (busy) {
            CircularProgressIndicator()
        } else if (registerMode) {
            Button(
                onClick = { run { Repository.registerFirstDispatcher(name, email, password, phone) } },
                enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Załóż konto dyspozytora") }
            TextButton(onClick = { registerMode = false }) { Text("Mam już konto – zaloguj") }
        } else {
            Button(
                onClick = { run { Repository.signIn(email, password) } },
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zaloguj") }
            TextButton(
                onClick = {
                    run {
                        Repository.sendPasswordReset(email)
                        info = "Wysłano link do zmiany hasła na $email"
                    }
                },
                enabled = email.isNotBlank(),
            ) { Text("Nie pamiętam hasła") }
            if (firstRun == true) {
                TextButton(onClick = { registerMode = true }) { Text("Pierwsze uruchomienie – załóż konto") }
            } else {
                Text(
                    "Konta kierowców zakłada dyspozytor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
