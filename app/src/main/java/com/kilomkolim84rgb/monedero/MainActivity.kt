package com.kilomkolim84rgb.monedero

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import java.util.*

// CLASE PARA EL HISTORIAL
data class Movimiento(
    val fecha: String = "",
    val monto: Int = 0,
    val total: Int = 0
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val db = FirebaseDatabase.getInstance().reference
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // INICIAMOS LA VOZ
        tts = TextToSpeech(this, this)
        setContent { PantallaPrincipal() }
        escucharDatosFirebase()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "PE")
        }
    }

    private fun hablar(texto: String) {
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private var totalSoles by mutableStateOf(0)
    private var ultimoIngreso by mutableStateOf("-")
    private var temperatura by mutableStateOf("-- °C")
    private var voltaje by mutableStateOf("-- V")
    private var energia by mutableStateOf("-- A")
    private var historial by mutableStateOf(listOf<Movimiento>())
    private var ultimoTotalAnterior = 0

    private fun escucharDatosFirebase() {
        // TOTAL GENERAL
        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevoTotal = snapshot.getValue(Int::class.java) ?: 0
                if(nuevoTotal > ultimoTotalAnterior){
                    val ingreso = nuevoTotal - ultimoTotalAnterior
                    hablar("Ingreso $ingreso soles. Total $nuevoTotal soles")
                }
                ultimoTotalAnterior = totalSoles
                totalSoles = nuevoTotal
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Sin conexión", Toast.LENGTH_SHORT).show()
            }
        })

        // ÚLTIMO MOVIMIENTO
        db.child("ultimo_movimiento").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimoIngreso = snapshot.getValue(String::class.java) ?: "-"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // HISTORIAL COMPLETO
        db.child("historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Movimiento>()
                for(item in snapshot.children){
                    lista.add(
                        Movimiento(
                            fecha = item.child("fecha").getValue(String::class.java) ?: "",
                            monto = item.child("monto").getValue(Int::class.java) ?: 0,
                            total = item.child("total").getValue(Int::class.java) ?: 0
                        )
                    )
                }
                historial = lista.reversed() // MUESTRA LO ÚLTIMO ARRIBA
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // SENSORES
        db.child("sensores/temperatura").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val t = snapshot.getValue(Double::class.java)
                temperatura = if(t!=null) String.format("%.1f °C", t) else "-- °C"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("sensores/voltaje").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val v = snapshot.getValue(Double::class.java)
                voltaje = if(v!=null) String.format("%.1f V", v) else "-- V"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.child("sensores/corriente").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val a = snapshot.getValue(Double::class.java)
                energia = if(a!=null) String.format("%.2f A", a) else "-- A"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    @Composable
    fun PantallaPrincipal() {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text("MONEDERO SMART", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BotonDato("TEMPERATURA", temperatura)
                    BotonDato("VOLTAJE", voltaje)
                    BotonDato("ENERGÍA", energia)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { vaciarMonedero() },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("VACIAR MONEDERO", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL ACUMULADO", fontSize = 16.sp)
                        Text("$totalSoles SOLES", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Último ingreso: $ultimoIngreso", fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Historial de movimientos", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                // LISTA DEL HISTORIAL
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(historial) { mov ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(mov.fecha, fontSize = 12.sp)
                                Text("Ingreso: ${mov.monto} soles | Total: ${mov.total} soles", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BotonDato(etiqueta: String, valor: String) {
        Card(modifier = Modifier.size(90.dp, 55.dp), shape = RoundedCornerShape(18.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(etiqueta, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(valor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun vaciarMonedero() {
        db.child("total_general").setValue(0)
        db.child("ultimo_movimiento").setValue("Monedero vaciado")
        hablar("Monedero vaciado")
        Toast.makeText(this, "Monedero vaciado ✅", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}
