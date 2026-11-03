package pl.pomocdrogowa.zlecenia.ui.map

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pl.pomocdrogowa.zlecenia.R
import pl.pomocdrogowa.zlecenia.data.Availability
import pl.pomocdrogowa.zlecenia.data.DriverLocation
import pl.pomocdrogowa.zlecenia.data.Order
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.Role
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.util.formatDate

private val StaleColor = Color(0xFF9E9E9E)
private val OrderColor = Color(0xFF1E88E5)

/** Mapa dla dyspozytora: kierowcy w pracy oraz miejsca odbioru aktywnych zleceń. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversMapScreen(onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    val locationsFlow = remember { Repository.driverLocations() }
    val usersFlow = remember { Repository.allUsers() }
    val locations by locationsFlow.collectAsState(initial = emptyList())
    val users by usersFlow.collectAsState(initial = emptyList())
    val dispatcherStub = remember { UserProfile("", "", "", "", "", Role.DISPATCHER, true) }
    val ordersFlow = remember { Repository.ordersFor(dispatcherStub) }
    val orders by ordersFlow.collectAsState(initial = emptyList())

    val usersById = users.associateBy { it.uid }
    val activeOrders = orders.filter { !it.status.isFinished && it.pickupLat != null && it.pickupLng != null }
    var focus by remember { mutableStateOf<GeoPoint?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa kierowców") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OsmMap(
                drivers = locations,
                usersById = usersById,
                orders = activeOrders,
                focus = focus,
                onFocusConsumed = { focus = null },
                onOrderClick = onOpenOrder,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Surface(tonalElevation = 2.dp) {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    if (locations.isEmpty()) {
                        item {
                            Text(
                                "Żaden kierowca nie udostępnia teraz lokalizacji.\n" +
                                    "Kierowca pojawi się na mapie po ustawieniu „Wolny” lub „Zajęty”.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    items(locations.sortedBy { it.name.lowercase() }, key = { it.uid }) { loc ->
                        val availability = usersById[loc.uid]?.availability ?: Availability.OFF
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { focus = GeoPoint(loc.lat, loc.lng) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(12.dp)
                                    .background(if (loc.isStale) StaleColor else availability.color, CircleShape),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(loc.name.ifBlank { "Kierowca" }, fontWeight = FontWeight.SemiBold)
                                Text(
                                    availability.label + " · " + formatDate(loc.updatedAt) +
                                        if (loc.isStale) " · brak świeżej pozycji" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmMap(
    drivers: List<DriverLocation>,
    usersById: Map<String, UserProfile>,
    orders: List<Order>,
    focus: GeoPoint?,
    onFocusConsumed: () -> Unit,
    onOrderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.5)
            controller.setCenter(GeoPoint(52.07, 19.48)) // środek Polski
        }
    }
    var fittedOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    fun pin(resId: Int, color: Color): Drawable? =
        ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(color.toArgb()) }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            orders.forEach { order ->
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(order.pickupLat!!, order.pickupLng!!)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = pin(R.drawable.ic_map_order, OrderColor)
                        title = "Zlecenie #${order.number} · ${order.status.label}"
                        snippet = listOf(order.pickupAddress, order.driverName.ifBlank { "bez kierowcy" })
                            .filter { it.isNotBlank() }.joinToString("<br>")
                        setOnMarkerClickListener { marker, _ ->
                            if (marker.isInfoWindowShown) onOrderClick(order.id) else marker.showInfoWindow()
                            true
                        }
                    },
                )
            }
            drivers.forEach { loc ->
                val user = usersById[loc.uid]
                val availability = user?.availability ?: Availability.OFF
                view.overlays.add(
                    Marker(view).apply {
                        position = GeoPoint(loc.lat, loc.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = pin(R.drawable.ic_map_driver, if (loc.isStale) StaleColor else availability.color)
                        title = loc.name.ifBlank { "Kierowca" } + " · " + availability.label
                        snippet = listOfNotNull(
                            user?.vehicle?.takeIf { it.isNotBlank() },
                            user?.phone?.takeIf { it.isNotBlank() },
                            "pozycja: " + formatDate(loc.updatedAt),
                        ).joinToString("<br>")
                    },
                )
            }

            if (focus != null) {
                view.controller.animateTo(focus, 14.0, 800L)
                onFocusConsumed()
            } else if (!fittedOnce) {
                val points = drivers.map { GeoPoint(it.lat, it.lng) } +
                    orders.map { GeoPoint(it.pickupLat!!, it.pickupLng!!) }
                if (points.size == 1) {
                    view.controller.setZoom(13.0)
                    view.controller.setCenter(points.first())
                    fittedOnce = true
                } else if (points.size > 1) {
                    view.post {
                        view.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.3f), false, 64)
                    }
                    fittedOnce = true
                }
            }
            view.invalidate()
        },
    )
}
