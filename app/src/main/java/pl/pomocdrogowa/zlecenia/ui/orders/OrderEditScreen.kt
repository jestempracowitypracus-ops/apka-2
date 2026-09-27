package pl.pomocdrogowa.zlecenia.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.data.OrderDraft
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.Role
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.data.toDraft
import pl.pomocdrogowa.zlecenia.ui.CenteredLoader
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.ui.common.toUserMessage

/** Tworzenie (orderId == null) lub edycja zlecenia przez dyspozytora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditScreen(me: UserProfile, orderId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<OrderDraft?>(if (orderId == null) OrderDraft() else null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val usersFlow = remember { Repository.allUsers() }
    val users by usersFlow.collectAsState(initial = emptyList())
    val drivers = users.filter { it.active && it.role == Role.DRIVER }

    LaunchedEffect(orderId) {
        if (orderId != null) draft = Repository.order(orderId).first()?.toDraft() ?: OrderDraft()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (orderId == null) "Nowe zlecenie" else "Edycja zlecenia") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
    ) { padding ->
        val d = draft
        if (d == null) {
            Box(Modifier.padding(padding)) { CenteredLoader() }
            return@Scaffold
        }
        fun update(block: OrderDraft.() -> OrderDraft) {
            draft = (draft ?: d).block()
        }
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Kierowca", style = MaterialTheme.typography.titleSmall)
            DriverPicker(
                drivers = drivers,
                selectedName = d.driverName,
                onSelect = { u -> update { copy(driverId = u?.uid, driverName = u?.name.orEmpty()) } },
            )

            Text("Klient", style = MaterialTheme.typography.titleSmall)
            Field(d.clientName, { v -> update { copy(clientName = v) } }, "Imię i nazwisko / firma", Modifier.fillMaxWidth())
            Field(
                d.clientPhone, { v -> update { copy(clientPhone = v) } }, "Telefon klienta",
                Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone,
            )

            Text("Pojazd", style = MaterialTheme.typography.titleSmall)
            Field(d.vehicle, { v -> update { copy(vehicle = v) } }, "Marka i model", Modifier.fillMaxWidth())
            Field(d.plate, { v -> update { copy(plate = v) } }, "Nr rejestracyjny", Modifier.fillMaxWidth())

            Text("Trasa", style = MaterialTheme.typography.titleSmall)
            Field(
                d.pickupAddress, { v -> update { copy(pickupAddress = v) } }, "Miejsce zdarzenia / odbioru",
                Modifier.fillMaxWidth(), singleLine = false,
            )
            Field(
                d.destinationAddress, { v -> update { copy(destinationAddress = v) } }, "Dokąd dowieźć",
                Modifier.fillMaxWidth(), singleLine = false,
            )

            Field(
                d.description, { v -> update { copy(description = v) } }, "Opis zlecenia / uwagi",
                Modifier.fillMaxWidth(), singleLine = false, minLines = 3,
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching {
                                if (orderId == null) Repository.createOrder(d, me)
                                else Repository.updateOrder(orderId, d).let { orderId }
                            }.onSuccess(onSaved).onFailure { error = it.toUserMessage() }
                            busy = false
                        }
                    },
                    enabled = d.pickupAddress.isNotBlank() || d.description.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (orderId == null) "Wyślij zlecenie" else "Zapisz zmiany") }
            }
        }
    }
}

@Composable
private fun DriverPicker(drivers: List<UserProfile>, selectedName: String, onSelect: (UserProfile?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedName.ifBlank { "Wybierz kierowcę" }, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("— bez kierowcy —") }, onClick = { onSelect(null); open = false })
            drivers.forEach { driver ->
                DropdownMenuItem(
                    text = {
                        Text(driver.name + if (driver.vehicle.isNotBlank()) " (${driver.vehicle})" else "")
                    },
                    onClick = { onSelect(driver); open = false },
                )
            }
            if (drivers.isEmpty()) {
                DropdownMenuItem(text = { Text("Brak kierowców – dodaj ich w zakładce Kierowcy") }, onClick = { open = false })
            }
        }
    }
}
