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
import java.text.SimpleDateFormat
import java.util.*

data class Movimiento(
    val fechaHora: String = "",
    val detalle: String = "",
    val montoIngresado: Int = 0,
    val totalAcumulado: Int = 0,
    val mac: String = "--",
    val ip: String = "--",
    val tieneFoto: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val db = FirebaseDatabase.getInstance().reference
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) tts?.language = Locale("es", "PE")
        }

        setContent { PantallaPrincipal() }
        escucharDatos()
        cargarHistorialGuardado()
    }

    private fun hablar(texto: String) {
        if(vozLista) tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private var totalGeneral by mutableStateOf(0)
    private var ultimoMovimiento by mutableStateOf("-")
    private var historial by mutableStateOf(listOf<Movimiento>())
    private var temperatura by mutableStateOf("-- °C")
    private var voltaje by mutableStateOf("-- V")
    private var energia by mutableStateOf("-- A")
    private var totalAnterior = 0

    private fun cargarHistorialGuardado() {
        db.child("historial").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Movimiento>()
                for(item in snapshot.children){
                    lista.add(
                        Movimiento(
                            fechaHora = item.child("fechaHora").getValue(String::class.java) ?: "",
                            detalle = item.child("detalle").getValue(String::class.java) ?: "",
                            montoIngresado = item.child("montoIngresado").getValue(Int::class.java) ?: 0,
                            totalAcumulado = item.child("totalAcumulado").getValue(Int::class.java) ?: 0,
                            mac = item.child("mac").getValue(String::class.java) ?: "--",
                            ip = item.child("ip").getValue(String::class.java) ?: "--"
                        )
                    )
                }
                historial = lista.reversed()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun escucharDatos() {
        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevoTotal = snapshot.getValue(Int::class.java) ?: 0

                if(nuevoTotal > totalAnterior){
                    val cuantoEntro = nuevoTotal - totalAnterior
                    val fecha = formatoFecha.format(Date())
                    val nuevoMov = Movimiento(fecha, "Ingreso", cuantoEntro, nuevoTotal)
                    historial = listOf(nuevoMov) + historial
                    db.child("historial").push().setValue(nuevoMov)
                    db.child("ultimo_movimiento").setValue("Ingreso: $cuantoEntro soles")
                    hablar("Ingreso $cuantoEntro soles. Total $nuevoTotal soles")
                }

                totalAnterior = nuevoTotal
                totalGeneral = nuevoTotal
            }
            override fun onCancelled(e: DatabaseError) {
                Toast.makeText(this@MainActivity, "Sin conexión", Toast.LENGTH_SHORT).show()
            }
        })

        db.child("ultimo_movimiento").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                ultimoMovimiento = s.getValue(String::class.java) ?: "-"
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        // SENSORES
        db.child("sensores/temperatura").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(Double::class.java)
                temperatura = if(v!=null) String.format("%.1f °C", v) else "-- °C"
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores/voltaje").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(Double::class.java)
                voltaje = if(v!=null) String.format("%.1f V", v) else "-- V"
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores/corriente").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(Double::class.java)
                energia = if(v!=null) String.format("%.2f A", v) else "-- A"
            }
            override fun onCancelled(e: DatabaseError) {}
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
                    onClick = { vaciar() },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("VACIAR MONEDERO", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL ACUMULADO", fontSize = 16.sp)
                        Text("$totalGeneral SOLES", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Último ingreso: $ultimoMovimiento", fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Historial de movimientos", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(historial) { mov ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // FILA PRINCIPAL: DATOS Y QR
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("📅 ${mov.fechaHora}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(mov.detalle == "Monedero vaciado"){
                                            Text("⚠️ ${mov.detalle}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        } else {
                                            Text("💵 ${mov.detalle}: ${mov.montoIngresado} soles", fontSize = 14.sp)
                                            Text("🧾 Total: ${mov.totalAcumulado} soles", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.size(60.dp, 60.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "Código\nQR",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 12.sp,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                    }
                                }
                                // ✅ FILA NUEVA: FOTO Y DATOS DE RED
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Card(
                                        modifier = Modifier.size(50.dp, 50.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📷", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("📶 MAC: ${mov.mac}", fontSize = 11.sp)
                                        Text("🌐 IP: ${mov.ip}", fontSize = 11.sp)
                                    }
                                }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Alignment.Center) {
                Text(etiqueta, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(valor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun vaciar() {
        val fecha = formatoFecha.format(Date())
        val registroVaciado = Movimiento(
            fechaHora = fecha,
            detalle = "Monedero vaciado",
            montoIngresado = 0,
            totalAcumulado = 0
        )
        historial = listOf(registroVaciado) + historial
        db.child("historial").push().setValue(registroVaciado)

        db.child("total_general").setValue(0)
        db.child("ultimo_movimiento").setValue("Monedero vaciado")
        totalAnterior = 0
        hablar("Monedero vaciado")

        Toast.makeText(this, "Monedero vaciado ✅", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
