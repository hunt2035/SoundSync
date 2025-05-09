package com.wanderreads.ebook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// 创建单例的DataStore实例
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ebook_settings") 