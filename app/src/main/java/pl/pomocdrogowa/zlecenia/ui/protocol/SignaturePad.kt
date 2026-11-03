package pl.pomocdrogowa.zlecenia.ui.protocol

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Podpis jako lista pociągnięć; punkty zapisane względnie (0..1), żeby dało się go przeskalować do PDF. */
class SignatureState {
    val strokes: SnapshotStateList<SnapshotStateList<Offset>> = mutableStateListOf()
    internal var size = IntSize(1, 1)

    val isEmpty: Boolean get() = strokes.none { it.size > 1 }

    fun clear() = strokes.clear()

    /** Rysuje podpis na białym tle (czarny tusz) w podanym rozmiarze. */
    fun toBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = width / 120f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        strokes.forEach { stroke ->
            val path = android.graphics.Path()
            stroke.forEachIndexed { i, p ->
                val x = p.x * width
                val y = p.y * height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

@Composable
fun SignaturePad(state: SignatureState, modifier: Modifier = Modifier) {
    val ink = Color(0xFF111111)
    Box(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        if (state.isEmpty) {
            Text(
                "Podpis klienta – podpisz palcem",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(4.dp)
                .onSizeChanged { state.size = it }
                .pointerInput(state) {
                    detectDragGestures(
                        onDragStart = { start ->
                            state.strokes.add(mutableStateListOf(start.normalized(state.size)))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            state.strokes.lastOrNull()?.add(change.position.normalized(state.size))
                        },
                    )
                },
        ) {
            state.strokes.forEach { stroke ->
                if (stroke.size < 2) return@forEach
                val path = Path().apply {
                    stroke.forEachIndexed { i, p ->
                        val x = p.x * size.width
                        val y = p.y * size.height
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(path, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun Offset.normalized(size: IntSize) = Offset(
    (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
    (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
)
