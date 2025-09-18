package com.example.autonix_work_in_progress

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("MapsClonePrefs", Context.MODE_PRIVATE)

    fun saveRememberMe(remember: Boolean) {
        sharedPreferences.edit().putBoolean("remember_me", remember).apply()
    }

    fun getRememberMe(): Boolean {
        return sharedPreferences.getBoolean("remember_me", false)
    }

    fun saveUserCredentials(email: String) {
        sharedPreferences.edit().putString("saved_email", email).apply()
    }

    fun getSavedEmail(): String? {
        return sharedPreferences.getString("saved_email", "")
    }

    fun clearSavedCredentials() {
        sharedPreferences.edit().remove("saved_email").apply()
    }
}