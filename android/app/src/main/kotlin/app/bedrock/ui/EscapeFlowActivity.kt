package app.bedrock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bedrock.BuildConfig
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.Mode
import app.bedrock.engine.SessionEvent
import kotlinx.coroutines.delay

/**
 * The way out of a locked night, deliberately unpleasant:
 * - NORMAL mode: sit through a countdown, then type the phrase, exactly.
 * - HARDCORE mode: the only exit is the $1 emergency bypass via Play.
 * Leaving the screen resets the countdown - that is the point.
 */
class EscapeFlowActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)

        val engine = BedrockEngine.get(applicationContext)
        val hardcore = engine.store.activeConfig().mode == Mode.HARDCORE

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                if (hardcore) {
                    HardcoreBypassScreen(
                        onBuy = { onError ->
                            engine.billing.launchBypassPurchase(this, onError)
                        },
                        onCancel = ::finish,
                    )
                } else {
                    NormalEscapeScreen(
                        countdownSeconds = if (BuildConfig.DEBUG) 10 else 5 * 60,
                        onEscaped = {
                            engine.dispatch(SessionEvent.EscapeCompleted)
                            finish()
                        },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        showing = this
    }

    override fun onStop() {
        if (showing === this) showing = null
        super.onStop()
    }

    companion object {
        const val ESCAPE_PHRASE = "I am choosing my phone over my sleep"

        private var showing: EscapeFlowActivity? = null

        /**
         * Called by the engine when blocking ends. The hardcore purchase
         * completes asynchronously through billing, so this screen cannot
         * finish itself the way the normal escape flow does.
         */
        fun closeIfShowing() {
            showing?.finish()
            showing = null
        }
    }
}

@Composable
private fun NormalEscapeScreen(
    countdownSeconds: Int,
    onEscaped: () -> Unit,
    onCancel: () -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(countdownSeconds) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    EscapeScaffold(title = "Break tonight's lockdown?") {
        if (secondsLeft > 0) {
            Text(
                text = "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                color = Color(0xFF8A8A9E),
                fontSize = 64.sp,
            )
            Text(
                "Sit with it for a few minutes. If you still need your phone " +
                    "when the timer ends, you can unlock the rest of the night.",
                color = Color(0xFF6A6A7E),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Type exactly:\n\"${EscapeFlowActivity.ESCAPE_PHRASE}\"",
                color = Color(0xFF8A8A9E),
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onEscaped,
                enabled = typed.trim() == EscapeFlowActivity.ESCAPE_PHRASE,
            ) {
                Text("Unlock the rest of tonight")
            }
        }
        OutlinedButton(onClick = onCancel) { Text("Never mind, back to sleep") }
    }
}

@Composable
private fun HardcoreBypassScreen(
    onBuy: (onError: (String) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }

    EscapeScaffold(title = "Hardcore mode") {
        Text(
            "You asked for this. The only way out tonight is the emergency " +
                "bypass. It needs an internet connection and charges your " +
                "Google Play account \$1.",
            color = Color(0xFF8A8A9E),
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onBuy { error = it } }) {
            Text("Buy emergency bypass (\$1)")
        }
        error?.let { Text(it, color = Color(0xFFB05A5A), textAlign = TextAlign.Center) }
        OutlinedButton(onClick = onCancel) { Text("Never mind, back to sleep") }
    }
}

@Composable
private fun EscapeScaffold(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(title, color = Color(0xFFAAAAC0), fontSize = 24.sp)
        content()
    }
}
