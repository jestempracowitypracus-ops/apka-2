package pl.pomocdrogowa.zlecenia.ui.orders

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.ui.common.toUserMessage

/**
 * Wysyłanie zdjęć i głosówek działa w tle aplikacji, więc nie przerywa się,
 * gdy kierowca wyjdzie z ekranu zlecenia.
 */
object Uploads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Liczba trwających wysyłek dla danego zlecenia. */
    val pending = mutableStateMapOf<String, Int>()

    fun run(context: Context, orderId: String, block: suspend () -> Unit) {
        val appContext = context.applicationContext
        pending[orderId] = (pending[orderId] ?: 0) + 1
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(appContext, "Nie wysłano: " + e.toUserMessage(), Toast.LENGTH_LONG).show()
            } finally {
                val left = (pending[orderId] ?: 1) - 1
                if (left <= 0) pending.remove(orderId) else pending[orderId] = left
            }
        }
    }
}
