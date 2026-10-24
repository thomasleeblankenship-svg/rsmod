package dev.audaro.runemobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One runnable on-device script: its id (the config enum name) and display name. */
data class MicrobotScript(
    val id: String,
    val name: String,
)

/** Microbot Mobile's live state, from MobileUiBridge.microbotStatus(). */
data class MicrobotStatus(
    val ready: Boolean,
    val running: Boolean,
    val status: String,
    val scripts: List<MicrobotScript>,
) {
    companion object {
        val UNKNOWN = MicrobotStatus(ready = false, running = false, status = "Connecting…", scripts = emptyList())
    }
}

/** Parses the bridge's status JSON; an empty or malformed payload (bridge not up yet) reads as [MicrobotStatus.UNKNOWN]. */
fun parseMicrobotStatus(json: String?): MicrobotStatus =
    try {
        val obj = JSONObject(json ?: "")
        val arr = obj.optJSONArray("scripts")
        val scripts =
            (0 until (arr?.length() ?: 0)).map { i ->
                val s = arr!!.getJSONObject(i)
                MicrobotScript(s.optString("id"), s.optString("name"))
            }
        MicrobotStatus(
            ready = obj.optBoolean("ready", false),
            running = obj.optBoolean("running", false),
            status = obj.optString("status", ""),
            scripts = scripts,
        )
    } catch (t: Throwable) {
        MicrobotStatus.UNKNOWN
    }

/**
 * Microbot Mobile's own screen: a control card (live status, script picker, Start/Stop) above the selected
 * script's settings. The bridge already filters the settings to the selected script, so switching scripts
 * swaps the list below. Start/Stop writes the same `runScript` setting the plugin watches each game tick.
 */
@Composable
fun MicrobotScreen(
    pluginName: String,
    onBack: () -> Unit,
    onTextActive: (Boolean) -> Unit = {},
) {
    ConfigScreen(
        MICROBOT_GROUP,
        pluginName,
        onBack,
        onTextActive,
        hiddenKeys = setOf(MICROBOT_SCRIPT_KEY, MICROBOT_RUN_KEY),
        header = { items, change -> MicrobotControlCard(items, change) },
    )
}

@Composable
private fun MicrobotControlCard(
    items: List<ConfigItem>,
    change: (ConfigItem, String) -> Unit,
) {
    var live by remember { mutableStateOf(MicrobotStatus.UNKNOWN) }
    // Poll while the card is on screen (the LaunchedEffect is cancelled when it leaves).
    LaunchedEffect(Unit) {
        while (true) {
            val json = withContext(Dispatchers.IO) { JvmLauncher.onBridge { JvmLauncher.microbotStatus() }.get() }
            live = parseMicrobotStatus(json)
            delay(1000)
        }
    }
    val scriptItem = items.firstOrNull { it.key == MICROBOT_SCRIPT_KEY }
    val runItem = items.firstOrNull { it.key == MICROBOT_RUN_KEY }
    // "Run" is what the user asked for; "running" is what the plugin reports (it may still be starting).
    val runRequested = runItem?.value?.toBoolean() ?: false
    val active = live.running || runRequested

    Surface(
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (live.running) Color(0xFF66BB6A) else Color(0xFF9E9E9E),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.size(10.dp),
                ) {}
                Spacer(Modifier.size(8.dp))
                Text(if (live.running) "Running" else "Stopped", color = Color.White, fontSize = 16.sp)
            }
            if (live.status.isNotEmpty()) {
                Text(live.status, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text("Script", color = Color.Gray, fontSize = 12.sp)
            if (scriptItem != null) {
                ScriptPicker(live.scripts, scriptItem.value, enabled = !active) { change(scriptItem, it) }
            }
            if (active) {
                Text("Stop the script to change it.", color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(Modifier.size(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { if (runItem != null) change(runItem, (!active).toString()) },
                    enabled = runItem != null,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = if (active) Color(0xFFC62828) else Color(0xFF2E7D32),
                        ),
                    modifier = Modifier.semantics { contentDescription = if (active) "Stop script" else "Start script" },
                ) { Text(if (active) "Stop" else "Start", color = Color.White) }
            }
        }
    }
}

/** A dropdown of scripts by display name; writes the chosen script's id. Falls back to ids before the list loads. */
@Composable
private fun ScriptPicker(
    scripts: List<MicrobotScript>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = scripts.firstOrNull { it.id == selectedId }?.name ?: selectedId ?: "—"
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Script, $label" },
        ) { Text("$label  ▾", color = if (enabled) Color.White else Color.Gray) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            scripts.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = {
                    expanded = false
                    if (s.id != selectedId) onSelect(s.id)
                })
            }
        }
    }
}
