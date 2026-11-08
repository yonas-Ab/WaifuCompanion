package com.sae.waifucompanion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sae_settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val KEY_IP = stringPreferencesKey("server_ip")
        val KEY_PORT = stringPreferencesKey("server_port")
        val KEY_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val serverIpFlow: Flow<String> = context.dataStore.data.map { it[KEY_IP] ?: "127.0.0.1" }
    val serverPortFlow: Flow<String> = context.dataStore.data.map { it[KEY_PORT] ?: "8000" }
    val isDarkThemeFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DARK_THEME] ?: true }
    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: false }

    suspend fun saveServerSettings(ip: String, port: String) {
        context.dataStore.edit {
            it[KEY_IP] = ip.trim()
            it[KEY_PORT] = port.trim()
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_THEME] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }
}