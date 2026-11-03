package pl.pomocdrogowa.zlecenia.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.data.Order
import pl.pomocdrogowa.zlecenia.data.OrderStatus
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.CenteredLoader
import pl.pomocdrogowa.zlecenia.ui.CenteredMessage
import pl.pomocdrogowa.zlecenia.ui.common.StatusChip
import pl.pomocdrogowa.zlecenia.util.AudioPlayer
import pl.pomocdrogowa.zlecenia.util.dial
import pl.pomocdrogowa.zlecenia.util.formatDate
import pl.pomocdrogowa.zlecenia.util.navigateTo
import pl.pomocdrogowa.zlecenia.util.openUrl

private object Loading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    me: UserProfile,
    orderId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onProtocol: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val orderFlow = remember(orderId) { Repository.order(orderId) }
    val messagesFlow = remember(orderId) { Repository.messages(orderId) }
    val order by orderFlow.collectAsState(initial = Loading)
    val messages by messagesFlow.collectAsState(initial = emptyList())
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    var photo by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmFinishWithoutProtocol by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Przewijanie na dół przy nowej wiadomości.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun setStatus(status: OrderStatus) {
        scope.launch {
            runCatching { Repository.changeStatus(orderId, status, me) }
                .onFailure { error = it.message }
        }
    }

    val current = order as? Order
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.let { "Zlecenie #${it.number}" } ?: "Zlecenie") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                actions = {
                    if (me.isDispatcher && current != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edytuj") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Więcej") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                OrderStatus.entries.filter { it != current.status }.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text("Ustaw: ${status.label}") },
                                        onClick = {
                                            menuOpen = false
                                            setStatus(status)
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Usuń zlecenie") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) Box(Modifier.imePadding()) { Composer(orderId, me) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                order === Loading -> CenteredLoader()
                current == null -> CenteredMessage("Zlecenie nie istnieje lub nie masz do niego dostępu.")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        OrderHeader(
                            order = current,
                            me = me,
                            error = error,
                            onCall = { dial(context, current.clientPhone) },
                            onNavigate = { navigateTo(context, it) },
                            onNextStatus = {
                                if (it == OrderStatus.DONE && current.protocolUrl == null) {
                                    confirmFinishWithoutProtocol = true
                                } else {
                                    setStatus(it)
                                }
                            },
                            onProtocol = onProtocol,
                            onOpenProtocol = { url -> openUrl(context, url) },
                        )
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageItem(message, mine = message.senderId == me.uid, player = player, onPhoto = { photo = it })
                    }
                }
            }
        }
    }

    photo?.let { PhotoViewer(it) { photo = null } }

    if (confirmFinishWithoutProtocol) {
        AlertDialog(
            onDismissRequest = { confirmFinishWithoutProtocol = false },
            title = { Text("Brak protokołu odbioru") },
            text = { Text("Do tego zlecenia nie wypełniono protokołu z podpisem klienta. Wypełnić go teraz?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinishWithoutProtocol = false
                    onProtocol()
                }) { Text("Wypełnij protokół") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmFinishWithoutProtocol = false
                    setStatus(OrderStatus.DONE)
                }) { Text("Zakończ bez protokołu") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć zlecenie?") },
            text = { Text("Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { Repository.deleteOrder(orderId) }
                            .onSuccess { onBack() }
                            .onFailure { error = it.message }
                    }
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun OrderHeader(
    order: Order,
    me: UserProfile,
    error: String?,
    onCall: () -> Unit,
    onNavigate: (String) -> Unit,
    onNextStatus: (OrderStatus) -> Unit,
    onProtocol: () -> Unit,
    onOpenProtocol: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDate(order.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(order.status)
            }
            InfoRow("Kierowca", order.driverName.ifBlank { "nieprzydzielony" })
            InfoRow("Klient", order.clientName)
            InfoRow("Telefon", order.clientPhone)
            InfoRow("Pojazd", listOf(order.vehicle, order.plate).filter { it.isNotBlank() }.joinToString(" · "))
            InfoRow("Skąd", order.pickupAddress)
            InfoRow("Dokąd", order.destinationAddress)
            InfoRow("Opis", order.description)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.clientPhone.isNotBlank()) {
                    OutlinedButton(onClick = onCall) {
                        Icon(Icons.Filled.Call, null)
                        Text(" Zadzwoń")
                    }
                }
                val target = if (order.status.ordinal >= OrderStatus.TOWING.ordinal && order.destinationAddress.isNotBlank()) {
                    order.destinationAddress
                } else {
                    order.pickupAddress.ifBlank { order.destinationAddress }
                }
                if (target.isNotBlank()) {
                    OutlinedButton(onClick = { onNavigate(target) }) {
                        Icon(Icons.Filled.Navigation, null)
                        Text(" Nawiguj")
                    }
                }
            }

            // Protokół odbioru: kierowca wypełnia go przy kliencie, obie strony mogą otworzyć PDF.
            val protocolUrl = order.protocolUrl
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (protocolUrl != null) {
                    OutlinedButton(onClick = { onOpenProtocol(protocolUrl) }) {
                        Icon(Icons.Filled.Description, null)
                        Text(" Protokół PDF")
                    }
                }
                if (!me.isDispatcher && !order.status.isFinished) {
                    OutlinedButton(onClick = onProtocol) {
                        Icon(Icons.Filled.Draw, null)
                        Text(if (protocolUrl == null) " Protokół odbioru" else " Nowy protokół")
                    }
                }
            }

            // Kierowca przesuwa zlecenie do kolejnego etapu jednym przyciskiem.
            val next = order.status.next
            if (!me.isDispatcher && next != null) {
                Button(
                    onClick = { onNextStatus(next) },
                    colors = ButtonDefaults.buttonColors(containerColor = next.color),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (order.status == OrderStatus.NEW) "Przyjmij zlecenie" else "Status: ${next.label}") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
