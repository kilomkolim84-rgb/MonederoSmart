package com.kilomkolim84rgb.monedero.emisor

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 👇 INICIALIZA FIREBASE A PRUEBA DE CRASH
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("Firebase", "Error inicializando: ${e.message}")
        }
        
        setContent {
            MaterialTheme {
                MonederoScreen()
            }
        }
    }
}

@Composable
fun MonederoScreen() {
    var saldo by remember { mutableStateOf(0) }
    var monto by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val db = Firebase.database("https://monedero-soles-default-rtdb.firebaseio.com/") // 👈 URL EXPLÍCITA
    val monederoRef = db.reference

    LaunchedEffect(Unit) {
        try {
            monederoRef.child("total_soles").get()
                .addOnSuccessListener {
                    saldo = it.getValue(Int::class.java) ?: 0
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Firebase error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Monedero Emisor", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        
        Text("Saldo actual:", fontSize = 18.sp)
        Text("S/ $saldo", fontSize = 48.sp, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it },
            label = { Text("Monto a depositar") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                val montoInt = monto.toIntOrNull() ?: 0
                if (montoInt > 0) {
                    val nuevoSaldo = saldo + montoInt
                    monederoRef.child("total_soles").setValue(nuevoSaldo)
                        .addOnFailureListener {
                            Toast.makeText(context, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    monederoRef.child("ultimo_movimiento").setValue(montoInt)
                    saldo = nuevoSaldo
                    monto = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("DEPOSITAR", fontSize = 18.sp)
        }
    }
}
