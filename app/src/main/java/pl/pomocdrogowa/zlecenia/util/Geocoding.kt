package pl.pomocdrogowa.zlecenia.util

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Zamienia adres na współrzędne (wbudowany geokoder Androida). Zwraca null, gdy się nie uda. */
suspend fun geocode(context: Context, address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    if (address.isBlank() || !Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("pl", "PL")).getFromLocationName(address, 1)
            ?.firstOrNull()
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
