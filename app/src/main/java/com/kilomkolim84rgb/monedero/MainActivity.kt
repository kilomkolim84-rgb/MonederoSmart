package com.kilomkolim84rgb.monedero

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

const val CLAVE_VACIAR = "1234"
const val ORIGEN_MONEDERO = "Ciber Cesarín - Monedero"

data class Registro(
    val id: String = "",
    val monto: Int = 0,
    val fecha: Long = System.currentTimeMillis(),
    val origen: String = ORIGEN_MONEDERO
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var db: FirebaseDatabase
    private lateinit var refTotal: DatabaseReference
    private lateinit var refHistorial: DatabaseReference
    private val CHANNEL_ID = "monedero_channel"
    private var ttsListo = false

    private var totalActual by mutableStateOf(0)
    private var listaHistorial by mutableStateOf(listOf<Registro>())

    private val monedaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val monto = intent?.getIntExtra("MONTO", 0) ?: 0
            if (monto > 0) {
                agregarDinero(monto)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ INICIA Y FUERZA LA CARGA DE LA VOZ
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        pedirPermisos()

        db = FirebaseDatabase.getInstance("https://ciber-cesarin-default-rtdb.firebaseio.com/")
        refTotal = db.getReference("total_general")
        refHistorial = db.getReference("historial")

        refTotal.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalActual = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // ✅ ESCUCHA EL HISTORIAL SIN FALLAS
        refHistorial.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = mutableListOf<Registro>()
                for (dato in snapshot.children) {
                    dato.getValue(Registro::class.java)?.let { temp.add(0, it) }
                }
                listaHistorial = temp
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        registerReceiver(monedaReceiver, IntentFilter("COM.MONEDERO.ADD_MONEDA"), RECEIVER_NOT_EXPORTED)

        setContent {
            MaterialTheme {
                PantallaMonedero()
            }
        }
    }

    private fun agregarDinero(monto: Int) {
        totalActual += monto
        refTotal.setValue(totalActual)
        val nuevo = Registro(monto = monto)
        // ✅ ASEGURA QUE SE GUARDE ANTES DE HABLAR
        refHistorial.push().setValue(nuevo).addOnCompleteListener {
            hablar(monto)
            aviso(monto)
        }
    }

    private fun vaciarConClave(claveIngresada: String): Boolean {
        return if(claveIngresada == CLAVE_VACIAR){
            totalActual = 0
            refTotal.setValue(0)
            refHistorial.removeValue()
            Toast.makeText(this, "✅ Monedero vaciado", Toast.LENGTH_SHORT).show()
            true
        } else {
            Toast.makeText(this, "❌ Clave incorrecta", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun hablar(monto: Int) {
        if (!ttsListo) return
        val texto = when(monto){
            1 -> "Un sol"
            2 -> "Dos soles"
            3 -> "Tres soles"
            4 -> "Cuatro soles"
            5 -> "Cinco soles"
            6 -> "Seis soles"
            7 -> "Siete soles"
            8 -> "Ocho soles"
            9 -> "Nueve soles"
            10 -> "Diez soles"
            11 -> "Once soles"
            12 -> "Doce soles"
            13 -> "Trece soles"
            14 -> "Catorce soles"
            15 -> "Quince soles"
            20 -> "Veinte soles"
            50 -> "Cincuenta soles"
            else -> "$monto soles"
        }
        // ✅ FUERZA LA REPRODUCCIÓN
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "hablar_$monto")
    }

    private fun aviso(monto: Int){
        val noti = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ INGRESO REGISTRADO")
            .setContentText("Entraron $monto soles | Total: $totalActual soles")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED){
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), noti.build())
        }
    }

    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS){
            val resultado = tts.setLanguage(Locale("es", "PE"))
            ttsListo = (resultado != TextToSpeech.LANG_MISSING_DATA && resultado != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Monedero", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun pedirPermisos(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
            registerForActivityResult(ActivityResultContracts.RequestPermission()){}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(monedaReceiver)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    @Composable
    fun PantallaMonedero(){
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        var pedirClave by remember { mutableStateOf(false) }
        var textoClave by remember { mutableStateOf("") }

        if(pedirClave){
            AlertDialog(
                onDismissRequest = { pedirClave = false },
                title = { Text("INGRESE CLAVE DE SEGURIDAD") },
                text = {
                    TextField(
                        value = textoClave,
                        onValueChange = { textoClave = it },
                        label = { Text("Escriba la clave") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if(vaciarConClave(textoClave)) pedirClave = false
                        textoClave = ""
                    }) { Text("CONFIRMAR") }
                },
                dismissButton = { Button({ pedirClave = false }) { Text("CANCELAR") } }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("S/ $totalActual", fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Text("Total acumulado", fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { pedirClave = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("VACIAR MONEDERO", fontSize = 18.sp) }

            Spacer(Modifier.height(16.dp))
            Divider()
            Text("HISTORIAL DE INGRESOS", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(listaHistorial) { reg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("S/ ${reg.monto}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Origen: ${reg.origen}", fontSize = 12.sp)
                        }
                        Text(formato.format(Date(reg.fecha)), fontSize = 12.sp)
                    }
                    Divider()
                }
            }
        }
    }
}
