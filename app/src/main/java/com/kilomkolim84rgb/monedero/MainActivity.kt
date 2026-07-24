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
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

const val CLAVE_VACIADO = "222777"
const val CANAL_NOTIFICACIONES = "canal_monedero"
const val CANAL_SERVICIO = "canal_servicio"
const val ID_NOTIFICACION_SERVICIO = 12345
const val DISTANCIA_PELIGRO = 8.0
const val DISTANCIA_SEGURIDAD = 9.0
const val TIEMPO_ESPERA_CONEXION = 5L // ⏳ 5 minutos sin respuesta = OFF

data class Movimiento(
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
    private val TOTAL_GUARDADO = "total_acumulado"
    private var escuchando: ValueEventListener? = null
    private var sensoresEscucha: ValueEventListener? = null
    private var estadoSistemaEscucha: ValueEventListener? = null

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

        escucharFirebase()
        escucharSensores()
        escucharEstadoSistema()
    }

    private fun crearCanalServicio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_SERVICIO, "Servicio Monedero Paoyhan", NotificationManager.IMPORTANCE_LOW)
            canal.description = "Escucha tickets en segundo plano"
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun escucharFirebase() {
        val db = FirebaseDatabase.getInstance().reference
        db.keepSynced(true)

        escuchando = object : ValueEventListener {
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

                        val totalActual = prefs.getFloat(TOTAL_GUARDADO, 0f).toDouble()
                        val nuevoTotal = totalActual + monto
                        prefs.edit().putFloat(TOTAL_GUARDADO, nuevoTotal.toFloat()).apply()

                        if(vozLista) tts?.speak("plin", TextToSpeech.QUEUE_FLUSH, null, null)

                        mostrarNotificacion(monto, nuevoTotal)

                        val leidoTicket = nivel2.child("leido_por_ticket").getValue(Boolean::class.java) ?: false
                        if (leidoTicket) nivel2.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }

        db.child("historial").addValueEventListener(escuchando!!)
    }

    private fun escucharSensores() {
        val db = FirebaseDatabase.getInstance().reference
        sensoresEscucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val voltaje = snapshot.child("voltaje").getValue(Double::class.java) ?: 0.0
                val temperatura = snapshot.child("temperatura").getValue(Double::class.java) ?: 0.0
                val rayosDistancia = snapshot.child("rayos_km").getValue(Double::class.java) ?: 999.0
                val sistemaEncendido = snapshot.child("sistema_encendido").getValue(Boolean::class.java) ?: true
                val cerrojoAbierto = snapshot.child("cerrojo_abierto").getValue(Boolean::class.java) ?: false
                val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE")).format(Date())
                
                prefs.edit()
                    .putFloat("ultimo_voltaje", voltaje.toFloat())
                    .putFloat("ultima_temperatura", temperatura.toFloat())
                    .putFloat("rayos_distancia", rayosDistancia.toFloat())
                    .putBoolean("sistema_encendido", sistemaEncendido)
                    .putBoolean("cerrojo_abierto", cerrojoAbierto)
                    .putString("ultima_lectura_sensores", fechaHora)
                    .apply()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("sensores").addValueEventListener(sensoresEscucha!!)
    }

    private fun escucharEstadoSistema() {
        val db = FirebaseDatabase.getInstance().reference
        estadoSistemaEscucha = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ultimaConexionStr = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                val estado = snapshot.child("estado").getValue(String::class.java) ?: ""
                
                prefs.edit()
                    .putString("ultima_conexion_esp32", ultimaConexionStr)
                    .putString("estado_sistema", estado)
                    .apply()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("sistema").addValueEventListener(estadoSistemaEscucha!!)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        escuchando?.let {
            FirebaseDatabase.getInstance().reference.child("historial").removeEventListener(it)
        }
        sensoresEscucha?.let {
            FirebaseDatabase.getInstance().reference.child("sensores").removeEventListener(it)
        }
        estadoSistemaEscucha?.let {
            FirebaseDatabase.getInstance().reference.child("sistema").removeEventListener(it)
        }
        tts?.stop()
        tts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private val formatoHoraFirebase = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private lateinit var prefs: SharedPreferences
    private val TOTAL_GUARDADO = "total_acumulado"
    private val HISTORIAL_GUARDADO = "historial_guardado"
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var totalGeneral by mutableStateOf(0.0)
    private var historial by mutableStateOf(listOf<Movimiento>())
    private var voltaje by mutableStateOf(0.0)
    private var temperatura by mutableStateOf(0.0)
    private var rayosDistancia by mutableStateOf(999.0)
    private var sistemaEncendido by mutableStateOf(true)
    private var cerrojoAbierto by mutableStateOf(false)
    private var ultimaLecturaSensores by mutableStateOf("")
    private var sistemaActivo by mutableStateOf(false)
    private var ultimaConexionEsp32 by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("MonederoPrefs", Context.MODE_PRIVATE)
        totalGeneral = leerTotalGuardado()
        cargarHistorialGuardado()
        cargarSensoresGuardados()
        cargarEstadoSistema()
        
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) { }
        
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
        
        db.child("historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (nivel1 in snapshot.children) {
                    for (nivel2 in nivel1.children) {
                        val codigo = nivel2.child("codigo").getValue(String::class.java) ?: ""
                        val leido = nivel2.child("leido_por_monedero").getValue(Boolean::class.java)
                        val monto = nivel2.child("monto").getValue(Double::class.java) ?: 0.0
                        val fecha = nivel2.child("fecha").getValue(String::class.java) ?: ""
                        
                        if (leido != true) continue
                        if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue
                        if (monto <= 0.0) continue
                        
                        val existeYa = historial.any { it.codigo == codigo }
                        if (existeYa) continue
                        
                        val totalActual = leerTotalGuardado()
                        val nuevoTicket = Movimiento(fecha, "Ticket generado", monto, totalActual, codigo, "")
                        historial = listOf(nuevoTicket) + historial
                        totalGeneral = totalActual
                        guardarHistorial()
                    }
                }
                totalGeneral = leerTotalGuardado()
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sensores").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                voltaje = snapshot.child("voltaje").getValue(Double::class.java) ?: 0.0
                temperatura = snapshot.child("temperatura").getValue(Double::class.java) ?: 0.0
                rayosDistancia = snapshot.child("rayos_km").getValue(Double::class.java) ?: 999.0
                sistemaEncendido = snapshot.child("sistema_encendido").getValue(Boolean::class.java) ?: true
                cerrojoAbierto = snapshot.child("cerrojo_abierto").getValue(Boolean::class.java) ?: false
                ultimaLecturaSensores = SimpleDateFormat("dd/MM HH:mm", Locale("es", "PE")).format(Date())
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("sistema").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ultimaConexionEsp32 = snapshot.child("ultima_conexion").getValue(String::class.java) ?: ""
                verificarEstadoSistema()
            }
            override fun onCancelled(e: DatabaseError) {}
        })
    }

    private fun cargarEstadoSistema() {
        ultimaConexionEsp32 = prefs.getString("ultima_conexion_esp32", "") ?: ""
        verificarEstadoSistema()
    }

    private fun verificarEstadoSistema() {
        if (ultimaConexionEsp32.isEmpty()) {
            sistemaActivo = false
            return
        }
        
        try {
            val fechaUltima = formatoHoraFirebase.parse(ultimaConexionEsp32)
            val ahora = Date()
            val diferenciaMs = ahora.time - fechaUltima.time
            val diferenciaMinutos = TimeUnit.MILLISECONDS.toMinutes(diferenciaMs)
            
            sistemaActivo = diferenciaMinutos < TIEMPO_ESPERA_CONEXION
        } catch (e: Exception) {
            sistemaActivo = false
        }
    }

    private fun cargarSensoresGuardados() {
        voltaje = prefs.getFloat("ultimo_voltaje", 0f).toDouble()
        temperatura = prefs.getFloat("ultima_temperatura", 0f).toDouble()
        rayosDistancia = prefs.getFloat("rayos_distancia", 999f).toDouble()
        sistemaEncendido = prefs.getBoolean("sistema_encendido", true)
        cerrojoAbierto = prefs.getBoolean("cerrojo_abierto", false)
        ultimaLecturaSensores = prefs.getString("ultima_lectura_sensores", "") ?: ""
    }

    private fun leerTotalGuardado(): Double = prefs.getFloat(TOTAL_GUARDADO, 0f).toDouble()
    private fun guardarTotal(total: Double) = prefs.edit().putFloat(TOTAL_GUARDADO, total.toFloat()).apply()

    private fun guardarHistorial() {
        val historialTexto = historial.joinToString("|||") { mov ->
            "${mov.fechaHora}§${mov.detalle}§${mov.montoIngresado}§${mov.totalAcumulado}§${mov.codigo}§${mov.alias}"
        }
        prefs.edit().putString(HISTORIAL_GUARDADO, historialTexto).apply()
    }

    private fun cargarHistorialGuardado() {
        val texto = prefs.getString(HISTORIAL_GUARDADO, "") ?: ""
        if (texto.isNotEmpty()) {
            historial = texto.split("|||").mapNotNull { linea ->
                val campos = linea.split("§")
                if (campos.size >= 5) {
                    Movimiento(
                        campos[0], campos[1],
                        campos[2].toDoubleOrNull() ?: 0.0,
                        campos[3].toDoubleOrNull() ?: 0.0,
                        campos[4], if (campos.size >= 6) campos[5] else ""
                    )
                } else null
            }
        }
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL_NOTIFICACIONES, "Monedero Paoyhan", NotificationManager.IMPORTANCE_HIGH)
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

    private fun alternarCerrojo() {
        val db = FirebaseDatabase.getInstance().reference
        val nuevoEstado = !cerrojoAbierto
        db.child("sensores/cerrojo_abierto").setValue(nuevoEstado)
        cerrojoAbierto = nuevoEstado
        Toast.makeText(this, if(nuevoEstado) "🔓 CERROJO ABIERTO" else "🔒 CERROJO CERRADO", Toast.LENGTH_SHORT).show()
    }

    private fun ponerAlias(posicion: Int) {
        val campoAlias = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("PONER ALIAS")
            .setView(campoAlias)
            .setPositiveButton("GUARDAR") { _, _ ->
                val nombre = campoAlias.text.toString().trim()
                if(nombre.isNotEmpty()) {
                    historial = historial.toMutableList().also { it[posicion] = it[posicion].copy(alias = nombre) }
                    guardarHistorial()
                }
            }
            .show()
    }

    private fun pedirClaveVaciado() {
        val campoClave = EditText(this)
        campoClave.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        campoClave.filters = arrayOf(InputFilter.LengthFilter(6))

        val contenedor = LinearLayout(this)
        contenedor.orientation = LinearLayout.HORIZONTAL
        contenedor.setPadding(48, 16, 48, 16)
        contenedor.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        campoClave.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
            .setTitle("CLAVE DE SEGURIDAD")
            .setMessage("Escribe los 6 dígitos para vaciar el monedero")
            .setView(contenedor)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                if(campoClave.text.toString() == CLAVE_VACIADO) {
                    totalGeneral = 0.0
                    guardarTotal(0.0)
                    Toast.makeText(this@MainActivity, "✅ Monedero vaciado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun limpiarHistorial() {
        AlertDialog.Builder(this)
            .setTitle("LIMPIAR HISTORIAL")
            .setMessage("¿Borrar todo el historial de la aplicación?\n\n⚠️ No afecta el total ni Firebase.")
            .setPositiveButton("SÍ, LIMPIAR") { _, _ ->
                historial = emptyList()
                prefs.edit().remove(HISTORIAL_GUARDADO).apply()
                Toast.makeText(this@MainActivity, "✅ Historial limpiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCELAR", null)
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
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("MONEDERO PAOYHAN", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    
                    val colorSistema = if (sistemaActivo) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    val textoSistema = if (sistemaActivo) "SISTEMA ON" else "SISTEMA OFF"
                    Box(modifier = Modifier
                        .background(colorSistema, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(textoSistema, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Card(modifier = Modifier.weight(1f).height(70.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(Color(0xFFFFEB3B))) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("⚡ VOLTAJE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (sistemaActivo && voltaje > 0) String.format("%.1f V", voltaje) 
                                else "—", 
                                fontSize = 18.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(modifier = Modifier.weight(1f).height(70.dp), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(Color(0xFFFFCC80))) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("🌡️ TEMPERATURA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (sistemaActivo && temperatura > -100) String.format("%.1f °C", temperatura) 
                                else "—", 
                                fontSize = 18.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val colorRayos = when {
                        rayosDistancia < DISTANCIA_PELIGRO && !sistemaEncendido -> Color(0xFFFF5252)
                        rayosDistancia < DISTANCIA_PELIGRO -> Color(0xFFFF5252)
                        rayosDistancia <= 40.0 -> Color(0xFFFFC107)
                        else -> Color(0xFF4CAF50)
                    }
                    val textoRayos = when {
                        !sistemaActivo -> "-- km ✅"
                        rayosDistancia < DISTANCIA_PELIGRO && !sistemaEncendido -> "${String.format("%.0f", rayosDistancia)} km 🔴 APAGADO"
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

                if (sistemaActivo && ultimaLecturaSensores.isNotEmpty()) {
                    Text("Última lectura: $ultimaLecturaSensores", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                } else if (!sistemaActivo) {
                    Text("Última lectura: Sin conexión", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(Color(0xFFCDFF33))) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL ACUMULADO", fontSize = 14.sp)
                        Text(String.format("%.2f SOLES", totalGeneral), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { pedirClaveVaciado() }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Text("VACIAR", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val colorBotonCerrojo = if (cerrojoAbierto) Color(0xFFFF5252) else Color(0xFF4CAF50)
                    val textoCerrojo = if (cerrojoAbierto) "🔓 ABIERTO" else "🔒 CERRADO"
                    Button(onClick = { alternarCerrojo() }, colors = ButtonDefaults.buttonColors(colorBotonCerrojo), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1.2f)) {
                        Text(textoCerrojo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { limpiarHistorial() }, colors = ButtonDefaults.buttonColors(Color(0xFF1976D2)), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Text("LIMPIAR", fontSize = 12.sp)
                    }
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

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
