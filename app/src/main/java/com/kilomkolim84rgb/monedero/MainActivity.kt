package com.kilomkolim84rgb.monedero

import android.app.AlertDialog
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

// ✅ TU CLAVE DE 6 DÍGITOS, CAMBIA AQUÍ LO QUE QUIERAS
const val CLAVE_VACIADO = "222777" // Ejemplo: "987654", "CESAR77" etc.

data class Movimiento(
    val fechaHora: String = "",
    val detalle: String = "",
    val montoIngresado: Int = 0,
    val totalAcumulado: Int = 0,
    val mac: String = "--",
    val ip: String = "--"
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
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top // ✅ AQUÍ ESTABA EL ERROR, YA ESTÁ PERFECTO
            ) {
                Text("MONEDERO SMART", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BotonDato("TEMP", temperatura)
                    BotonDato("VOLT", voltaje)
                    BotonDato("ENERG", energia)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { pedirClave() },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("VACIAR", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL", fontSize = 14.sp)
                        Text("$totalGeneral SOLES", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Último: $ultimoMovimiento", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(historial) { mov ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // IZQUIERDA: FECHA Y MONTOS
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mov.fechaHora, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(mov.detalle == "Monedero vaciado"){
                                        Text("⚠️ Vaciado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("+${mov.montoIngresado} | Total: ${mov.totalAcumulado}", fontSize = 12.sp)
                                    }
                                }

                                // DERECHA: FOTO + MAC/IP + QR
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Card(
                                        modifier = Modifier.size(45.dp, 45.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📷", fontSize = 18.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.width(100.dp)) {
                                        Text("MAC: ${mov.mac}", fontSize = 10.sp)
                                        Text("IP: ${mov.ip}", fontSize = 10.sp)
                                    }
                                    Card(
                                        modifier = Modifier.size(45.dp, 45.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("QR", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // VENTANA DE CLAVE
    private fun pedirClave() {
        val entrada = android.widget.EditText(this)
        entrada.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("CLAVE DE SEGURIDAD")
            .setMessage("Escribe tu clave de 6 dígitos:")
            .setView(entrada)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                val clave = entrada.text.toString()
                if(clave == CLAVE_VACIADO) vaciar()
                else Toast.makeText(this, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    @Composable
    fun BotonDato(etiqueta: String, valor: String) {
        Card(modifier = Modifier.size(75.dp, 45.dp), shape = RoundedCornerShape(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Alignment.Center) {
                Text(etiqueta, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Text(valor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun vaciar() {
        val fecha = formatoFecha.format(Date())
        val reg = Movimiento(fecha, "Monedero vaciado", 0, 0)
        historial = listOf(reg) + historial
        db.child("historial").push().setValue(reg)
        db.child("total_general").setValue(0)
        db.child("ultimo_movimiento").setValue("Monedero vaciado")
        totalAnterior = 0
        hablar("Monedero vaciado")
        Toast.makeText(this, "✅ Vaciado correctamente", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
