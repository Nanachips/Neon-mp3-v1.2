package com.neonplayer

import android.app.Application

class App : Application() {
    lateinit var db: AppDb
    lateinit var prefs: AppPrefs
    lateinit var hub: PlayerHub

    override fun onCreate() {
        super.onCreate()
        db = AppDb.build(this)
        prefs = AppPrefs(this)
        ThemeState.customs.addAll(customThemesFromJson(prefs.customThemesJson))
        ThemeState.setById(prefs.themeId)
        hub = PlayerHub(this, db.dao(), prefs)
    }
}
