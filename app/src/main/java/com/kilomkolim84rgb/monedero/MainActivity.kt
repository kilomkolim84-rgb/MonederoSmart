package com.kilomkolim84rgb.monedero

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.room.*
import com.google.firebase.database.*
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
    val origen: String = "monedero Paoyhan"
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

data class MonedaRemota(val montoCentimos: Int = 0, val origen: String = "monedero Paoyhan", val timestamp: Long = 0)

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

    fun insertarMoneda(montoCentimos: Int, origen: String) {
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
    private lateinit var database: DatabaseReference
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        askNotificationPermission()
        
        viewModel = MonederoViewModel()
        viewModel.initDatabase(this)
        
        database = FirebaseDatabase.getInstance().getReference("monedas")
        escucharMonedasRemotas()
        
        setContent {
            MaterialTheme {
                MonederoScreen(viewModel)
            }
        }
    }
    
    private fun escucharMonedasRemotas() {
        database.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val moneda = snapshot.getValue(MonedaRemota::class.java)
                moneda?.let {
                    if (it.montoCentimos > 0) {
                        viewModel.insertarMoneda(it.montoCentimos, it.origen)
                        hablarMoneda(it.montoCentimos) // Solo audio: "un sol"
                        mostrarNotificacion(it.montoCentimos, it.origen) // Noti: "Ingreso un sol de monedero Paoyhan"
                        snapshot.ref.removeValue()
                    }
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
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
            val channel = NotificationChannel(CHANNEL_ID, "Monedero Paoyhan", NotificationManager.IMPORTANCE_HIGH)
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
    
    private fun mostrarNotificacion(montoCentimos: Int, origen: String) {
        val montoTexto = "S/ %.2f".format(montoCentimos / 100.0)
        val montoAudio = when(montoCentimos) {
            10 -> "diez céntimos"; 20 -> "veinte céntimos"; 50 -> "cincuenta céntimos"
            100 -> "un sol"; 200 -> "dos soles"; 500 -> "cinco soles"
            else -> "${montoCentimos / 100.0} soles"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ingreso $montoAudio")
            .setContentText("Desde $origen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
    
    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MonederoScreen(viewModel: MonederoViewModel) {
    val monedas by viewModel.monedas.collectAsState()
    val totalCentimos by viewModel.totalCentimos.collectAsState()
    val totalSoles = totalCentimos / 100.0
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var errorPassword by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    
    val CLAVE_CORRECTA = "123456" // CAMBIA TU CLAVE AQUÍ - 6 DÍGITOS

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("¿Vaciar monedero?") },
            text = { Text("Se borrarán todas las monedas. Esta acción requiere clave.") },
            confirmButton = {
                Button(onClick = { showConfirmDialog = false; showPasswordDialog = true }) { Text("Continuar") }
            },
            dismissButton = { Button(onClick = { showConfirmDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; password = ""; errorPassword = false },
            title = { Text("Ingresa clave de 6 dígitos") },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            if (it.length <= 6) password = it
                            errorPassword = false
                        },
                        label = { Text("Clave") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = errorPassword,
                        singleLine = true
                    )
                    if (errorPassword) Text("Clave incorrecta", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (password == CLAVE_CORRECTA) {
                        viewModel.borrarTodo()
                        showPasswordDialog = false
                        password = ""
                    } else {
                        errorPassword = true
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { Button(onClick = { showPasswordDialog = false; password = "" }) { Text("Cancelar") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("S/ %.2f".format(totalSoles), fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text("Monto Total", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Monedero Paoyhan", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = { showConfirmDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { 
            Text("Vaciar Monedero") 
        }
        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Text("Historial - ${monedas.size} monedas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(monedas) { moneda ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("S/ %.2f".format(moneda.montoCentimos / 100.0), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(dateFormat.format(Date(moneda.fecha)), fontSize = 12.sp)
                    }
                    Text("Origen: ${moneda.origen}", fontSize = 12.sp)
                }
                Divider()
            }
        }
    }
}
