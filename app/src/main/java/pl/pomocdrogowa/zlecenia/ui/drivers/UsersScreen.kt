package pl.pomocdrogowa.zlecenia.ui.drivers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.Role
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.ui.common.toUserMessage
import pl.pomocdrogowa.zlecenia.util.dial

/** Lista kont (kierowcy i dyspozytorzy) - dostępna tylko dla dyspozytora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val usersFlow = remember { Repository.allUsers() }
    val users by usersFlow.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kierowcy i dyspozytorzy") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.PersonAdd, null) },
                text = { Text("Dodaj konto") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.uid }) { user ->
                Card(Modifier.fillMaxWidth().clickable { editing = user }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(
                                    user.role.label,
                                    if (user.role == Role.DRIVER) user.availability.label else "",
                                    user.vehicle,
                                    if (user.active) "" else "WYŁĄCZONE",
                                )
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            Text(user.email, style = MaterialTheme.typography.bodySmall)
                        }
                        if (user.phone.isNotBlank()) {
                            IconButton(onClick = { dial(context, user.phone) }) { Icon(Icons.Filled.Call, "Zadzwoń") }
                        }
                    }
                }
            }
        }
    }

    if (adding) AddUserDialog(onDismiss = { adding = false })
    editing?.let { EditUserDialog(it, onDismiss = { editing = null }) }
}

@Composable
private fun AddUserDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.DRIVER) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Nowe konto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RolePicker(role) { role = it }
                Field(name, { name = it }, "Imię i nazwisko", Modifier.fillMaxWidth())
                Field(email, { email = it }, "E-mail (login)", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
                Field(password, { password = it }, "Hasło startowe (min. 6 znaków)", Modifier.fillMaxWidth())
                Field(phone, { phone = it }, "Telefon", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
                if (role == Role.DRIVER) {
                    Field(vehicle, { vehicle = it }, "Pojazd (np. laweta WX 12345)", Modifier.fillMaxWidth())
                }
                Text(
                    "Przekaż kierowcy e-mail i hasło – zaloguje się nimi w aplikacji.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { Repository.createUser(context, name, email, password, phone, vehicle, role) }
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.toUserMessage() }
                        busy = false
                    }
                },
            ) { Text("Utwórz") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Anuluj") } },
    )
}

@Composable
private fun EditUserDialog(user: UserProfile, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }
    var vehicle by remember { mutableStateOf(user.vehicle) }
    var role by remember { mutableStateOf(user.role) }
    var active by remember { mutableStateOf(user.active) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.email) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RolePicker(role) { role = it }
                Field(name, { name = it }, "Imię i nazwisko", Modifier.fillMaxWidth())
                Field(phone, { phone = it }, "Telefon", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
                Field(vehicle, { vehicle = it }, "Pojazd", Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Konto aktywne", modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        Repository.updateUser(
                            user.uid,
                            mapOf(
                                "name" to name.trim(),
                                "phone" to phone.trim(),
                                "vehicle" to vehicle.trim(),
                                "role" to role.key,
                                "active" to active,
                            ),
                        )
                    }.onSuccess { onDismiss() }.onFailure { error = it.toUserMessage() }
                }
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun RolePicker(role: Role, onChange: (Role) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Role.entries.forEach { r ->
            FilterChip(selected = role == r, onClick = { onChange(r) }, label = { Text(r.label) })
        }
    }
}
