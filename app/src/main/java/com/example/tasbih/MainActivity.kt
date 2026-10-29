package com.example.tasbih

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ---------- DataStore ----------
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tasbih")
private val KEY_COUNT = intPreferencesKey("count")
private val KEY_DHIKR = intPreferencesKey("dhikr_index")

val AZKAR = listOf(
    "سبحان الله",
    "الحمد لله",
    "الله أكبر",
    "لا إله إلا الله",
    "أستغفر الله",
    "سبحان الله وبحمده",
    "لا حول ولا قوة إلا بالله",
)

data class TasbihState(val count: Int = 0, val dhikrIndex: Int = 0)

// ---------- ViewModel ----------
class TasbihViewModel(private val store: DataStore<Preferences>) : ViewModel() {

    val state: StateFlow<TasbihState> = store.data
        .map { TasbihState(it[KEY_COUNT] ?: 0, (it[KEY_DHIKR] ?: 0).coerceIn(0, AZKAR.lastIndex)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TasbihState())

    /** Returns the new count (read from the saved state after the edit). */
    fun increment() = viewModelScope.launch {
        store.edit { it[KEY_COUNT] = (it[KEY_COUNT] ?: 0) + 1 }
    }

    fun reset() = viewModelScope.launch { store.edit { it[KEY_COUNT] = 0 } }

    fun nextDhikr() = viewModelScope.launch {
        store.edit {
            it[KEY_DHIKR] = ((it[KEY_DHIKR] ?: 0) + 1) % AZKAR.size
            it[KEY_COUNT] = 0   // new dhikr starts from zero
        }
    }

    class Factory(private val store: DataStore<Preferences>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TasbihViewModel(store) as T
    }
}

// ---------- Vibration ----------
private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun vibrateForCount(context: Context, count: Int) {
    val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
    val effect = when {
        count % 100 == 0 -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)
        count % 33 == 0 -> VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
        else -> VibrationEffect.createOneShot(20, 80) // very light tick
    }
    v.vibrate(effect)
}

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()   // required look for Android 15+
        super.onCreate(savedInstanceState)
        setContent { TasbihTheme { TasbihScreen() } }
    }
}

@Composable
fun TasbihTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF6FD3B0),
        background = androidx.compose.ui.graphics.Color(0xFF0F1513),
        surface = androidx.compose.ui.graphics.Color(0xFF18211E),
    ) else lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF0B7A5C),
        background = androidx.compose.ui.graphics.Color(0xFFF5F8F6),
        surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun TasbihScreen() {
    val context = LocalContext.current
    val vm: TasbihViewModel = viewModel(factory = TasbihViewModel.Factory(context.applicationContext.dataStore))
    val state by vm.state.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            // The whole screen is the tap target (no ripple, so it's usable without looking).
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        vm.increment()
                        vibrateForCount(context, state.count + 1)
                    }
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        AZKAR[state.dhikrIndex],
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))
                    Box(
                        Modifier
                            .size(280.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                        Text(
                            "${state.count}",
                            fontSize = 88.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { showConfirm = true }) { Text("إعادة ضبط") }
                        Button(onClick = { vm.nextDhikr() }) { Text("تغيير الذكر") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("تصفير العداد؟") },
                text = { Text("سيتم تصفير العدد الحالي (${state.count}). هل أنت متأكد؟") },
                confirmButton = {
                    TextButton(onClick = { vm.reset(); showConfirm = false }) { Text("نعم، صفّر") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) { Text("إلغاء") }
                }
            )
        }
    }
}
