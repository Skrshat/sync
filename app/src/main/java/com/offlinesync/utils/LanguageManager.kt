package com.offlinesync.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "settings"
    private const val KEY_LANG = "app_lang"
    
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_RU = "ru"
    const val LANGUAGE_UK = "uk"
    
    fun setLocale(context: Context): Context {
        val lang = getCurrentLanguage(context)
        return updateResources(context, lang)
    }
    
    fun setLocale(context: Context, language: String): Context {
        persistLanguage(context, language)
        return updateResources(context, language)
    }
    
    fun getCurrentLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val systemLang = Locale.getDefault().language
        val defaultLang = when (systemLang) {
            "ru" -> LANGUAGE_RU
            "uk" -> LANGUAGE_UK
            else -> LANGUAGE_EN
        }
        return prefs.getString(KEY_LANG, defaultLang) ?: LANGUAGE_EN
    }
    
    fun setLanguage(context: Context, lang: String) {
        persistLanguage(context, lang)
    }
    
    private fun persistLanguage(context: Context, language: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language).apply()
    }
    
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        val config = context.resources.configuration
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
}