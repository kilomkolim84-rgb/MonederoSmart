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
import androidx.lifecycle.ViewModel
import androidx.room.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

@Entity
data class Moneda(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val montoCentimos: Int,
    val fecha: Long = System.currentTimeMillis(),
    val origen: String = "Manual"
)

@Dao
interface MonedaDao {
    @Query("SELECT * FROM Moneda ORDER BY fecha DESC")
    suspend fun getAll(): List<Moneda>
    @Insert
    suspend fun insert(moneda: Moneda)
    @Query("DELETE FROM Moneda")
    suspend fun deleteAll()
}

@Database(entities = [Moneda::class], version = 2, exportSchema = false)
abstract class MonederoDatabase : RoomDatabase() {
    abstract fun monedaDao(): MonedaDao
    companion object {
        @Volatile private var INSTANCE: MonederoDatabase? = null
        fun getDatabase(context: Context): MonederoDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, MonederoDatabase::class.java, "monedero_database")
                    .fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class MonederoViewModel : ViewModel() {
    private val _monedas = MutableStateFlow<List<Moneda>>(emptyList())
    val monedas: StateFlow<List<Moneda>> = _monedas
    private val _totalCentimos = MutableStateFlow(0)
    val totalCentimos: StateFlow<Int> = _totalCentimos
    private var db: MonederoDatabase? = null
    
    fun initDatabase(context: Context) {
        db = MonederoDatabase.getDatabase(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { cargarMonedas() }
    }

    fun insertarMoneda(montoCentimos: Int, origen: String = "Manual") {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db?.monedaDao()?.insert(Moneda(montoCentimos = montoCentimos, origen = origen))
            cargarMonedas()
        }
    }

    fun borrarTodo() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db?.monedaDao()?.deleteAll()
            cargarMonedas()
        }
    }
    
    private suspend fun cargarMonedas() {
        val lista = db?.monedaDao()?.getAll() ?: emptyList()
        _monedas.value = lista
        _totalCentimos.value = lista.sumOf { it.montoCentimos }
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var tts: TextToSpeech
    private val CHANNEL_ID = "monedero_channel"
    private lateinit var viewModel: MonederoViewModel
    
    private val monedaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val monto = intent?.getIntExtra("MONTO_CENTIMOS", 0) ?: 0
            if (monto > 0) {
                viewModel.insertarMoneda(monto, "ESP32")
                hablarMoneda(monto)
                mostrarNotificacion(monto)
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        askNotificationPermission()
        
        viewModel = MonederoViewModel()
        viewModel.initDatabase(this)
        
        registerReceiver(monedaReceiver, IntentFilter("COM.MONEDERO.ADD_MONEDA"), RECEIVER_NOT_EXPORTED)
        
        setContent {
            MaterialTheme {
                MonederoScreen(viewModel) { montoCentimos -> 
                    viewModel.insertarMoneda(montoCentimos, "Manual")
                    hablarMoneda(montoCentimos)
                    mostrarNotificacion(montoCentimos)
                }
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("es", "PE")
    }
    
    private fun hablarMoneda(montoCentimos: Int) {
        val texto = when(montoCentimos) {
            10 -> "diez céntimos"; 20 -> "veinte céntimos"; 50 -> "cincuenta céntimos"
            100 -> "un sol"; 200 -> "dos soles"; 500 -> "cinco soles"
            else -> "${montoCentimos / 100.0} soles"
        }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Monedero", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun mostrarNotificacion(montoCentimos: Int) {
        val montoTexto = "S/ %.2f".format(montoCentimos / 100.0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Se agregó una moneda")
            .setContentText("Ingresó $montoTexto a tu cuenta")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
    
    override fun onDestroy() {
        unregisterReceiver(monedaReceiver)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MonederoScreen(viewModel: MonederoViewModel, onMonedaAgregada: (Int) -> Unit) {
    val monedas by viewModel.monedas.collectAsState()
    val totalCentimos by viewModel.totalCentimos.collectAsState()
    val totalSoles = totalCentimos / 100.0
    var showDialog by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("¿Vaciar monedero?") },
            text = { Text("Se borrarán todas las monedas. No se puede deshacer.") },
            confirmButton = {
                Button(onClick = { viewModel.borrarTodo(); showDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Borrar") }
            },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("S/ %.2f".format(totalSoles), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text("Total en monedero", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Prueba Manual:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onMonedaAgregada(10) }) { Text("S/ 0.10") }
            Button(onClick = { onMonedaAgregada(20) }) { Text("S/ 0.20") }
            Button(onClick = { onMonedaAgregada(50) }) { Text("S/ 0.50") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onMonedaAgregada(100) }) { Text("S/ 1") }
            Button(onClick = { onMonedaAgregada(200) }) { Text("S/ 2") }
            Button(onClick = { onMonedaAgregada(500) }) { Text("S/ 5") }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { showDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Vaciar Monedero") }
        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Text("Historial - ${monedas.size} monedas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(monedas) { moneda ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("S/ %.2f".format(moneda.montoCentimos / 100.0), fontWeight = FontWeight.Bold)
                        Text("Origen: ${moneda.origen}", fontSize = 10.sp)
                    }
                    Text(dateFormat.format(Date(moneda.fecha)), fontSize = 12.sp)
                }
                Divider()
            }
        }
    }
}
