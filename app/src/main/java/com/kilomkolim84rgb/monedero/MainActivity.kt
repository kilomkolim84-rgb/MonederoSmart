package com.kilomkolim84rgb.monedero

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.EditText
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

// ✅ TUS DATOS ORIGINALES SIN CAMBIOS
const val CLAVE_VACIADO = "222777"
const val CANAL_NOTIFICACIONES = "canal_monedero"

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

    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) tts?.language = Locale("es", "PE")
        }

        crearCanalNotificaciones()
        
        pedirPermisoNotificaciones()
        startForegroundService(Intent(this, 
        EscuchaFirebaseService::class.java))
        
        setContent { PantallaPrincipal() }
        escucharDatos()
        cargarHistorialGuardado()
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_NOTIFICACIONES,
                "Monedero Smart",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de ingresos y movimientos"
            }
            val gestor = getSystemService(NotificationManager::class.java)
            gestor.createNotificationChannel(canal)
        }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun mostrarNotificacion(monto: Int, total: Int) {
        val textoMonto = when(monto) {
            1 -> "un sol"
            2 -> "dos soles"
            3 -> "tres soles"
            4 -> "cuatro soles"
            5 -> "cinco soles"
            else -> "$monto soles"
        }

        // ✅ PARA QUE AL TOCAR LA NOTIFICACIÓN ABRA LA APP
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val aviso = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("✅ INGRESO REGISTRADO")
            .setContentText("Entró $textoMonto | Total: $total soles")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001, aviso)
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
            6 -> "seis soles"
            7 -> "siete soles"
            8 -> "ocho soles"
            9 -> "nueve soles"
            10 -> "diez soles"
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
        db.keepSynced(true)
        
        // ✅ CARGA EL TOTAL GUARDADO AL ABRIR LA APP PARA NO DUPLICAR NOTIFICACIONES
        db.child("total_general").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalAnterior = snapshot.getValue(Int::class.java) ?: 0
                totalGeneral = totalAnterior
            }
            override fun onCancelled(e: DatabaseError) {}
        })

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

        db.child("sensores/rayos_distancia").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(Double::class.java)
                distanciaRayos = if(v!=null) String.format("%.0f km", v) else "-- km"
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    @Composable
    fun PantallaPrincipal() {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text("MONEDERO SMART", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp), color = Color.Black)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BotonDato("TEMP", temperatura)
                    BotonDato("VOLT", voltaje)
                    BotonDato("RAYOS", distanciaRayos)
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFCDFF33))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL", fontSize = 14.sp, color = Color.Black)
                        Text("$totalGeneral SOLES", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Último: $ultimoMovimiento", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                    
                    Button(
                        onClick = { limpiarSoloHistorial() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("LIMPIAR", fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFE0F7FF), shape = RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(historial) { mov ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mov.fechaHora, fontSize = 11.sp, color = Color.Gray)
                                        if(mov.detalle == "Monedero vaciado"){
                                            Text("⚠️ Vaciado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        } else {
                                            Text("+${mov.montoIngresado} | Total: ${mov.totalAcumulado}", fontSize = 12.sp, color = Color.Black)
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Card(
                                            modifier = Modifier.size(45.dp, 45.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = CardDefaults.cardColors(Color(0xFFF5F5F5))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text("📷", fontSize = 18.sp) }
                                        }
                                        Column(modifier = Modifier.width(100.dp)) {
                                            Text("MAC: ${mov.mac}", fontSize = 10.sp, color = Color.Gray)
                                            Text("IP: ${mov.ip}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Card(
                                            modifier = Modifier.size(45.dp, 45.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = CardDefaults.cardColors(Color(0xFFF5F5F5))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text("QR", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.Black) }
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

    @Composable
    fun BotonDato(etiqueta: String, valor: String) {
        Card(
            modifier = Modifier.size(90.dp, 50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEB3B))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(etiqueta, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Text(valor, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }

    private fun pedirClave() {
        val campoClave = EditText(this)
        campoClave.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        campoClave.hint = "******"

        AlertDialog.Builder(this)
            .setTitle("CLAVE DE SEGURIDAD")
            .setMessage("Escribe tu clave de 6 dígitos:")
            .setView(campoClave)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                val clave = campoClave.text.toString()
                if(clave == CLAVE_VACIADO) vaciar()
                else Toast.makeText(this, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
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

    private fun limpiarSoloHistorial() {
        db.child("historial").removeValue()
        historial = emptyList()
        Toast.makeText(this, "✅ Historial limpiado", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
// ✅ SERVICIO QUE MANTIENE LA APP DESPIERTA AUNQUE LA CIERRES
class EscuchaFirebaseService : android.app.Service() {
    private val db = FirebaseDatabase.getInstance().reference
    private var totalAnterior = 0
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false

    override fun onCreate() {
        super.onCreate()
        db.keepSynced(true)
        
        val notificacion = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Monedero activo")
            .setContentText("Escuchando ingresos...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(1002, notificacion)

        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) tts?.language = Locale("es", "PE")
        }

        db.child("total_general").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalAnterior = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevoTotal = snapshot.getValue(Int::class.java) ?: 0
                if(nuevoTotal > totalAnterior){
                    val cuantoEntro = nuevoTotal - totalAnterior
                    val fecha = formatoFecha.format(Date())
                    val nuevoMov = Movimiento(fecha, "Ingreso", cuantoEntro, nuevoTotal)
                    db.child("historial").push().setValue(nuevoMov)
                    db.child("ultimo_movimiento").setValue("Ingreso: $cuantoEntro soles")
                    
                    if(vozLista){
                        val texto = when(cuantoEntro){
                            1 -> "un sol"
                            2 -> "dos soles"
                            3 -> "tres soles"
                            4 -> "cuatro soles"
                            5 -> "cinco soles"
                            else -> "$cuantoEntro soles"
                        }
                        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    
                    val textoMonto = when(cuantoEntro) {
                        1 -> "un sol"
                        2 -> "dos soles"
                        3 -> "tres soles"
                        4 -> "cuatro soles"
                        5 -> "cinco soles"
                        else -> "$cuantoEntro soles"
                    }
                    
                    val intent = Intent(this@EscuchaFirebaseService, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    val pendingIntent = PendingIntent.getActivity(
                        this@EscuchaFirebaseService,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val aviso = NotificationCompat.Builder(this@EscuchaFirebaseService, CANAL_NOTIFICACIONES)
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setContentTitle("✅ INGRESO REGISTRADO")
                        .setContentText("Entró $textoMonto | Total: $nuevoTotal soles")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build()
                    
                    NotificationManagerCompat.from(this@EscuchaFirebaseService).notify(1001, aviso)
                }
                totalAnterior = nuevoTotal
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}

