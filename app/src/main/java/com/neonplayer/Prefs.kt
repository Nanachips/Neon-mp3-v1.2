package com.neonplayer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

class AppPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("neon_prefs", Context.MODE_PRIVATE)

    val name = MutableStateFlow(sp.getString("name", "Utilisateur") ?: "Utilisateur")
    val bio = MutableStateFlow(sp.getString("bio", "") ?: "")
    val photo = MutableStateFlow(sp.getString("photo", null))
    val autoArtists = MutableStateFlow(sp.getBoolean("autoArtists", true))

    fun setName(v: String) { name.value = v; sp.edit().putString("name", v).apply() }
    fun setBio(v: String) { bio.value = v; sp.edit().putString("bio", v).apply() }
    fun setPhoto(v: String?) { photo.value = v; sp.edit().putString("photo", v).apply() }
    fun setAutoArtists(v: Boolean) { autoArtists.value = v; sp.edit().putBoolean("autoArtists", v).apply() }

    fun trees(): List<String> = (sp.getStringSet("trees", emptySet()) ?: emptySet()).toList()
    fun addTree(u: String) {
        val s = HashSet(sp.getStringSet("trees", emptySet()) ?: emptySet())
        s.add(u)
        sp.edit().putStringSet("trees", s).apply()
    }

    var trackSort: Int
        get() = sp.getInt("trackSort", 0)
        set(v) { sp.edit().putInt("trackSort", v).apply() }

    var libSort: Int
        get() = sp.getInt("libSort", 0)
        set(v) { sp.edit().putInt("libSort", v).apply() }

    var fadeSec: Int
        get() = sp.getInt("fadeSec", 0)
        set(v) { sp.edit().putInt("fadeSec", v).apply() }

    var customThemesJson: String
        get() = sp.getString("customThemes", "[]") ?: "[]"
        set(v) { sp.edit().putString("customThemes", v).apply() }

    var themeId: String
        get() = sp.getString("theme", "neon") ?: "neon"
        set(v) { sp.edit().putString("theme", v).apply() }

    var eqEnabled: Boolean
        get() = sp.getBoolean("eqOn", false)
        set(v) { sp.edit().putBoolean("eqOn", v).apply() }

    var eqLevels: List<Int>
        get() = (sp.getString("eqLv", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        set(v) { sp.edit().putString("eqLv", v.joinToString(",")).apply() }
}
