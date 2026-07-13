package com.kilomkolim84rgb.monedero

import android.os.Bundle // ✅ ESTE FALTABA, LO AGREGUÉ
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*

// ------------------- ESTRUCTURAS DE DATOS -------------------
data class RegistroIngreso(
    val id: String = "",
    val origen: String = "",
    val monto: Int = 0,
    val fecha: String = "",
    val hora: String = "",
    val ticket: String = "",
    val fotoUrl: String = ""
)

data class DatosSensores(
    val temperatura: Float = 0f,
    val humedad: Float = 0f,
    val rayos: Int = 0,
    val voltaje: Float = 0f
)

class MainActivity : ComponentActivity() {
    private val db = FirebaseDatabase.getInstance().reference
    private var totalGeneral by mutableStateOf(0)
    private var listaIngresos by mutableStateOf(listOf<RegistroIngreso>())
    private var sensores by mutableStateOf(DatosSensores())
    private var mostrarDialogo by mutableStateOf(false)
    private var claveIngresada by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        escucharDatos()
        setContent { PantallaPrincipal() }
    }

    // ------------------- ESCUCHA DE FIREBASE -------------------
    private fun escucharDatos() {
        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalGeneral = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("historial").orderByChild("fecha").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<RegistroIngreso>()
                snapshot.children.reversed().forEach { hijo ->
                    hijo.getValue(RegistroIngreso::class.java)?.let {
                        lista.add(it.copy(id = hijo.key ?: ""))
                    }
                }
                listaIngresos = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("sensores").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sensores = snapshot.getValue(DatosSensores::class.java) ?: DatosSensores()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // ------------------- ACCIÓN DE VACIADO -------------------
    private fun confirmarVaciado() {
        if(claveIngresada == "1234") { // ✅ PON TU CLAVE AQUÍ
            db.child("total_general").setValue(0)
            db.child("historial").removeValue()
            Toast.makeText(this, "✅ MONEDERO VACIADO", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ CLAVE INCORRECTA", Toast.LENGTH_SHORT).show()
        }
        mostrarDialogo = false
        claveIngresada = ""
    }

    // ------------------- INTERFAZ PRINCIPAL -------------------
    @Composable
    fun PantallaPrincipal() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MONEDERO SMART", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 🔹 TOTAL Y BOTÓN VACIAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "S/ $totalGeneral.00",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("TOTAL ACUMULADO", fontSize = 16.sp, color = Color.Gray)
                    }
                    IconButton(
                        onClick = { mostrarDialogo = true },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFD32F2F), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Filled.Delete, "Vaciar", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 BARRA AZUL CON 4 DATOS
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DatoBarra("🌡️", "TEMP", "${sensores.temperatura}°C")
                        Divider(modifier = Modifier.height(40.dp), color = Color.White.copy(alpha = 0.3f))
                        DatoBarra("💧", "HUM", "${sensores.humedad}%")
                        Divider(modifier = Modifier.height(40.dp), color = Color.White.copy(alpha = 0.3f))
                        DatoBarra("⚡", "RAYOS", "${sensores.rayos}km")
                        Divider(modifier = Modifier.height(40.dp), color = Color.White.copy(alpha = 0.3f))
                        DatoBarra("🔌", "VOLT", "${sensores.voltaje}V")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 🔹 HISTORIAL
                Text(
                    "HISTORIAL DE INGRESOS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listaIngresos) { reg -> TarjetaRegistro(reg) }
                }
            }

            // 🔹 DIÁLOGO DE CLAVE
            if (mostrarDialogo) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogo = false },
                    title = { Text("VACIAR MONEDERO") },
                    text = {
                        TextField(
                            value = claveIngresada,
                            onValueChange = { claveIngresada = it },
                            label = { Text("CLAVE DE SEGURIDAD") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = { confirmarVaciado() }) { Text("ACEPTAR") }
                    },
                    dismissButton = {
                        Button(
                            onClick = { mostrarDialogo = false; claveIngresada = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) { Text("CANCELAR") }
                    }
                )
            }
        }
    }

    // 🔹 COMPONENTES AUXILIARES
    @Composable
    fun DatoBarra(icono: String, etiqueta: String, valor: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icono, fontSize = 24.sp, color = Color.White)
            Text(etiqueta, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(valor, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    fun TarjetaRegistro(reg: RegistroIngreso) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ESPACIO PARA FOTO
                Card(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("[FOTO]", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // DATOS DEL REGISTRO
                Column(modifier = Modifier.weight(1f)) {
                    Text(reg.origen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("MONTO: S/ ${reg.monto}", fontSize = 14.sp)
                    Text("${reg.fecha}  ${reg.hora}", fontSize = 13.sp, color = Color.Gray)
                }

                // TICKET
                Text(
                    "#${reg.ticket}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C)
                )
            }
        }
    }
}
