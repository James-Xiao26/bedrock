package app.bedrock.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.bedrock.BuildConfig
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The lockdown surface: a dim, minimal clock the user lands on whenever the
 * phone is used during the bedtime window. Must be launchable over the
 * keyguard and after process death without the Flutter engine.
 */
class NightClockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // During lockdown there is nowhere to go "back" to.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val wakeLabel = intent.getStringExtra(EXTRA_WAKE_LABEL) ?: "--:--"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NightClockScreen(
                    wakeLabel = wakeLabel,
                    onEmergencyCall = ::openDialer,
                    onDebugExit = if (BuildConfig.DEBUG) ::finish else null,
                )
            }
        }
    }

    private fun openDialer() {
        // ACTION_DIAL needs no permission; the dialer is always allowlisted.
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
    }

    companion object {
        const val EXTRA_WAKE_LABEL = "wake_label"
    }
}

@Composable
private fun NightClockScreen(
    wakeLabel: String,
    onEmergencyCall: () -> Unit,
    onDebugExit: (() -> Unit)?,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                color = Color(0xFF8A8A9E),
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = "Sleeping until $wakeLabel",
                color = Color(0xFF4A4A5A),
                fontSize = 18.sp,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onEmergencyCall) {
                Text("Emergency call", color = Color(0xFF8A8A9E))
            }
            if (onDebugExit != null) {
                OutlinedButton(onClick = onDebugExit) {
                    Text("Exit (debug)", color = Color(0xFF5A3A3A))
                }
            }
        }
    }
}
