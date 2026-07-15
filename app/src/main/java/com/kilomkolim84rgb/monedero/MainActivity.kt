package com.kilomkolim84rgb.monedero

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle // ✅ AGREGADO: FALTABA ESTE IMPORT
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

// ✅ CONFIGURACIONES
const val CLAVE_VACIADO = "123456"
const val CANAL_NOTIFICACIONES = "canal_monedero"
const val CANAL_SERVICIO = "servicio_monedero"
const val ID_SERVICIO = 12345

data class Movimiento(
    val fechaHora: String = "",
    val detalle: String = "",
    val montoIngresado: Int = 0,
    val totalAcumulado: Int = 0,
    val mac: String = "--",
    val ip: String = "--"
)

// ✅ SERVICIO QUE SE QUEDA FUNCIONANDO SIEMPRE
class ServicioMonedero : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalServicio()
        val notificacion = NotificationCompat.Builder(this, CANAL_SERVICIO)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Monedero Smart activo")
            .setContentText("Esperando ingresos...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(ID_SERVICIO, notificacion)
        return START_STICKY
    }

    private fun crearCanalServicio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_SERVICIO,
                "Servicio Monedero",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val db = FirebaseDatabase.getInstance().reference
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private var totalAnterior = 0

    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) { // ✅ YA RECONOCE BUNDLE
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) tts?.language = Locale("es", "PE")
        }

        crearCanales()
        pedirPermisos()
        
        // ✅ INICIAMOS EL SERVICIO QUE SE QUEDA ACTIVO
        startForegroundService(Intent(this, ServicioMonedero::class.java))

        setContent { PantallaPrincipal() }
        escucharDatos()
        cargarHistorialGuardado()
    }

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canalAviso = NotificationChannel(
                CANAL_NOTIFICACIONES,
                "Ingresos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avisos de dinero recibido" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canalAviso)
        }
    }

    private fun pedirPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ✅ NOTIFICACIÓN QUE LLEGA SIEMPRE
    private fun mostrarNotificacion(monto: Int, total: Int) {
        val texto = when(monto) {
            1 -> "un sol"
            2 -> "dos soles"
            else -> "$monto soles"
        }

        val notif = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("✅ DINERO RECIBIDO")
            .setContentText("Entró $texto | Total: $total soles")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(2001, notif)
        }
    }

    private fun hablar(texto: String) {
        if(vozLista) tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun hablarMonto(monto: Int) {
        val texto = when(monto) {
            1 -> "un sol"
            2 -> "dos soles"
            3 -> "tres soles"
            4 -> "cuatro soles"
            5 -> "cinco soles"
            else -> "$monto soles"
        }
        hablar(texto)
    }

    private var totalGeneral by mutableStateOf(0)
    private var ultimoMovimiento by mutableStateOf("-")
    private var historial by mutableStateOf(listOf<Movimiento>())
    private var temperatura by mutableStateOf("-- °C")
    private var voltaje by mutableStateOf("-- V")
    private var distanciaRayos by mutableStateOf("-- km")

    private fun cargarHistorialGuardado() {
        db.child("historial").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Movimiento>()
                for(item in snapshot.children){
                    lista.add(Movimiento(
                        item.child("fechaHora").getValue(String::class.java) ?: "",
                        item.child("detalle").getValue(String::class.java) ?: "",
                        item.child("montoIngresado").getValue(Int::class.java) ?: 0,
                        item.child("totalAcumulado").getValue(Int::class.java) ?: 0,
                        item.child("mac").getValue(String::class.java) ?: "--",
                        item.child("ip").getValue(String::class.java) ?: "--"
                    ))
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
                    
                    hablarMonto(cuantoEntro)
                    mostrarNotificacion(cuantoEntro, nuevoTotal)
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

        db.child("sensores/temperatura").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                temperatura = if(s.getValue(Double::class.java) != null) String.format("%.1f °C", s.getValue(Double::class.java)) else "-- °C"
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores/voltaje").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                voltaje = if(s.getValue(Double::class.java) != null) String.format("%.1f V", s.getValue(Double::class.java)) else "-- V"
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores/rayos_distancia").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                distanciaRayos = if(s.getValue(Double::class.java) != null) String.format("%.0f km", s.getValue(Double::class.java)) else "-- km"
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    @Composable
    fun PantallaPrincipal() {
        Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, // ✅ CORREGIDO: Alignment correcto
                verticalArrangement = Arrangement.Top // ✅ CORREGIDO: Arrangement separado
            ) {
                Text("MONEDERO SMART", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly, // ✅ CORREGIDO
                    verticalAlignment = Alignment.CenterVertically // ✅ CORREGIDO
                ) {
                    BotonDato("TEMP", temperatura)
                    BotonDato("VOLT", voltaje)
                    BotonDato("RAYOS", distanciaRayos)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { pedirClave() }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) {
                    Text("VACIAR", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFCDFF33))) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally // ✅ CORREGIDO
                    ) {
                        Text("TOTAL", fontSize = 14.sp)
                        Text("$totalGeneral SOLES", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text("Último: $ultimoMovimiento", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Column(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE0F7FF), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    LazyColumn {
                        items(historial) { mov ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, // ✅ CORREGIDO
                                    verticalAlignment = Alignment.CenterVertically // ✅ CORREGIDO
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mov.fechaHora, fontSize = 11.sp, color = Color.Gray)
                                        if(mov.detalle == "Monedero vaciado") Text("⚠️ Vaciado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        else Text("+${mov.montoIngresado} | Total: ${mov.totalAcumulado}", fontSize = 12.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Card(modifier = Modifier.size(45.dp), shape = RoundedCornerShape(6.dp)) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("📷", fontSize = 18.sp)
                                            }
                                        }
                                        Column(modifier = Modifier.width(100.dp)) {
                                            Text("MAC: ${mov.mac}", fontSize = 10.sp, color = Color.Gray)
                                            Text("IP: ${mov.ip}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Card(modifier = Modifier.size(45.dp), shape = RoundedCornerShape(6.dp)) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    }

    private fun pedirClave() {
        val entrada = android.widget.EditText(this)
        entrada.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("CLAVE")
            .setMessage("Escribe tu clave de 6 dígitos:")
            .setView(entrada)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                if(entrada.text.toString() == CLAVE_VACIADO) vaciar()
                else Toast.makeText(this, "Clave incorrecta", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    @Composable
    fun BotonDato(etiqueta: String, valor: String) {
        Card(modifier = Modifier.size(90.dp, 50.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEB3B))) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally, // ✅ CORREGIDO
                verticalArrangement = Arrangement.Center // ✅ CORREGIDO
            ) {
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
        Toast.makeText(this, "Vaciado correctamente", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
