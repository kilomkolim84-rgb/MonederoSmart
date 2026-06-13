package com.tuusuario.monedero

import android.os.Bundle
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
import com.google.firebase.database.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MonederoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonederoApp() {
    var totalDinero by remember { mutableStateOf(0.0) }
    var totalMonedas by remember { mutableStateOf(0) }
    var ultimaMoneda by remember { mutableStateOf("Nunca") }
    var conectado by remember { mutableStateOf(false) }

    // Escucha cambios en Firebase
    LaunchedEffect(Unit) {
        val database = FirebaseDatabase.getInstance().getReference("monedero")
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalDinero = snapshot.child("totalDinero").getValue(Double::class.java) ?: 0.0
                totalMonedas = snapshot.child("totalMonedas").getValue(Int::class.java) ?: 0
                ultimaMoneda = snapshot.child("ultimaMoneda").getValue(String::class.java) ?: "Nunca"
                conectado = true
            }
            override fun onCancelled(error: DatabaseError) {
                conectado = false
            }
        })
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Mi Monedero Electrónico") }) }
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Total Ahorrado", fontSize = 20.sp)
                Text(
                    "S/ %.2f".format(totalDinero),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text("$totalMonedas monedas ingresadas")
                Text("Última moneda: $ultimaMoneda", fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                AssistChip(
                    onClick = { },
                    label = { Text(if(conectado) "Conectado" else "Sin conexión") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if(conectado) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.errorContainer
                    )
                )
                
                Spacer(Modifier.height(40.dp))
                Button(onClick = {
                    FirebaseDatabase.getInstance().getReference("monedero").apply {
                        child("totalDinero").setValue(0.0)
                        child("totalMonedas").setValue(0)
                    }
                }) {
                    Text("Vaciar Monedero")
                }
            }
        }
    }
}
