package pl.pomocdrogowa.zlecenia.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.pomocdrogowa.zlecenia.data.Order
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.CenteredLoader
import pl.pomocdrogowa.zlecenia.ui.CenteredMessage
import pl.pomocdrogowa.zlecenia.ui.common.StatusChip
import pl.pomocdrogowa.zlecenia.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    me: UserProfile,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onUsers: () -> Unit,
    onProfile: () -> Unit,
) {
    val ordersFlow = remember(me.uid, me.role) { Repository.ordersFor(me) }
    val orders by ordersFlow.collectAsState(initial = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (me.isDispatcher) "Zlecenia" else "Moje zlecenia")
                        Text(
                            "${me.name} · ${me.role.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (me.isDispatcher) {
                        IconButton(onClick = onUsers) { Icon(Icons.Filled.Group, "Kierowcy") }
                    }
                    IconButton(onClick = onProfile) { Icon(Icons.Filled.AccountCircle, "Mój profil") }
                },
            )
        },
        floatingActionButton = {
            if (me.isDispatcher) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Nowe zlecenie") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val all = orders
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("W toku") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Zakończone") })
            }
            when {
                all == null -> CenteredLoader()
                else -> {
                    val visible = all.filter { it.status.isFinished == (tab == 1) }
                    if (visible.isEmpty()) {
                        CenteredMessage(if (tab == 0) "Brak aktywnych zleceń" else "Brak zakończonych zleceń")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.id }) { order ->
                                OrderCard(order, showDriver = me.isDispatcher) { onOpen(order.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, showDriver: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${order.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(order.status)
            }
            Text(
                listOf(order.vehicle, order.plate).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "Pojazd nieokreślony" },
                style = MaterialTheme.typography.titleSmall,
            )
            if (order.pickupAddress.isNotBlank()) {
                Text("📍 ${order.pickupAddress}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (order.destinationAddress.isNotBlank()) {
                Text("🏁 ${order.destinationAddress}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showDriver) {
                Text(
                    "Kierowca: " + order.driverName.ifBlank { "nieprzydzielony" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (order.lastMessage.isNotBlank()) {
                Text(
                    "${formatDate(order.lastMessageAt)} · ${order.lastMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
