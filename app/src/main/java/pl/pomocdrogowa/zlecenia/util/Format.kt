package pl.pomocdrogowa.zlecenia.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale("pl", "PL"))
private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL"))

fun formatDate(date: Date?): String {
    if (date == null) return "teraz"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) "dziś " + timeFormat.format(date) else dateTimeFormat.format(date)
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun dial(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))
}

/** Otwiera nawigację Google Maps (lub inną aplikację map) do podanego adresu. */
fun navigateTo(context: Context, address: String) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(address))
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
