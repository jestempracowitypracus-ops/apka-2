package pl.pomocdrogowa.zlecenia.ui.orders

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.pomocdrogowa.zlecenia.data.DataField
import pl.pomocdrogowa.zlecenia.data.Repository
import pl.pomocdrogowa.zlecenia.data.UserProfile
import pl.pomocdrogowa.zlecenia.ui.common.Field
import pl.pomocdrogowa.zlecenia.util.AudioRecorder
import pl.pomocdrogowa.zlecenia.util.compressImage
import pl.pomocdrogowa.zlecenia.util.formatDuration
import pl.pomocdrogowa.zlecenia.util.newCameraFile

/** Pasek na dole zlecenia: komentarz, zdjęcie z aparatu/galerii, głosówka, dane dodatkowe. */
@Composable
fun Composer(orderId: String, me: UserProfile) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var dataDialog by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val uploading = (Uploads.pending[orderId] ?: 0) > 0

    DisposableEffect(Unit) { onDispose { recorder.stopAndDiscard() } }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedMs()
            delay(250)
        }
    }

    fun sendPhotos(uris: List<Uri>) {
        uris.forEach { uri ->
            Uploads.run(context, orderId) {
                val file = withContext(Dispatchers.IO) { compressImage(context, uri) }
                try {
                    Repository.sendPhoto(orderId, me, file)
                } finally {
                    file.delete()
                }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) sendPhotos(listOf(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) sendPhotos(uris)
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { recording = true }
            .onFailure { Toast.makeText(context, "Nie można uruchomić mikrofonu", Toast.LENGTH_SHORT).show() }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Aplikacja potrzebuje dostępu do mikrofonu", Toast.LENGTH_LONG).show()
    }

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            if (uploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Wysyłanie…",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            if (recording) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(onClick = {
                        recorder.stopAndDiscard()
                        recording = false
                    }) { Icon(Icons.Filled.Close, "Anuluj nagranie") }
                    Box(Modifier.size(12.dp).background(Color.Red, CircleShape))
                    Text("Nagrywanie ${formatDuration(elapsed)}", modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = {
                        recording = false
                        val result = recorder.stop()
                        if (result == null) {
                            Toast.makeText(context, "Nagranie za krótkie", Toast.LENGTH_SHORT).show()
                        } else {
                            val (file, duration) = result
                            Uploads.run(context, orderId) {
                                try {
                                    Repository.sendAudio(orderId, me, file, duration)
                                } finally {
                                    file.delete()
                                }
                            }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Wyślij nagranie") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.AddCircle, "Dodaj", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Zrób zdjęcie") },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                                onClick = {
                                    menuOpen = false
                                    val (_, uri) = newCameraFile(context)
                                    cameraUri = uri
                                    camera.launch(uri)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Zdjęcia z galerii") },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                                onClick = {
                                    menuOpen = false
                                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Dane dodatkowe") },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = {
                                    menuOpen = false
                                    dataDialog = true
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Komentarz…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    if (text.isNotBlank()) {
                        IconButton(onClick = {
                            val toSend = text
                            text = ""
                            Uploads.run(context, orderId) { Repository.sendText(orderId, me, toSend) }
                        }) { Icon(Icons.AutoMirrored.Filled.Send, "Wyślij", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        IconButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }) { Icon(Icons.Filled.Mic, "Nagraj głosówkę", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    if (dataDialog) {
        ExtraDataDialog(
            onDismiss = { dataDialog = false },
            onSend = { fields ->
                dataDialog = false
                Uploads.run(context, orderId) { Repository.sendData(orderId, me, fields) }
            },
        )
    }
}

private val extraDataLabels = listOf(
    "VIN",
    "Przebieg (km)",
    "Poziom paliwa",
    "Stan pojazdu / uszkodzenia",
    "Nr szkody / polisy",
    "Pobrana kwota (zł)",
    "Forma płatności",
    "Czas zakończenia",
    "Inne informacje",
)

@Composable
private fun ExtraDataDialog(onDismiss: () -> Unit, onSend: (List<DataField>) -> Unit) {
    val values = remember { mutableStateListOf(*Array(extraDataLabels.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dane dodatkowe") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wypełnij tylko potrzebne pola.", style = MaterialTheme.typography.bodySmall)
                extraDataLabels.forEachIndexed { i, label ->
                    Field(
                        values[i], { values[i] = it }, label, Modifier.fillMaxWidth(),
                        keyboard = if (label.contains("km") || label.contains("zł")) KeyboardType.Number else KeyboardType.Text,
                        singleLine = !label.startsWith("Stan") && !label.startsWith("Inne"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSend(
                        extraDataLabels.indices
                            .filter { values[it].isNotBlank() }
                            .map { DataField(extraDataLabels[it], values[it].trim()) },
                    )
                },
                enabled = values.any { it.isNotBlank() },
            ) { Text("Wyślij") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
