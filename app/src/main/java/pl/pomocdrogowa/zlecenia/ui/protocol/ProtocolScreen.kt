package pl.pomocdrogowa.zlecenia.ui.protocol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.pomocdrogowa.zlecenia.data.DataField
import pl.pomocdrogowa.zlecenia.data.Order
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.CenteredLoader
import pl.pomocdrogowa.zlecenia.ui.CenteredMessage
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.ui.orders.Uploads

private val fuelLevels = listOf("Rezerwa", "1/4", "1/2", "3/4", "Pełny")

private val damageZones = listOf(
    "Przód", "Tył", "Lewy bok", "Prawy bok", "Dach", "Szyby", "Lusterka", "Felgi / opony", "Wnętrze", "Podwozie",
)

private val equipmentItems = listOf(
    "Dowód rejestracyjny", "Koło zapasowe", "Trójkąt", "Gaśnica", "Apteczka", "Podnośnik", "Fotelik dziecięcy",
    "Rzeczy osobiste",
)

private object Loading

/** Protokół odbioru pojazdu: stan, paliwo, uszkodzenia, wyposażenie i podpis klienta → PDF w zleceniu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolScreen(me: UserProfile, orderId: String, onBack: () -> Unit) {
    val orderFlow = remember(orderId) { Repository.order(orderId) }
    val order by orderFlow.collectAsState(initial = Loading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protokół odbioru") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val o = order) {
                Loading -> CenteredLoader()
                is Order -> ProtocolForm(me, o, onBack)
                else -> CenteredMessage("Zlecenie nie istnieje.")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProtocolForm(me: UserProfile, order: Order, onDone: () -> Unit) {
    val context = LocalContext.current
    var mileage by rememberSaveable { mutableStateOf("") }
    var vin by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf("") }
    val damages = remember { mutableStateMapOf<String, String>() }
    val equipment = remember { mutableStateListOf<String>() }
    var keys by rememberSaveable { mutableStateOf("1") }
    var remarks by rememberSaveable { mutableStateOf("") }
    var clientName by rememberSaveable { mutableStateOf(order.clientName) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val signature = remember { SignatureState() }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Zlecenie #${order.number} · " + listOf(order.vehicle, order.plate).filter { it.isNotBlank() }.joinToString(" "),
            style = MaterialTheme.typography.titleMedium,
        )

        Section("Pojazd")
        Field(mileage, { mileage = it }, "Przebieg (km)", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
        Field(vin, { vin = it.uppercase() }, "VIN (opcjonalnie)", Modifier.fillMaxWidth())
        Text("Poziom paliwa", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fuelLevels.forEach { level ->
                FilterChip(selected = fuel == level, onClick = { fuel = level }, label = { Text(level) })
            }
        }

        Section("Uszkodzenia")
        Text(
            "Zaznacz miejsca z uszkodzeniami i krótko je opisz. Zrób też zdjęcia w zleceniu.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            damageZones.forEach { zone ->
                FilterChip(
                    selected = zone in damages,
                    onClick = { if (zone in damages) damages.remove(zone) else damages[zone] = "" },
                    label = { Text(zone) },
                )
            }
        }
        damageZones.filter { it in damages }.forEach { zone ->
            Field(damages[zone].orEmpty(), { damages[zone] = it }, "$zone – opis uszkodzenia", Modifier.fillMaxWidth())
        }

        Section("Wyposażenie i rzeczy w aucie")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            equipmentItems.forEach { item ->
                FilterChip(
                    selected = item in equipment,
                    onClick = { if (item in equipment) equipment.remove(item) else equipment.add(item) },
                    label = { Text(item) },
                )
            }
        }
        Field(keys, { keys = it.filter(Char::isDigit) }, "Liczba kluczyków", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
        Field(remarks, { remarks = it }, "Uwagi", Modifier.fillMaxWidth(), singleLine = false, minLines = 2)

        Section("Podpis klienta")
        Field(clientName, { clientName = it }, "Imię i nazwisko klienta", Modifier.fillMaxWidth())
        if (!refused) {
            SignaturePad(signature)
            TextButton(onClick = { signature.clear() }) { Text("Wyczyść podpis") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = refused, onCheckedChange = { refused = it })
            Text("Klient odmówił podpisu / nieobecny")
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (!refused && signature.isEmpty) {
                    error = "Brak podpisu klienta. Poproś o podpis albo zaznacz, że odmówił."
                    return@Button
                }
                val protocol = Protocol(
                    mileage = mileage.trim(),
                    vin = vin.trim(),
                    fuel = fuel,
                    damages = damageZones.filter { it in damages }.map { it to damages[it].orEmpty().trim() },
                    equipment = equipmentItems.filter { it in equipment },
                    keysCount = keys,
                    remarks = remarks.trim(),
                    clientName = clientName.trim(),
                    clientRefused = refused,
                    driverName = me.name,
                    signature = if (refused) null else signature.toBitmap(600, 240),
                )
                val summary = listOfNotNull(
                    protocol.mileage.takeIf { it.isNotBlank() }?.let { DataField("Przebieg", "$it km") },
                    protocol.fuel.takeIf { it.isNotBlank() }?.let { DataField("Paliwo", it) },
                    DataField(
                        "Uszkodzenia",
                        if (protocol.damages.isEmpty()) "brak" else protocol.damages.joinToString { it.first },
                    ),
                    DataField("Podpis", if (refused) "klient odmówił" else protocol.clientName.ifBlank { "klient" }),
                )
                // Wysyłka w tle - kierowca może od razu wrócić do zlecenia.
                Uploads.run(context, order.id) {
                    val pdf = withContext(Dispatchers.IO) { writeProtocolPdf(context, order, protocol) }
                    try {
                        Repository.sendProtocol(order.id, me, pdf, summary)
                    } finally {
                        pdf.delete()
                    }
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zapisz i wyślij protokół") }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
