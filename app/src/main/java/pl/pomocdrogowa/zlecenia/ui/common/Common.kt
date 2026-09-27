package pl.pomocdrogowa.zlecenia.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import pl.pomocdrogowa.zlecenia.data.OrderStatus

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

@Composable
fun StatusChip(status: OrderStatus) {
    Text(
        status.label,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(status.color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Zamienia błędy Firebase na zrozumiały komunikat po polsku. */
fun Throwable.toUserMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Hasło jest za słabe (minimum 6 znaków)."
    is FirebaseAuthUserCollisionException -> "Konto z tym adresem e-mail już istnieje."
    is FirebaseAuthInvalidUserException -> "Nie ma konta z tym adresem e-mail lub zostało wyłączone."
    is FirebaseAuthInvalidCredentialsException -> "Nieprawidłowy e-mail lub hasło."
    is FirebaseNetworkException -> "Brak połączenia z internetem."
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Brak uprawnień do tej operacji."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Brak połączenia z serwerem."
        else -> "Błąd bazy danych: ${message.orEmpty()}"
    }
    else -> message ?: "Nieznany błąd"
}
