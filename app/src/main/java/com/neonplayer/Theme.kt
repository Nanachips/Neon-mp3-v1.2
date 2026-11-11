package com.neonplayer

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class AppTheme(
    val id: String, val label: String, val light: Boolean,
    val bg: Color, val surface: Color, val card: Color,
    val hi: Color, val lo: Color,
    val c1: Color, val c2: Color, val onAccent: Color,
    val custom: Boolean = false
)

val BuiltInThemes = listOf(
    AppTheme("neon", "Néon", false, Color(0xFF05050A), Color(0xFF0B0B16), Color(0xFF12122A), Color(0xFFEDEDFF), Color(0xFF8B8BA7), Color(0xFF4D8DFF), Color(0xFF9B5CFF), Color.White),
    AppTheme("noir_blanc", "Noir & blanc", false, Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF181818), Color(0xFFFFFFFF), Color(0xFF9A9A9A), Color(0xFFFFFFFF), Color(0xFFCFCFCF), Color.Black),
    AppTheme("blanc_noir", "Blanc & noir", true, Color(0xFFF6F6F6), Color(0xFFFFFFFF), Color(0xFFEAEAEA), Color(0xFF000000), Color(0xFF666666), Color(0xFF000000), Color(0xFF444444), Color.White),
    AppTheme("vert", "Noir & vert", false, Color(0xFF000000), Color(0xFF121212), Color(0xFF242424), Color(0xFFFFFFFF), Color(0xFFB3B3B3), Color(0xFF1DB954), Color(0xFF1ED760), Color.Black),
    AppTheme("cyber", "Cyber rouge", false, Color(0xFF0A0505), Color(0xFF140A0A), Color(0xFF241010), Color(0xFFFFEDED), Color(0xFFA78B8B), Color(0xFFFF3D3D), Color(0xFFFF8A3D), Color.White),
    AppTheme("ocean", "Océan", false, Color(0xFF020A10), Color(0xFF07131C), Color(0xFF0C2230), Color(0xFFE6F7FF), Color(0xFF7FA3B5), Color(0xFF00B8FF), Color(0xFF00F0C8), Color.Black),
    AppTheme("sunset", "Sunset rose", false, Color(0xFF0D050F), Color(0xFF170A1A), Color(0xFF26122B), Color(0xFFFFEDF6), Color(0xFFB08AA0), Color(0xFFFF4D9D), Color(0xFFFFB84D), Color.Black),
    AppTheme("or", "Noir & or", false, Color(0xFF0A0804), Color(0xFF14100A), Color(0xFF221B0E), Color(0xFFFFF6DD), Color(0xFFA89A72), Color(0xFFFFD54A), Color(0xFFC9A227), Color.Black),
    AppTheme("lavande", "Lavande clair", true, Color(0xFFF4F0FF), Color(0xFFFFFFFF), Color(0xFFE6DEFF), Color(0xFF1B1038), Color(0xFF6E6490), Color(0xFF6C4DFF), Color(0xFFB04DFF), Color.White)
)

/** Construit un thème complet à partir de 3 couleurs choisies par l'utilisateur. */
fun makeCustomTheme(id: String, label: String, light: Boolean, bg: Color, c1: Color, c2: Color): AppTheme {
    val surface = if (light) lerp(bg, Color.White, 0.6f) else lerp(bg, Color.White, 0.04f)
    val card = if (light) lerp(bg, Color.Black, 0.06f) else lerp(bg, Color.White, 0.10f)
    val hi = if (light) Color(0xFF111111) else Color(0xFFF5F5F5)
    val lo = lerp(hi, bg, 0.5f)
    val on = if (c2.luminance() > 0.5f) Color.Black else Color.White
    return AppTheme(id, label, light, bg, surface, card, hi, lo, c1, c2, on, true)
}

fun customThemesToJson(list: List<AppTheme>): String {
    val a = JSONArray()
    list.forEach {
        a.put(
            JSONObject().put("id", it.id).put("label", it.label).put("light", it.light)
                .put("bg", it.bg.toArgb()).put("c1", it.c1.toArgb()).put("c2", it.c2.toArgb())
        )
    }
    return a.toString()
}

fun customThemesFromJson(s: String): List<AppTheme> = try {
    val a = JSONArray(s)
    List(a.length()) {
        val o = a.getJSONObject(it)
        makeCustomTheme(o.getString("id"), o.getString("label"), o.getBoolean("light"), Color(o.getInt("bg")), Color(o.getInt("c1")), Color(o.getInt("c2")))
    }
} catch (e: Exception) { emptyList() }

object ThemeState {
    var current by mutableStateOf(BuiltInThemes[0])
    val customs = mutableStateListOf<AppTheme>()

    fun all(): List<AppTheme> = BuiltInThemes + customs.toList()

    fun setById(id: String) { current = all().firstOrNull { it.id == id } ?: BuiltInThemes[0] }

    fun saveCustom(t: AppTheme, prefs: AppPrefs) {
        val i = customs.indexOfFirst { it.id == t.id }
        if (i >= 0) customs[i] = t else customs.add(t)
        prefs.customThemesJson = customThemesToJson(customs.toList())
        current = t
        prefs.themeId = t.id
    }

    fun deleteCustom(id: String, prefs: AppPrefs) {
        customs.removeAll { it.id == id }
        prefs.customThemesJson = customThemesToJson(customs.toList())
        if (current.id == id) { current = BuiltInThemes[0]; prefs.themeId = BuiltInThemes[0].id }
    }
}

val NeonBg: Color get() = ThemeState.current.bg
val NeonSurface: Color get() = ThemeState.current.surface
val NeonCard: Color get() = ThemeState.current.card
val Blue: Color get() = ThemeState.current.c1
val Violet: Color get() = ThemeState.current.c2
val TextHi: Color get() = ThemeState.current.hi
val TextLo: Color get() = ThemeState.current.lo
val NeonBrush: Brush get() = Brush.linearGradient(listOf(ThemeState.current.c1, ThemeState.current.c2))

fun Modifier.neonBorder(shape: Shape = RoundedCornerShape(16.dp), width: Dp = 1.dp): Modifier =
    this.border(width, NeonBrush, shape)

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    val t = ThemeState.current
    val scheme = if (t.light) lightColorScheme(
        primary = t.c2, onPrimary = t.onAccent, secondary = t.c1, onSecondary = t.onAccent,
        background = t.bg, onBackground = t.hi, surface = t.surface, onSurface = t.hi,
        surfaceVariant = t.card, onSurfaceVariant = t.lo, outline = t.c2
    ) else darkColorScheme(
        primary = t.c2, onPrimary = t.onAccent, secondary = t.c1, onSecondary = t.onAccent,
        background = t.bg, onBackground = t.hi, surface = t.surface, onSurface = t.hi,
        surfaceVariant = t.card, onSurfaceVariant = t.lo, outline = t.c2
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
