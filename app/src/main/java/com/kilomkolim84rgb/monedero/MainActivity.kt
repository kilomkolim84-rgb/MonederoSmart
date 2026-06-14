package com.kilomkolim84rgb.monedero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.database.FirebaseDatabase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Firebase directo, sin Room
        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("transacciones")
        
        setContent {
            MaterialTheme {
                Text("Receptor Monedero Paoyhan Activo")
            }
        }
    }
}
