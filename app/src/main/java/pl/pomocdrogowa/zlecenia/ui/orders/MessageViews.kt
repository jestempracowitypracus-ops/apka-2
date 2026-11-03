package pl.pomocdrogowa.zlecenia.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import pl.pomocdrogowa.zlecenia.util.openUrl
import kotlinx.coroutines.delay
import pl.pomocdrogowa.zlecenia.data.Message
import pl.pomocdrogowa.zlecenia.data.MessageType
import pl.pomocdrogowa.zlecenia.util.AudioPlayer
import pl.pomocdrogowa.zlecenia.util.formatDate
import pl.pomocdrogowa.zlecenia.util.formatDuration

@Composable
fun MessageItem(message: Message, mine: Boolean, player: AudioPlayer, onPhoto: (String) -> Unit) {
    if (message.type == MessageType.SYSTEM) {
        Text(
            "${formatDate(message.createdAt)} · ${message.senderName}: ${message.text}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!mine) {
                    Text(
                        "${message.senderName} · ${message.senderRole.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                when (message.type) {
                    MessageType.TEXT -> Text(message.text)
                    MessageType.PHOTO -> message.mediaUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Zdjęcie",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(260.dp, 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.1f))
                                .clickable { onPhoto(url) },
                        )
                    }
                    MessageType.AUDIO -> message.mediaUrl?.let { VoiceMessage(it, message.durationMs, player) }
                    MessageType.DATA -> DataFields(message)
                    MessageType.PROTOCOL -> ProtocolMessage(message)
                    MessageType.SYSTEM -> Unit
                }
                Text(
                    formatDate(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun ProtocolMessage(message: Message) {
    val context = LocalContext.current
    Text("📄 Protokół odbioru pojazdu", style = MaterialTheme.typography.titleSmall)
    message.fields.forEach { Text("${it.label}: ${it.value}", style = MaterialTheme.typography.bodyMedium) }
    message.mediaUrl?.let { url ->
        OutlinedButton(onClick = { openUrl(context, url) }) { Text("Otwórz PDF") }
    }
}

@Composable
private fun DataFields(message: Message) {
    Text("Dane dodatkowe", style = MaterialTheme.typography.titleSmall)
    message.fields.forEach { field ->
        Column {
            Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(field.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VoiceMessage(url: String, durationMs: Long, player: AudioPlayer) {
    val playing = player.playingUrl == url
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        progress = 0f
        while (playing) {
            if (durationMs > 0) progress = (player.positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
            delay(200)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledIconButton(onClick = { player.toggle(url) }) {
            if (playing && player.loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) "Zatrzymaj" else "Odtwórz")
            }
        }
        Column(Modifier.widthIn(min = 120.dp, max = 180.dp)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
            Text("🎤 ${formatDuration(durationMs)}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Podgląd zdjęcia na cały ekran z powiększaniem dwoma palcami. */
@Composable
fun PhotoViewer(url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Zdjęcie",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
    }
}
