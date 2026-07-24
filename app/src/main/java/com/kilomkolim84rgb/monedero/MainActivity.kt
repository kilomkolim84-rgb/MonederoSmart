package com.kilomkolim84rgb.monedero

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.ImageButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

const val CLAVE_VACIADO_A = "222777"  // ✅ 3 veces 2 + 3 veces 7
const val CLAVE_VACIADO_B = "333888"  // ✅ Clave propia del Monedero B
const val CANAL_NOTIFICACIONES = "canal_monedero"
const val CANAL_SERVICIO = "canal_servicio"
const val ID_NOTIFICACION_SERVICIO = 12345
const val DISTANCIA_PELIGRO = 8.0
const val DISTANCIA_SEGURIDAD = 9.0
const val TIEMPO_ESPERA_CONEXION = 2L

data class Movimiento(
    val monedero: String = "A",
    val fechaHora: String = "",
    val detalle: String = "",
    val montoIngresado: Double = 0.0,
    val totalAcumulado: Double = 0.0,
    val codigo: String = "",
    val alias: String = ""
)

class MonederoServicio : Service() {
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private lateinit var prefs: SharedPreferences
    private val TOTAL_A = "total_acumulado_a"
    private val TOTAL_B = "total_acumulado_b"
    private var escuchandoA: ValueEventListener? = null
    private var escuchandoB: ValueEventListener? = null
    private var sensoresEscucha: ValueEventListener? = null
    private var sistemaAEscucha: ValueEventListener? = null
    private var sistemaBEscucha: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("MonederoPrefs", Context.MODE_PRIVATE)
        
        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) {
                tts?.language = Locale("es", "PE")
                tts?.setPitch(1.5f)
                tts?.setSpeechRate(1.0f)
            }
        }

        crearCanalServicio()
        val notificacion = NotificationCompat.Builder(this, CANAL_SERVICIO)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("MONEDERO PAOYHAN")
            .setContentText("✅ Escuchando tickets...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(ID_NOTIFICACION_SERVICIO, notificacion)

        escucharHistorialA()
        escucharHistorialB()
        escucharSensores()
        escucharSistemaA()
        escucharSistemaB()
    }

    private fun crearCanalServicio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_SERVICIO, "Servicio Monedero Paoyhan", NotificationManager.IMPORTANCE_LOW)
            canal.description = "Escucha tickets en segundo plano"
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun escucharHistorialA() {
        val db = FirebaseDatabase.getInstance().reference
        db.keepSynced(true)

        escuchandoA = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (nivel1 in snapshot.children) {
                    for (nivel2 in nivel1.children) {
                        val codigo = nivel2.child("codigo").getValue(String::class.java) ?: ""
                        val leido = nivel2.child("leido_por_monedero").getValue(Boolean::class.java)
                        val monto = nivel2.child("monto").getValue(Double::class.java) ?: 0.0
                        val fecha = nivel2.child("fecha").getValue(String::class.java) ?: ""
                        
                        if (leido == true) continue
                        if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                        if (monto <= 0.0) continue

                        nivel2.ref.child("leido_por_monedero").setValue(true)

                        val totalActual = prefs.getFloat(TOTAL_A, 0f).toDouble()
                        val nuevoTotal = totalActual + monto
                        prefs.edit().putFloat(TOTAL_A, nuevoTotal.toFloat()).apply()

                        if(vozLista) tts?.speak("plin", TextToSpeech.QUEUE_FLUSH, null, null)
                        mostrarNotificacion("A", monto, nuevoTotal)

                        val leidoTicket = nivel2.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                        if (leidoTicket) nivel2.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("historial").addValueEventListener(escuchandoA!!)
    }

    private fun escucharHistorialB() {
        val db = FirebaseDatabase.getInstance().reference
        escuchandoB = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (nivel1 in snapshot.children) {
                    for (nivel2 in nivel1.children) {
                        val codigo = nivel2.child("codigo").getValue(String::class.java) ?: ""
                        val leido = nivel2.child("leido_por_monedero").getValue(Boolean::class.java)
                        val monto = nivel2.child("monto").getValue(Double::class.java) ?: 0.0
                        val fecha = nivel2.child("fecha").getValue(String::class.java) ?: ""
                        
                        if (leido == true) continue
                        if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                        if (monto <= 0.0) continue

                        nivel2.ref.child("leido_por_monedero").setValue(true)

                        val totalActual = prefs.getFloat(TOTAL_B, 0f).toDouble()
                        val nuevoTotal = totalActual + monto
                        prefs.edit().putFloat(TOTAL_B, nuevoTotal.toFloat()).apply()

                        if(vozLista) tts?.speak("plin", TextToSpeech.QUEUE_FLUSH, null, null)
                        mostrarNotificacion("B", monto, nuevoTotal)

                        val leidoTicket = nivel2.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                        if (leidoTicket) nivel2.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("monederoB/historial").addValueEventListener(escuchandoB!!)
    }

    private fun escucharSensores() {
        val db = FirebaseDatabase.getInstance().reference
        sensoresEscucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val voltaje = snapshot.child("voltaje").getValue(Double::class.java) ?: 0.0
                val temperatura = snapshot.child("temperatura").getValue(Double::class.java) ?: 0.0
                val rayosDistancia = snapshot.child("rayos_km").getValue(Double::class.java) ?: 999.0
                val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE")).format(Date())
                
                prefs.edit()
                    .putFloat("ultimo_voltaje", voltaje.toFloat())
                    .putFloat("ultima_temperatura", temperatura.toFloat())
                    .putFloat("rayos_distancia", rayosDistancia.toFloat())
                    .putString("ultima_lectura_sensores", fechaHora)
                    .apply()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("sensores").addValueEventListener(sensoresEscucha!!)
    }

    private fun escucharSistemaA() {
        val db = FirebaseDatabase.getInstance().reference
        sistemaAEscucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ultimaConexion = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                prefs.edit().putString("ultima_conexion_a", ultimaConexion).apply()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("sistema").addValueEventListener(sistemaAEscucha!!)
    }

    private fun escucharSistemaB() {
        val db = FirebaseDatabase.getInstance().reference
        sistemaBEscucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ultimaConexion = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                prefs.edit().putString("ultima_conexion_b", ultimaConexion).apply()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("monederoB/sistema").addValueEventListener(sistemaBEscucha!!)
    }

    private fun mostrarNotificacion(monedero: String, monto: Double, total: Double) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val aviso = NotificationCompat.Builder(this, CANAL_NOTIFICACIONES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("✅ MONEDERO $monedero — Pago Recibido")
            .setContentText("S/ ${String.format("%.2f", monto)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Monedero $monedero recibió S/ ${String.format("%.2f", monto)}\nTotal: S/ ${String.format("%.2f", total)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001 + monedero.hashCode(), aviso)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        escuchandoA?.let { FirebaseDatabase.getInstance().reference.child("historial").removeEventListener(it) }
        escuchandoB?.let { FirebaseDatabase.getInstance().reference.child("monederoB/historial").removeEventListener(it) }
        sensoresEscucha?.let { FirebaseDatabase.getInstance().reference.child("sensores").removeEventListener(it) }
        sistemaAEscucha?.let { FirebaseDatabase.getInstance().reference.child("sistema").removeEventListener(it) }
        sistemaBEscucha?.let { FirebaseDatabase.getInstance().reference.child("monederoB/sistema").removeEventListener(it) }
        tts?.stop()
        tts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private val formatoHoraFirebase = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private lateinit var prefs: SharedPreferences
    private val TOTAL_A = "total_acumulado_a"
    private val TOTAL_B = "total_acumulado_b"
    private val HISTORIAL_GUARDADO = "historial_guardado"
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var totalA by mutableStateOf(0.0)
    private var totalB by mutableStateOf(0.0)
    private var voltaje by mutableStateOf(0.0)
    private var temperatura by mutableStateOf(0.0)
    private var rayosDistancia by mutableStateOf(999.0)
    private var ultimaLecturaSensores by mutableStateOf("")
    private var sistemaAActivo by mutableStateOf(false)
    private var sistemaBActivo by mutableStateOf(false)
    private var ultimaConexionA by mutableStateOf("")
    private var ultimaConexionB by mutableStateOf("")
    private var historial by mutableStateOf(listOf<Movimiento>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("MonederoPrefs", Context.MODE_PRIVATE)
        totalA = prefs.getFloat(TOTAL_A, 0f).toDouble()
        totalB = prefs.getFloat(TOTAL_B, 0f).toDouble()
        cargarHistorialGuardado()
        cargarSensoresGuardados()
        
        try { FirebaseApp.initializeApp(this) } catch (e: Exception) { }
        
        val db = FirebaseDatabase.getInstance().reference
        db.keepSynced(true)
        
        startForegroundService(Intent(this, MonederoServicio::class.java))

        tts = TextToSpeech(this) { estado ->
            vozLista = estado == TextToSpeech.SUCCESS
            if(vozLista) {
                tts?.language = Locale("es", "PE")
                tts?.setPitch(1.5f)
                tts?.setSpeechRate(1.0f)
            }
        }

        crearCanalNotificaciones()
        pedirPermisoNotificaciones()
        
        setContent { PantallaPrincipal() }
        
        verificarTodoFirebase()
        
        db.child("historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (nivel1 in snapshot.children) {
                    for (nivel2 in nivel1.children) {
                        val codigo = nivel2.child("codigo").getValue(String::class.java) ?: ""
                        val leido = nivel2.child("leido_por_monedero").getValue(Boolean::class.java)
                        val monto = nivel2.child("monto").getValue(Double::class.java) ?: 0.0
                        val fecha = nivel2.child("fecha").getValue(String::class.java) ?: ""
                        
                        if (leido != true || codigo.length != 6 || monto <= 0.0) continue
                        if (historial.any { it.codigo == codigo }) continue
                        
                        historial = listOf(Movimiento("A", fecha, "Ticket creado", monto, totalA, codigo, "")) + historial
                        guardarHistorial()
                    }
                }
                totalA = prefs.getFloat(TOTAL_A, 0f).toDouble()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("monederoB/historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (nivel1 in snapshot.children) {
                    for (nivel2 in nivel1.children) {
                        val codigo = nivel2.child("codigo").getValue(String::class.java) ?: ""
                        val leido = nivel2.child("leido_por_monedero").getValue(Boolean::class.java)
                        val monto = nivel2.child("monto").getValue(Double::class.java) ?: 0.0
                        val fecha = nivel2.child("fecha").getValue(String::class.java) ?: ""
                        
                        if (leido != true || codigo.length != 6 || monto <= 0.0) continue
                        if (historial.any { it.codigo == codigo }) continue
                        
                        historial = listOf(Movimiento("B", fecha, "Ticket creado", monto, totalB, codigo, "")) + historial
                        guardarHistorial()
                    }
                }
                totalB = prefs.getFloat(TOTAL_B, 0f).toDouble()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                voltaje = snapshot.child("voltaje").getValue(Double::class.java) ?: 0.0
                temperatura = snapshot.child("temperatura").getValue(Double::class.java) ?: 0.0
                rayosDistancia = snapshot.child("rayos_km").getValue(Double::class.java) ?: 999.0
                ultimaLecturaSensores = SimpleDateFormat("dd/MM HH:mm", Locale("es", "PE")).format(Date())
                verificarTodoFirebase()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sistema").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimaConexionA = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                verificarEstadoSistemaA()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("monederoB/sistema").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimaConexionB = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                verificarEstadoSistemaB()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun verificarEstadoSistemaA() {
        if (ultimaConexionA.isEmpty()) {
            sistemaAActivo = false
            return
        }
        try {
            val fecha = formatoHoraFirebase.parse(ultimaConexionA)
            val difMin = TimeUnit.MILLISECONDS.toMinutes(Date().time - fecha.time)
            sistemaAActivo = difMin < TIEMPO_ESPERA_CONEXION
        } catch (e: Exception) { sistemaAActivo = true }
    }

    private fun verificarEstadoSistemaB() {
        if (ultimaConexionB.isEmpty()) {
            sistemaBActivo = false
            return
        }
        try {
            val fecha = formatoHoraFirebase.parse(ultimaConexionB)
            val difMin = TimeUnit.MILLISECONDS.toMinutes(Date().time - fecha.time)
            sistemaBActivo = difMin < TIEMPO_ESPERA_CONEXION
        } catch (e: Exception) { sistemaBActivo = true }
    }

    private fun verificarTodoFirebase() {
        val db = FirebaseDatabase.getInstance().reference
        
        db.child("sensores").get().addOnSuccessListener { snap ->
            voltaje = snap.child("voltaje").getValue(Double::class.java) ?: 0.0
            temperatura = snap.child("temperatura").getValue(Double::class.java) ?: 0.0
            rayosDistancia = snap.child("rayos_km").getValue(Double::class.java) ?: 999.0
            ultimaLecturaSensores = SimpleDateFormat("dd/MM HH:mm", Locale("es", "PE")).format(Date())
        }
        
        db.child("sistema").get().addOnSuccessListener { snap ->
            ultimaConexionA = snap.child("ultima_conexion").getValue(String::class.java) ?: ""
            verificarEstadoSistemaA()
        }
        
        db.child("monederoB/sistema").get().addOnSuccessListener { snap ->
            ultimaConexionB = snap.child("ultima_conexion").getValue(String::class.java) ?: ""
            verificarEstadoSistemaB()
        }
    }

    private fun forzarActualizacionManual() {
        verificarTodoFirebase()
        Toast.makeText(this, "✅ Actualizado", Toast.LENGTH_SHORT).show()
    }

    private fun cargarSensoresGuardados() {
        voltaje = prefs.getFloat("ultimo_voltaje", 0f).toDouble()
        temperatura = prefs.getFloat("ultima_temperatura", 0f).toDouble()
        rayosDistancia = prefs.getFloat("rayos_distancia", 999f).toDouble()
        ultimaLecturaSensores = prefs.getString("ultima_lectura_sensores", "") ?: ""
        ultimaConexionA = prefs.getString("ultima_conexion_a", "") ?: ""
        ultimaConexionB = prefs.getString("ultima_conexion_b", "") ?: ""
        verificarEstadoSistemaA()
        verificarEstadoSistemaB()
    }

    // 👁️ FUNCIÓN CON OJILLO PARA AMBOS MONEDEROS
    private fun pedirClaveVaciado(monedero: String, claveCorrecta: String, onConfirmar: () -> Unit) {
        val campoClave = EditText(this)
        campoClave.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        campoClave.filters = arrayOf(InputFilter.LengthFilter(6)) // ✅ 6 dígitos exactos

        val contenedor = LinearLayout(this)
        contenedor.orientation = LinearLayout.HORIZONTAL
        contenedor.setPadding(48, 16, 48, 16)
        contenedor.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        campoClave.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        // 👁️ BOTÓN OJILLO PARA VER/OCULTAR
        val botonOjo = ImageButton(this)
        botonOjo.setImageResource(android.R.drawable.ic_menu_view)
        botonOjo.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        botonOjo.setPadding(32, 0, 0, 0)

        var visible = false
        botonOjo.setOnClickListener {
            visible = !visible
            if (visible) {
                campoClave.inputType = InputType.TYPE_CLASS_NUMBER
                botonOjo.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                campoClave.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                botonOjo.setImageResource(android.R.drawable.ic_menu_view)
            }
        }

        contenedor.addView(campoClave)
        contenedor.addView(botonOjo)

        AlertDialog.Builder(this)
            .setTitle("VACIAR MONEDERO $monedero")
            .setMessage("Escribe los 6 dígitos para vaciar")
            .setView(contenedor)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                if (campoClave.text.toString() == claveCorrecta) {
                    onConfirmar()
                } else {
                    Toast.makeText(this, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun vaciarMonederoA() {
        pedirClaveVaciado("A", CLAVE_VACIADO_A) {
            totalA = 0.0
            prefs.edit().putFloat(TOTAL_A, 0f).apply()
            Toast.makeText(this, "✅ Monedero A vaciado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vaciarMonederoB() {
        pedirClaveVaciado("B", CLAVE_VACIADO_B) {
            totalB = 0.0
            prefs.edit().putFloat(TOTAL_B, 0f).apply()
            Toast.makeText(this, "✅ Monedero B vaciado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarHistorial() {
        val texto = historial.joinToString("|||") {
            "${it.monedero}§${it.fechaHora}§${it.detalle}§${it.montoIngresado}§${it.totalAcumulado}§${it.codigo}§${it.alias}"
        }
        prefs.edit().putString(HISTORIAL_GUARDADO, texto).apply()
    }

    private fun cargarHistorialGuardado() {
        val texto = prefs.getString(HISTORIAL_GUARDADO, "") ?: ""
        if (texto.isNotEmpty()) {
            historial = texto.split("|||").mapNotNull { linea ->
                val campos = linea.split("§")
                if (campos.size >= 6) Movimiento(
                    campos[0], campos[1], campos[2],
                    campos[3].toDoubleOrNull() ?: 0.0,
                    campos[4].toDoubleOrNull() ?: 0.0,
                    campos[5], if (campos.size >= 7) campos[6] else ""
                ) else null
            }
        }
    }

    private fun limpiarHistorial() {
        AlertDialog.Builder(this)
            .setTitle("LIMPIAR HISTORIAL")
            .setMessage("¿Borrar todo el historial?\n⚠️ No afecta los totales.")
            .setPositiveButton("SÍ") { _, _ ->
                historial = emptyList()
                prefs.edit().remove(HISTORIAL_GUARDADO).apply()
                Toast.makeText(this, "✅ Historial limpiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_NOTIFICACIONES, "Monedero Paoyhan", NotificationManager.IMPORTANCE_HIGH)
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

    private fun formatearMonto(monto: Double) = when {
        monto == 0.10 -> "+0.10"
        monto == 0.20 -> "+0.20"
        monto == 0.50 -> "+0.50"
        monto < 1.00 -> String.format("+%.2f", monto)
        else -> String.format("+%.0f", monto)
    }

    @Composable
    fun PantallaPrincipal() {
        LaunchedEffect(Unit) {
            while (true) {
                verificarEstadoSistemaA()
                verificarEstadoSistemaB()
                delay(30000)
            }
        }

        Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.White) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MONEDERO PAOYHAN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { forzarActualizacionManual() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color(0xFF1976D2))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Card(modifier = Modifier.weight(1f).height(70.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(Color(0xFFFFEB3B))) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("⚡ VOLTAJE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (voltaje > 0) String.format("%.1f V", voltaje) else "—", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(modifier = Modifier.weight(1f).height(70.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(Color(0xFFFFCC80))) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("🌡️ TEMPERATURA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (temperatura > -100) String.format("%.1f °C", temperatura) else "—", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val colorRayos = when {
                        rayosDistancia < DISTANCIA_PELIGRO -> Color(0xFFFF5252)
                        rayosDistancia <= 40.0 -> Color(0xFFFFC107)
                        else -> Color(0xFF4CAF50)
                    }
                    val textoRayos = when {
                        rayosDistancia < DISTANCIA_PELIGRO -> "${String.format("%.0f", rayosDistancia)} km ⚠️"
                        rayosDistancia <= 40.0 -> "${String.format("%.0f", rayosDistancia)} km"
                        else -> "-- km ✅"
                    }
                    Card(modifier = Modifier.weight(1f).height(70.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(colorRayos)) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("⚠️ RAYOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(textoRayos, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (ultimaLecturaSensores.isNotEmpty()) {
                    Text("Última lectura: $ultimaLecturaSensores", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFFFEB3B))) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MONEDERO A", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("TOTAL ACUMULADO", fontSize = 12.sp)
                                Text(String.format("%.2f SOLES", totalA), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { vaciarMonederoA() }, colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("VACIAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val colorSistemaA = if (sistemaAActivo) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        val textoSistemaA = if (sistemaAActivo) "SISTEMA ON" else "SISTEMA OFF"
                        Box(modifier = Modifier.fillMaxWidth().background(colorSistemaA, RoundedCornerShape(8.dp)).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(textoSistemaA, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFB3E5FC))) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MONEDERO B", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("TOTAL ACUMULADO", fontSize = 12.sp)
                                Text(String.format("%.2f SOLES", totalB), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { vaciarMonederoB() }, colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("VACIAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val colorSistemaB = if (sistemaBActivo) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        val textoSistemaB = if (sistemaBActivo) "SISTEMA ON" else "SISTEMA OFF"
                        Box(modifier = Modifier.fillMaxWidth().background(colorSistemaB, RoundedCornerShape(8.dp)).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(textoSistemaB, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Historial", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = { limpiarHistorial() }, colors = ButtonDefaults.buttonColors(Color(0xFF1976D2)), shape = RoundedCornerShape(8.dp)) {
                        Text("LIMPIAR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE0F7FF), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(historial.size) { posicion ->
                            val mov = historial[posicion]
                            val colorMonedero = if (mov.monedero == "A") Color(0xFFF57F17) else Color(0xFF0277BD)
                            val textoMonto = formatearMonto(mov.montoIngresado)

                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Card(modifier = Modifier.size(45.dp), shape = RoundedCornerShape(6.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(if (mov.monedero == "A") "🟡" else "🔵", fontSize = 20.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("MONEDERO ${mov.monedero}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorMonedero)
                                            Text(mov.fechaHora, fontSize = 11.sp, color = Color.Gray)
                                            Text("Ticket creado", fontSize = 12.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("CÓDIGO: ${mov.codigo}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                        Text(textoMonto, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
