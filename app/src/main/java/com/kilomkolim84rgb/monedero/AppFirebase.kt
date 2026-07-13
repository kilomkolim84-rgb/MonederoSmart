package com.kilomkolim84rgb.monedero

import android.app.Application
import com.google.firebase.FirebaseApp

class AppFirebase : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this) // ✅ INICIA FIREBASE ANTES QUE TODO
    }
}
