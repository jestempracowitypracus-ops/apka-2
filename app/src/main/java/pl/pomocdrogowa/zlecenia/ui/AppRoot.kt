package pl.pomocdrogowa.zlecenia.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.BuildConfig
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.auth.LoginScreen
import pl.pomocdrogowa.zlecenia.ui.drivers.ProfileScreen
import pl.pomocdrogowa.zlecenia.ui.drivers.UsersScreen
import pl.pomocdrogowa.zlecenia.ui.orders.OrderDetailScreen
import pl.pomocdrogowa.zlecenia.ui.orders.OrderEditScreen
import pl.pomocdrogowa.zlecenia.ui.orders.OrdersScreen

private object Loading

@Composable
fun AppRoot(pendingOrderId: String?, onPendingOrderHandled: () -> Unit) {
    if (!BuildConfig.FIREBASE_CONFIGURED) {
        CenteredMessage(
            "Aplikacja nie jest jeszcze połączona z Firebase.\n\n" +
                "Dodaj plik google-services.json (instrukcja w README.md) i zbuduj aplikację ponownie.",
        )
        return
    }

    val uid by remember { Repository.authState() }.collectAsState(initial = Loading)
    when (val current = uid) {
        Loading -> CenteredLoader()
        null -> LoginScreen()
        is String -> {
            val profileFlow = remember(current) { Repository.profile(current) }
            val profile by profileFlow.collectAsState(initial = Loading)
            when (val p = profile) {
                Loading -> CenteredLoader()
                is UserProfile -> if (p.active) {
                    LoggedIn(p, pendingOrderId, onPendingOrderHandled)
                } else {
                    BlockedAccount("Twoje konto zostało wyłączone przez dyspozytora.")
                }
                else -> BlockedAccount("Brak profilu dla tego konta. Poproś dyspozytora o założenie konta w aplikacji.")
            }
        }
    }
}

@Composable
private fun LoggedIn(me: UserProfile, pendingOrderId: String?, onPendingOrderHandled: () -> Unit) {
    val nav = rememberNavController()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(me.uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { Repository.registerPushToken() }
    }
    LaunchedEffect(pendingOrderId) {
        if (pendingOrderId != null) {
            nav.navigate("order/$pendingOrderId") { launchSingleTop = true }
            onPendingOrderHandled()
        }
    }

    NavHost(navController = nav, startDestination = "orders") {
        composable("orders") {
            OrdersScreen(
                me = me,
                onOpen = { nav.navigate("order/$it") },
                onNew = { nav.navigate("edit/new") },
                onUsers = { nav.navigate("users") },
                onProfile = { nav.navigate("profile") },
            )
        }
        composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            val id = it.arguments!!.getString("id")!!
            OrderDetailScreen(
                me = me,
                orderId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            val id = it.arguments!!.getString("id")!!.takeIf { v -> v != "new" }
            OrderEditScreen(
                me = me,
                orderId = id,
                onBack = { nav.popBackStack() },
                onSaved = { savedId ->
                    nav.popBackStack()
                    if (id == null) nav.navigate("order/$savedId")
                },
            )
        }
        composable("users") { UsersScreen(onBack = { nav.popBackStack() }) }
        composable("profile") { ProfileScreen(me = me, onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun BlockedAccount(text: String) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { scope.launch { Repository.signOut() } }) { Text("Wyloguj") }
    }
}

@Composable
fun CenteredLoader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
