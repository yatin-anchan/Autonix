package com.example.autonix_work_in_progress

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

object FirebaseUtils {

    fun getCurrentUser(): FirebaseUser? {
        return FirebaseAuth.getInstance().currentUser
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUser() != null
    }

    fun getUserDisplayName(): String? {
        return getCurrentUser()?.displayName
    }

    fun getUserEmail(): String? {
        return getCurrentUser()?.email
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}