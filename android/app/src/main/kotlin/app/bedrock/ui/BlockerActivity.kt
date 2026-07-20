package app.bedrock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.GrantKind
import app.bedrock.engine.HardcorePassword

/**
 * The per-app blocker. Shown whenever a blocked app is opened during a
 * downtime window (iOS-Downtime style). Entering the passcode reveals a
 * duration menu that grants THAT app more time; leaving goes home.
 * Unlike the old night clock this is not a full takeover - the home screen
 * and allowed apps stay usable, so "leave" is always available.
 */
class BlockerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = BedrockEngine.get(applicationContext)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { goHome(); finish(); return }
        val label = appLabel(pkg)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BlockerScreen(
                    appLabel = label,
                    checkCode = engine::checkPasscode,
                    onGrant = { kind ->
                        engine.grantApp(pkg, kind)
                        finish() // blocked app resumes, now granted
                    },
                    onReset = { onRevealed ->
                        engine.onCodeReset = { code -> runOnUiThread { onRevealed(code) } }
                        engine.billing.launchBypassPurchase(this) { /* error -> shown inline */ }
                    },
                    onLeave = { goHome(); finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        showing = this
    }

    override fun onStop() {
        if (showing === this) showing = null
        BedrockEngine.get(applicationContext).onCodeReset = null
        super.onStop()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        "This app"
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"

        private var showing: BlockerActivity? = null

        /** Called by the engine when the window ends so the blocker gets out of the way. */
        fun closeIfShowing() {
            showing?.finish()
            showing = null
        }
    }
}

@Composable
private fun BlockerScreen(
    appLabel: String,
    checkCode: (String) -> Boolean,
    onGrant: (GrantKind) -> Unit,
    onReset: (onRevealed: (String) -> Unit) -> Unit,
    onLeave: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("$appLabel is blocked", color = Color(0xFFAAAAC0), fontSize = 24.sp)
        Text(
            "Downtime is on. Enter your passcode to give yourself more time on this app.",
            color = Color(0xFF8A8A9E),
            textAlign = TextAlign.Center,
        )

        if (!unlocked) {
            OutlinedTextField(
                value = typed,
                onValueChange = { next ->
                    if (next.length <= HardcorePassword.LENGTH && next.all(Char::isDigit)) {
                        typed = next
                        error = null
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    if (checkCode(typed)) unlocked = true else error = "That code isn't right."
                },
                enabled = typed.length == HardcorePassword.LENGTH,
            ) {
                Text("Continue")
            }
            revealed?.let {
                Text(
                    "Your new code is $it - write it down.",
                    color = Color(0xFF8B96E6),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Forgot your code? Reset it - this charges your Google Play account " +
                    "\$1 and needs an internet connection.",
                color = Color(0xFF6A6A7E),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = { onReset { code -> revealed = code; typed = code } }) {
                Text("Reset code (\$1)")
            }
            error?.let { Text(it, color = Color(0xFFB05A5A), textAlign = TextAlign.Center) }
        } else {
            Text("How much longer?", color = Color(0xFF8A8A9E))
            Button(onClick = { onGrant(GrantKind.FIVE_MIN) }, modifier = Modifier.fillMaxWidth()) {
                Text("5 minutes")
            }
            Button(onClick = { onGrant(GrantKind.FIFTEEN_MIN) }, modifier = Modifier.fillMaxWidth()) {
                Text("15 minutes")
            }
            Button(onClick = { onGrant(GrantKind.REST_OF_WINDOW) }, modifier = Modifier.fillMaxWidth()) {
                Text("Rest of the window")
            }
        }

        OutlinedButton(onClick = onLeave) { Text("Leave, go home") }
    }
}
