package com.offlinesync

import android.app.Application
import android.content.Context
import com.offlinesync.utils.LanguageManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfflineSyncApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.setLocale(base))
    }
}