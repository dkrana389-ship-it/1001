package com.deepinnovations.reelcounter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private val Bg = Color(0xFF0B0B0D)
private val PanelBg = Color(0xFF141417)
private val Line = Color(0xFF26262C)
private val Ink = Color(0xFFF2F2F3)
private val Muted = Color(0xFF8A8A93)
private val Ok = Color(0xFF3DDC84)
private val Bad = Color(0xFFE5484D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ink,
                    onPrimary = Bg,
                    secondary = Muted,
                    background = Bg,
                    onBackground = Ink,
                    surface = PanelBg,
                    onSurface = Ink,
                    surfaceVariant = Color(0xFF2A2A30),
                    onSurfaceVariant = Muted,
                    outline = Line,
                    outlineVariant = Line
                )
            ) { Home() }
        }
    }
}

fun isServiceEnabled(c: Context): Boolean {
    val enabled = Settings.Secure.getString(
        c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.startsWith(c.packageName) && it.contains("ReelAccessibilityService")
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${sec}s" else "${sec}s"
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelBg)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PanelBg)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(label, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Home() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val reels = remember(tick) { Store.reels(ctx) }
    val ms = remember(tick) { Store.millis(ctx) }
    val enabled = remember(tick) { isServiceEnabled(ctx) }
    val diag = remember(tick) { Store.diag(ctx) }
    val diagAgo = remember(tick) { (System.currentTimeMillis() - Store.diagAt(ctx)) / 1000 }
    val week = remember(tick) {
        (6 downTo 0).map {
            val d = LocalDate.now().minusDays(it.toLong())
            Triple(d.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.ENGLISH), Store.reels(ctx, d.toString()), it == 0)
        }
    }
    val avg = week.sumOf { it.second } / 7
    var name by remember { mutableStateOf(Store.username(ctx)) }
    var strict by remember { mutableStateOf(Store.strict(ctx)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("REEL COUNTER", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium)

        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                if (name.isBlank()) "Aaj" else "@${name.trim().removePrefix("@")}",
                color = Muted, fontSize = 15.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$reels", color = Ink, fontSize = 88.sp, fontWeight = FontWeight.Light)
                Text("  reels", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 20.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("INSTAGRAM TIME", fmt(ms), Modifier.weight(1f))
            Stat("7-DIN AVERAGE", "$avg / din", Modifier.weight(1f))
        }

        Panel {
            Text("Last 7 din", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            val max = (week.maxOf { it.second }).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                week.forEach { (label, count, isToday) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
                        Text("$count", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(18.dp)
                                .height((6 + 80f * count / max).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isToday) Ink else Color(0xFF34343B))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = if (isToday) Ink else Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (enabled) Ok else Bad))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (enabled) "Counter ON hai" else "Counter OFF hai",
                    color = Ink, fontWeight = FontWeight.Medium
                )
            }
            if (!enabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "1. 'Accessibility kholo' dabao, Installed apps mein Reel Counter ko ON karo.\n" +
                        "2. ON na ho paye to 'App info' kholo, upar ⋮ (3 dot) mein 'Allow restricted settings' karo, phir step 1 dobara.\n" +
                        "3. Instagram kholo, camera ke paas counter dikhega.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Bg),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Accessibility kholo") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("App info (Restricted settings)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Battery: No restrictions") }
            Spacer(Modifier.height(10.dp))
            Text(
                "Vivo / Xiaomi / Oppo / Realme: Battery mein Reel Counter ko Unrestricted aur Autostart ON rakho, warna phone service band kar deta hai.",
                color = Muted, fontSize = 12.sp, lineHeight = 17.sp
            )
        }

        Panel {
            Text("Detection status", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                if (diag.isBlank()) "Abhi koi data nahi. Service ON karke Instagram kholo." else diag,
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace
            )
            if (diag.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Aakhri update: ${diagAgo}s pehle", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }

        Panel {
            Text("Settings", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; Store.setUsername(ctx, it) },
                label = { Text("Instagram username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sirf Reels count karo", color = Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "OFF karne par backup mode Instagram ke har scroll ko ginta hai.",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Switch(checked = strict, onCheckedChange = { strict = it; Store.setStrict(ctx, it) })
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { Store.resetToday(ctx) }) {
                Text("Aaj ki counting reset karo", color = Bad)
            }
        }

        Text(
            "Privacy: app internet permission nahi leta. Sirf Instagram mein reel badli ya nahi, yahi dekhta hai. Koi text, message ya photo nahi padhta. Saara data sirf aapke phone mein rehta hai.",
            color = Muted.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}
