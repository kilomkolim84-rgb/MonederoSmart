package com.kilomkolim84rgb.monedero

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

const val CLAVE_VACIADO = "222777"
const val CANAL_NOTIFICACIONES = "canal_monedero"

data class Movimiento(
    val fechaHora: String = "",
    val detalle: String = "",
    val montoIngresado: Double = 0.0,
    val totalAcumulado: Double = 0.0,
    val codigo: String = "",
    val alias: String = ""
)

class MainActivity : ComponentActivity() {
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private lateinit var prefs: SharedPreferences
    private val TOTAL_GUARDADO = "total_acumulado"
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ INICIALIZAR FIREBASE
        try {
            FirebaseApp.initializeApp(this)
            Toast.makeText(this@MainActivity, "✅ Firebase inicializado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "❌ ERROR Firebase: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        val db = FirebaseDatabase.getInstance().reference
        db.keepSynced(true)
        
        prefs = getSharedPreferences("MonederoPrefs", Context.MODE_PRIVATE)
        
        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) {
                tts?.language = Locale("es", "PE")
                tts?.setPitch(1.3f)
                tts?.setSpeechRate(0.85f)
            }
        }

        crearCanalNotificaciones()
        pedirPermisoNotificaciones()
        
        setContent { PantallaPrincipal() }
        
        Toast.makeText(this@MainActivity, "🔍 Escuchando historial...", Toast.LENGTH_SHORT).show()
        
        db.child("historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Toast.makeText(this@MainActivity, "📡 Leyendo: ${snapshot.childrenCount} tickets", Toast.LENGTH_SHORT).show()
                
                for (hijo in snapshot.children) {
                    val leido = hijo.child("leido_por_monedero").getValue(Boolean::class.java)
                    if (leido == true) continue

                    val codigo = hijo.child("codigo").getValue(String::class.java) ?: ""
                    val monto = hijo.child("monto").getValue(Double::class.java) ?: 0.0
                    val fecha = hijo.child("fecha").getValue(String::class.java) ?: ""

                    if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                    if (monto <= 0.0) continue

                    hijo.ref.child("leido_por_monedero").setValue(true)
                    
                    Toast.makeText(this@MainActivity, "✅ TICKET LEÍDO: $codigo — S/ $monto", Toast.LENGTH_LONG).show()

                    val nuevoTotal = leerTotalGuardado() + monto
                    totalGeneral = nuevoTotal
                    guardarTotal(nuevoTotal)

                    val nuevoTicket = Movimiento(fecha, "Ticket generado", monto, nuevoTotal, codigo, "")
                    historial = listOf(nuevoTicket) + historial
                    
                    hablarPlingUnaVez()
                    mostrarNotificacion(monto, nuevoTotal)
                    
                    val leidoTicket = hijo.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                    if (leidoTicket) hijo.ref.removeValue()
                }
            }
            
            override fun onCancelled(e: DatabaseError) {
                Toast.makeText(this@MainActivity, "❌ ERROR: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun leerTotalGuardado(): Double = prefs.getFloat(TOTAL_GUARDADO, 0f).toDouble()
    private fun guardarTotal(total: Double) = prefs.edit().putFloat(TOTAL_GUARDADO, total.toFloat()).apply()

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_NOTIFICACIONES, "Monedero Smart", NotificationManager.IMPORTANCE_HIGH)
            canal.description = "Confirmaciones de pago"
            canal.enableVibration(true)
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hablarPlingUnaVez() {
        if(vozLista) tts?.speak("pling", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun mostrarNotificacion(monto: Double, total: Double) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val aviso = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("✅ Confirmación de Pago")
            .setContentText("Monedero te envió S/ ${String.format("%.2f", monto)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Monedero te envió S/ ${String.format("%.2f", monto)}\nTotal acumulado: S/ ${String.format("%.2f", total)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001, aviso)
        }
    }

    private var totalGeneral by mutableStateOf(0.0)
    private var historial by mutableStateOf(listOf<Movimiento>())

    private fun ponerAlias(posicion: Int) {
        val campoAlias = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("PONER ALIAS")
            .setView(campoAlias)
            .setPositiveButton("GUARDAR") { _, _ ->
                val nombre = campoAlias.text.toString().trim()
                if(nombre.isNotEmpty()) {
                    historial = historial.toMutableList().also { it[posicion] = it[posicion].copy(alias = nombre) }
                }
            }
            .show()
    }

    private fun formatearMonto(monto: Double): String = when {
        monto == 0.10 -> "+0.10"
        monto == 0.20 -> "+0.20"
        monto == 0.50 -> "+0.50"
        monto == 1.00 -> "+1.00"
        monto == 2.00 -> "+2.00"
        monto == 5.00 -> "+5.00"
        monto < 1.00 -> String.format("+%.2f", monto)
        else -> String.format("+%.0f", monto)
    }

    @Composable
    fun PantallaPrincipal() {
        Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MONEDERO SMART", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFCDFF33))) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL", fontSize = 14.sp)
                        Text(String.format("%.2f SOLES", totalGeneral), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { pedirClave() }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error), shape = RoundedCornerShape(10.dp)) {
                    Text("VACIAR", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Column(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE0F7FF), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(historial.size) { posicion ->
                            val mov = historial[posicion]
                            val textoAlias = if(mov.alias.isNotEmpty()) mov.alias else "DESCONOCIDO"
                            val colorAlias = if(mov.alias.isNotEmpty()) Color(0xFF1976D2) else Color(0xFFFF9800)
                            val textoMonto = formatearMonto(mov.montoIngresado)

                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Card(modifier = Modifier.size(45.dp), shape = RoundedCornerShape(6.dp)) { Box(contentAlignment = Alignment.Center) { Text("📷", fontSize = 18.sp) } }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(textoAlias, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorAlias, modifier = Modifier.clickable { ponerAlias(posicion) })
                                            Text(mov.fechaHora, fontSize = 11.sp, color = Color.Gray)
                                            if(mov.codigo.isNotEmpty()) Text("Ticket creado", fontSize = 12.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if(mov.codigo.isNotEmpty()) Text("CÓDIGO: ${mov.codigo}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                        if(mov.montoIngresado > 0) Text(textoMonto, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pedirClave() {
        val campoClave = EditText(this)
        campoClave.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("CLAVE DE SEGURIDAD")
            .setView(campoClave)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                if(campoClave.text.toString() == CLAVE_VACIADO) {
                    totalGeneral = 0.0
                    guardarTotal(0.0)
                    historial = listOf(Movimiento(formatoFecha.format(Date()), "Monedero vaciado", 0.0, 0.0, "")) + historial
                    Toast.makeText(this@MainActivity, "✅ Vaciado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
