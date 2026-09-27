package pl.pomocdrogowa.zlecenia

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import pl.pomocdrogowa.zlecenia.ui.AppRoot
import pl.pomocdrogowa.zlecenia.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    /** Zlecenie do otwarcia po kliknięciu w powiadomienie. */
    private val pendingOrderId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppTheme {
                AppRoot(
                    pendingOrderId = pendingOrderId.value,
                    onPendingOrderHandled = { pendingOrderId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ORDER_ID)?.let { pendingOrderId.value = it }
    }

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
    }
}
