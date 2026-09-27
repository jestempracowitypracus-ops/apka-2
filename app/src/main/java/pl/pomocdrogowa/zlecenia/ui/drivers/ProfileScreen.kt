package pl.pomocdrogowa.zlecenia.ui.drivers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.BuildConfig
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.ui.common.toUserMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(me: UserProfile, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(me.name) }
    var phone by remember { mutableStateOf(me.phone) }
    var vehicle by remember { mutableStateOf(me.vehicle) }
    var info by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mój profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(me.email, style = MaterialTheme.typography.titleMedium)
            Text(me.role.label, color = MaterialTheme.colorScheme.primary)
            Field(name, { name = it }, "Imię i nazwisko", Modifier.fillMaxWidth())
            Field(phone, { phone = it }, "Telefon", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
            Field(vehicle, { vehicle = it }, "Pojazd", Modifier.fillMaxWidth())
            info?.let { Text(it) }
            Button(
                onClick = {
                    scope.launch {
                        info = runCatching {
                            Repository.updateUser(
                                me.uid,
                                mapOf("name" to name.trim(), "phone" to phone.trim(), "vehicle" to vehicle.trim()),
                            )
                        }.fold({ "Zapisano" }, { it.toUserMessage() })
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zapisz") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        info = runCatching { Repository.sendPasswordReset(me.email) }
                            .fold({ "Wysłano link do zmiany hasła na ${me.email}" }, { it.toUserMessage() })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zmień hasło") }
            OutlinedButton(
                onClick = { scope.launch { Repository.signOut() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Wyloguj") }
            Text(
                "Wersja ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
