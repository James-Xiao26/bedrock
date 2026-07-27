package app.bedrock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bedrock.engine.Acknowledgement
import app.bedrock.engine.BedrockEngine
import app.bedrock.engine.GrantKind
import app.bedrock.engine.HardcorePassword
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        val holdMs = engine.store.bypassHoldSeconds() * 1000L
        // Non-empty only when feed blocking sent us here, naming the surface
        // ("YouTube Shorts") rather than the app, which is still usable.
        val surface = intent.getStringExtra(EXTRA_SURFACE).orEmpty()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BlockerScreen(
                    appLabel = label,
                    surfaceLabel = surface,
                    holdToFreeMs = holdMs,
                    checkCode = engine::checkPasscode,
                    onGrant = { kind ->
                        engine.grantApp(pkg, kind)
                        finish() // blocked app resumes, now granted
                    },
                    onReset = { onRevealed ->
                        engine.onCodeReset = { code -> runOnUiThread { onRevealed(code) } }
                        engine.billing.launchBypassPurchase(this) { /* error -> shown inline */ }
                    },
                    onFreeReset = { engine.resetCodeFree() },
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

        /** What was blocked ("YouTube Shorts"); absent for whole-app blocking. */
        const val EXTRA_SURFACE = "blocked_surface"

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
    surfaceLabel: String,
    holdToFreeMs: Long,
    checkCode: (String) -> Boolean,
    onGrant: (GrantKind) -> Unit,
    onReset: (onRevealed: (String) -> Unit) -> Unit,
    onFreeReset: () -> String,
    onLeave: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf<String?>(null) }
    var freeMode by remember { mutableStateOf(false) }
    var acknowledged by remember { mutableStateOf("") }
    // One tip per appearance of the blocker (new each time it's shown).
    val tip = remember { PhoneTips.random() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (surfaceLabel.isNotEmpty()) "$surfaceLabel is blocked" else "$appLabel is blocked",
            color = Color(0xFFAAAAC0),
            fontSize = 24.sp,
        )
        Text(
            // Deliberately not "messaging still works": true of Instagram, wrong
            // for YouTube, where what survives is search and long-form video.
            if (surfaceLabel.isNotEmpty()) {
                "The rest of the app still works. Only enter your passcode if you really need this."
            } else {
                "Downtime is on. Only enter your passcode if you really need this app right now."
            },
            color = Color(0xFF8A8A9E),
            textAlign = TextAlign.Center,
        )

        if (!unlocked && freeMode) {
            Text(
                "Forgot your code? Reset it for free by copying the note below word for word.",
                color = Color(0xFF8A8A9E),
                textAlign = TextAlign.Center,
            )
            Text(
                Acknowledgement.TEXT,
                color = Color(0xFF6A6A7E),
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = acknowledged,
                onValueChange = { acknowledged = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onFreeReset().let { code -> revealed = code; typed = code; freeMode = false } },
                enabled = Acknowledgement.accepts(acknowledged),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset my code")
            }
            OutlinedButton(onClick = { freeMode = false; acknowledged = "" }) {
                Text("Back")
            }
        } else if (!unlocked) {
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
                "Forgot your code? Reset it for \$1 - it needs no one but you.",
                color = Color(0xFF6A6A7E),
                textAlign = TextAlign.Center,
            )
            HoldableBypassButton(
                holdMs = holdToFreeMs,
                onTap = { onReset { code -> revealed = code; typed = code } },
                onHold = { freeMode = true; revealed = null },
            )
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

        if (!unlocked && !freeMode) {
            Text(
                tip,
                color = Color(0xFF7E8AA0),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
            )
        }
        OutlinedButton(onClick = onLeave) { Text("Leave, go home") }
    }
}

/**
 * The $1 bypass button, styled like an outlined button. A normal tap launches
 * the paid bypass; a deliberate [holdMs]-long hold reveals the free (copy-a-note)
 * reset. The free path is intentionally hidden behind the hold so it isn't an
 * obvious one-tap escape, only a fallback for someone who knows to look. The
 * hold length is user-configurable in Settings (see ConfigStore.bypassHoldSeconds).
 */
@Composable
private fun HoldableBypassButton(holdMs: Long, onTap: () -> Unit, onHold: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Traces the button's border from 0 to 1 over the hold; a very subtle,
    // unlabelled progress ring so someone who knows the gesture gets feedback
    // without advertising the free path to a casual tapper.
    val progress = remember { Animatable(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .drawBehind {
                val strokePx = 1.5.dp.toPx()
                val inset = strokePx / 2
                val left = inset
                val top = inset
                val right = size.width - inset
                val bottom = size.height - inset
                val cx = size.width / 2
                val r = minOf(20.dp.toPx(), (right - left) / 2, (bottom - top) / 2)
                // Built by hand (not addRoundRect) so the path starts at the top
                // centre and runs clockwise - that's where the trace begins.
                val outline = Path().apply {
                    moveTo(cx, top)
                    lineTo(right - r, top)
                    arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)
                    lineTo(right, bottom - r)
                    arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)
                    lineTo(left + r, bottom)
                    arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)
                    lineTo(left, top + r)
                    arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
                    lineTo(cx, top)
                }
                // Faint base border, always present.
                drawPath(outline, Color(0xFF33333F), style = Stroke(strokePx))
                val p = progress.value
                if (p > 0f) {
                    val measure = PathMeasure().apply { setPath(outline, false) }
                    val traced = Path()
                    measure.getSegment(0f, measure.length * p, traced, true)
                    // Low alpha so the trace stays a whisper, not a highlight.
                    drawPath(traced, Color(0x558B96E6), style = Stroke(strokePx))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val fill = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                1f,
                                tween(holdMs.toInt(), easing = LinearEasing),
                            )
                        }
                        // Released before the hold completes -> normal tap ($1);
                        // still held when it fires -> the hidden free reset.
                        val released = withTimeoutOrNull(holdMs) { tryAwaitRelease() }
                        fill.cancel()
                        when (released) {
                            null -> onHold()
                            else -> {
                                if (released == true) onTap()
                                scope.launch { progress.animateTo(0f, tween(250)) }
                            }
                        }
                    },
                )
            }
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text("Reset code (\$1)", color = Color(0xFF8B96E6))
    }
}
