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
    private val db = FirebaseDatabase.getInstance().reference
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
    private var tts: TextToSpeech? = null
    private var vozLista = false
    private var ultimoCodigoRecibido = ""

    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        startForegroundService(Intent(this, EscuchaFirebaseService::class.java))
        
        setContent { PantallaPrincipal() }
        escucharDatos()
        escucharTicketsNuevos()
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

    private fun hablarPling() {
        if(vozLista) {
            tts?.speak("pling", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun mostrarNotificacion(monto: Double, total: Double) {
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
            .setContentText("Entró moneda | Total: ${String.format("%.2f", total)} soles")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(1001, aviso)
        }
    }

    private var totalGeneral by mutableStateOf(0.0)
    private var ultimoMovimiento by mutableStateOf("-")
    private var historial by mutableStateOf(listOf<Movimiento>())
    private var temperatura by mutableStateOf("-- °C")
    private var voltaje by mutableStateOf("-- V")
    private var distanciaRayos by mutableStateOf("-- km")
    private var totalAnterior = 0.0

    private fun escucharDatos() {
        db.keepSynced(true)
        
        db.child("total_general").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalAnterior = snapshot.getValue(Double::class.java) ?: 0.0
                totalGeneral = totalAnterior
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevoTotal = snapshot.getValue(Double::class.java) ?: 0.0
                if(nuevoTotal > totalAnterior){
                    val cuantoEntro = nuevoTotal - totalAnterior
                    val fecha = formatoFecha.format(Date())
                    val nuevoMov = Movimiento(fecha, "Ingreso", cuantoEntro, nuevoTotal, "", "")
                    historial = listOf(nuevoMov) + historial
                    db.child("ultimo_movimiento").setValue("Ingreso: ${String.format("%.2f", cuantoEntro)} soles")
                    
                    hablarPling()
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

    // ✅ ESCUCHA TICKETS — SOLO LEE, NO ESCRIBE NADA
    private fun escucharTicketsNuevos() {
        db.child("historial").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (hijo in snapshot.children) {
                    val codigo = hijo.child("codigo").getValue(String::class.java) ?: ""
                    val monto = hijo.child("monto").getValue(Double::class.java) ?: 0.0
                    val fecha = hijo.child("fecha").getValue(String::class.java) ?: ""

                    // 🔒 FILTRO 1: Solo código de 6 dígitos
                    if (codigo.length != 6 || !codigo.all { it.isDigit() }) continue

                    // 🔒 FILTRO 2: Solo monto mayor a 0
                    if (monto <= 0.0) continue

                    // 🔒 FILTRO 3: No repetir
                    if (codigo == ultimoCodigoRecibido) continue

                    // ✅ TICKET VÁLIDO — MOSTRAR SOLO EN LA APP
                    ultimoCodigoRecibido = codigo
                    val nuevoTicket = Movimiento(
                        fechaHora = fecha,
                        detalle = "Ticket generado",
                        montoIngresado = monto,
                        totalAcumulado = totalGeneral,
                        codigo = codigo,
                        alias = ""
                    )
                    historial = listOf(nuevoTicket) + historial
                    
                    // ✅ ELIMINAR EL TICKET DE FIREBASE DESPUÉS DE LEERLO
                    hijo.ref.removeValue()
                    
                    println("✅ TICKET LEÍDO Y ELIMINADO: $codigo — S/ $monto")
                }
            }
            override fun onCancelled(e: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error leyendo tickets", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun reiniciarSensores() {
        temperatura = "-- °C"
        voltaje = "-- V"
        distanciaRayos = "-- km"
        
        db.child("sensores/temperatura").removeValue()
        db.child("sensores/voltaje").removeValue()
        db.child("sensores/rayos_distancia").removeValue()
        
        Toast.makeText(this, "✅ Sensores reiniciados", Toast.LENGTH_SHORT).show()
    }

    private fun ponerAlias(posicion: Int, claveFirebase: String) {
        val campoAlias = EditText(this)
        campoAlias.hint = "Escribe el nombre o alias"
        
        AlertDialog.Builder(this)
            .setTitle("PONER ALIAS")
            .setMessage("Escribe el nombre de la persona:")
            .setView(campoAlias)
            .setPositiveButton("GUARDAR") { _, _ ->
                val nombre = campoAlias.text.toString().trim()
                if(nombre.isNotEmpty()) {
                    historial = historial.toMutableList().also {
                        it[posicion] = it[posicion].copy(alias = nombre)
                    }
                    Toast.makeText(this, "✅ Alias guardado: $nombre", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun formatearMonto(monto: Double): String {
        return when {
            monto == 0.10 -> "+0.10"
            monto == 0.20 -> "+0.20"
            monto == 0.50 -> "+0.50"
            monto == 1.00 -> "+1.00"
            monto == 2.00 -> "+2.00"
            monto == 5.00 -> "+5.00"
            monto < 1.00 -> String.format("+%.2f", monto)
            else -> String.format("+%.0f", monto)
        }
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

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { reiniciarSensores() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("REINICIAR SENSORES", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        Text(String.format("%.2f SOLES", totalGeneral), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Último: $ultimoMovimiento", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Historial", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFE0F7FF), shape = RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(historial.size) { posicion ->
                            val mov = historial[posicion]
                            val textoAlias = if(mov.alias.isNotEmpty()) mov.alias else "DESCONOCIDO"
                            val colorAlias = if(mov.alias.isNotEmpty()) Color(0xFF1976D2) else Color(0xFFFF9800)
                            val textoMonto = formatearMonto(mov.montoIngresado)

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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Card(
                                            modifier = Modifier.size(45.dp, 45.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = CardDefaults.cardColors(Color(0xFFF5F5F5))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text("📷", fontSize = 18.sp) }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                textoAlias,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorAlias,
                                                modifier = Modifier.clickable { ponerAlias(posicion, "") }
                                            )
                                            Text(mov.fechaHora, fontSize = 11.sp, color = Color.Gray)
                                            if(mov.detalle == "Monedero vaciado"){
                                                Text("⚠️ Vaciado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                            } else if(mov.codigo.isNotEmpty()) {
                                                Text("Ticket creado", fontSize = 12.sp, color = Color(0xFF4CAF50))
                                            }
                                        }
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Alignment.CenterVertically
                                    ) {
                                        if(mov.codigo.isNotEmpty()){
                                            Text("CÓDIGO: ${mov.codigo}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                        } else if (mov.montoIngresado > 0) {
                                            Text(
                                                textoMonto,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
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

    // ✅ VACIAR SIN GENERAR NADA EN FIREBASE
    private fun vaciar() {
        val fecha = formatoFecha.format(Date())
        val reg = Movimiento(fecha, "Monedero vaciado", 0.0, 0.0, "")
        historial = listOf(reg) + historial
        
        // ✅ SOLO REINICIA TOTALES — NO ESCRIBE NADA EN HISTORIAL DE FIREBASE
        db.child("total_general").setValue(0.0)
        db.child("ultimo_movimiento").setValue("Monedero vaciado")
        
        totalAnterior = 0.0
        totalGeneral = 0.0
        ultimoCodigoRecibido = "" // Reiniciar control de tickets
        
        hablarPling()
        Toast.makeText(this, "✅ Vaciado correctamente", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}

class EscuchaFirebaseService : android.app.Service() {
    private val db = FirebaseDatabase.getInstance().reference
    private var totalAnterior = 0.0
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
            if(vozLista) {
                tts?.language = Locale("es", "PE")
                tts?.setPitch(1.3f)
                tts?.setSpeechRate(0.85f)
            }
        }

        db.child("total_general").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalAnterior = snapshot.getValue(Double::class.java) ?: 0.0
            }
            override fun onCancelled(e: DatabaseError) {}
        })

        db.child("total_general").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nuevoTotal = snapshot.getValue(Double::class.java) ?: 0.0
                if(nuevoTotal > totalAnterior){
                    val cuantoEntro = nuevoTotal - totalAnterior
                    val fecha = formatoFecha.format(Date())
                    val nuevoMov = Movimiento(fecha, "Ingreso", cuantoEntro, nuevoTotal, "", "")
                    db.child("ultimo_movimiento").setValue("Ingreso: ${String.format("%.2f", cuantoEntro)} soles")
                    
                    if(vozLista){
                        tts?.speak("pling", TextToSpeech.QUEUE_FLUSH, null, null)
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
                        .setContentText("Entró moneda | Total: ${String.format("%.2f", nuevoTotal)} soles")
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
