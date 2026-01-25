package com.ccernusca.githabit

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataManager(private val context: Context) {
    private val HANDLE_KEY = stringPreferencesKey("github_handle")

    // Read the handle as a Flow (a stream of data)
    val getHandle: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[HANDLE_KEY] ?: "" }

    // Save the handle
    suspend fun saveHandle(handle: String) {
        context.dataStore.edit { preferences ->
            preferences[HANDLE_KEY] = handle
        }
    }
}