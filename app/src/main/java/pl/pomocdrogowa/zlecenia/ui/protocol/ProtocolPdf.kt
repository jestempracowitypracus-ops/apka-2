package pl.pomocdrogowa.zlecenia.ui.protocol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import pl.pomocdrogowa.zlecenia.data.Order
import pl.pomocdrogowa.zlecenia.util.mediaDir
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Treść protokołu odbioru pojazdu wypełniona przez kierowcę. */
data class Protocol(
    val mileage: String,
    val vin: String,
    val fuel: String,
    val damages: List<Pair<String, String>>, // strefa -> opis
    val equipment: List<String>,
    val keysCount: String,
    val remarks: String,
    val clientName: String,
    val clientRefused: Boolean,
    val driverName: String,
    val signature: Bitmap?,
    val createdAt: Date = Date(),
)

/** Tworzy PDF A4 z protokołem. Zwraca plik w pamięci podręcznej aplikacji. */
fun writeProtocolPdf(context: Context, order: Order, p: Protocol): File {
    val doc = PdfDocument()
    val writer = PdfWriter(doc)
    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL")).format(p.createdAt)

    writer.title("Protokół odbioru pojazdu")
    writer.text("Zlecenie #${order.number} · $date", size = 11f, color = Color.DKGRAY)
    writer.space(10f)

    writer.section("Pojazd")
    writer.row("Marka i model", order.vehicle)
    writer.row("Nr rejestracyjny", order.plate)
    writer.row("VIN", p.vin)
    writer.row("Przebieg", p.mileage.takeIf { it.isNotBlank() }?.let { "$it km" }.orEmpty())
    writer.row("Poziom paliwa", p.fuel)

    writer.section("Klient i trasa")
    writer.row("Klient", p.clientName.ifBlank { order.clientName })
    writer.row("Telefon", order.clientPhone)
    writer.row("Miejsce odbioru", order.pickupAddress)
    writer.row("Miejsce dostarczenia", order.destinationAddress)

    writer.section("Stan pojazdu / uszkodzenia")
    if (p.damages.isEmpty()) {
        writer.text("Nie stwierdzono widocznych uszkodzeń.")
    } else {
        p.damages.forEach { (zone, description) -> writer.row(zone, description.ifBlank { "uszkodzenie" }) }
    }

    writer.section("Wyposażenie i rzeczy w pojeździe")
    val equipment = p.equipment + listOfNotNull(p.keysCount.takeIf { it.isNotBlank() }?.let { "Kluczyki: $it szt." })
    writer.text(if (equipment.isEmpty()) "—" else equipment.joinToString(", "))

    if (p.remarks.isNotBlank()) {
        writer.section("Uwagi")
        writer.text(p.remarks)
    }

    writer.section("Podpisy")
    writer.text(
        "Klient potwierdza zgodność stanu pojazdu z opisem powyżej oraz przekazanie pojazdu do transportu.",
        size = 10f,
        color = Color.DKGRAY,
    )
    writer.space(8f)
    writer.signatures(p)

    writer.finish()
    val file = File(mediaDir(context), "protokol_${order.number}_${System.currentTimeMillis()}.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}

/** Prosty "składacz" stron: pilnuje marginesów i przenosi treść na kolejną stronę. */
private class PdfWriter(private val doc: PdfDocument) {
    private val pageWidth = 595 // A4 w punktach
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = (pageWidth - 2 * margin).toInt()
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = 0f

    init {
        newPage()
    }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun ensure(height: Float) {
        if (y + height > pageHeight - margin) newPage()
    }

    private fun layout(text: String, paint: TextPaint, width: Int = contentWidth): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun draw(l: StaticLayout, x: Float = margin) {
        canvas.save()
        canvas.translate(x, y)
        l.draw(canvas)
        canvas.restore()
    }

    fun title(text: String) {
        val l = layout(text, paint(20f, bold = true))
        ensure(l.height.toFloat())
        draw(l)
        y += l.height + 4f
    }

    fun section(text: String) {
        val l = layout(text.uppercase(), paint(11f, bold = true, color = Color.rgb(138, 90, 0)))
        ensure(l.height + 30f)
        y += 12f
        draw(l)
        y += l.height + 3f
        canvas.drawLine(margin, y, pageWidth - margin, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        y += 6f
    }

    fun text(text: String, size: Float = 11f, color: Int = Color.BLACK) {
        val l = layout(text, paint(size, color = color))
        ensure(l.height.toFloat())
        draw(l)
        y += l.height + 2f
    }

    /** Wiersz "etykieta: wartość"; pomijany, gdy wartość jest pusta. */
    fun row(label: String, value: String) {
        if (value.isBlank()) return
        val labelWidth = 150
        val l1 = layout(label, paint(11f, color = Color.DKGRAY), labelWidth - 8)
        val l2 = layout(value, paint(11f, bold = true), contentWidth - labelWidth)
        val h = maxOf(l1.height, l2.height).toFloat()
        ensure(h)
        draw(l1)
        draw(l2, margin + labelWidth)
        y += h + 4f
    }

    fun space(h: Float) {
        y += h
    }

    fun signatures(p: Protocol) {
        val boxW = (contentWidth - 20) / 2f
        val boxH = 110f
        ensure(boxH + 40f)
        val border = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
        val left = RectF(margin, y, margin + boxW, y + boxH)
        val right = RectF(margin + boxW + 20, y, margin + 2 * boxW + 20, y + boxH)
        canvas.drawRect(left, border)
        canvas.drawRect(right, border)

        if (p.signature != null && !p.clientRefused) {
            canvas.drawBitmap(p.signature, null, RectF(left.left + 4, left.top + 4, left.right - 4, left.bottom - 4), null)
        } else if (p.clientRefused) {
            val l = layout("Klient odmówił podpisu", paint(11f, bold = true, color = Color.RED), boxW.toInt() - 16)
            canvas.save()
            canvas.translate(left.left + 8, left.top + boxH / 2 - l.height / 2)
            l.draw(canvas)
            canvas.restore()
        }
        val driver = layout(p.driverName, paint(13f, bold = true), boxW.toInt() - 16)
        canvas.save()
        canvas.translate(right.left + 8, right.top + boxH / 2 - driver.height / 2)
        driver.draw(canvas)
        canvas.restore()

        y += boxH + 4f
        val caption = paint(10f, color = Color.DKGRAY)
        canvas.drawText("Klient: ${p.clientName}", left.left, y + 10f, caption)
        canvas.drawText("Kierowca pomocy drogowej", right.left, y + 10f, caption)
        y += 20f
    }

    fun finish() {
        page?.let { doc.finishPage(it) }
        page = null
    }
}
