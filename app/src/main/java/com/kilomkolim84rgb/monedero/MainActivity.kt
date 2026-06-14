package com.kilomkolim84rgb.monedero

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.room.*
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Entity
data class Moneda(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monto: Int = 1,
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

@Database(entities = [Moneda::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monedaDao(): MonedaDao
}

data class EventoMoneda(
    val timestamp: Long = 0,
    val deviceId: String = ""
)

class MonederoViewModel(private val tts: TextToSpeech) : ViewModel() {
    private val _monedas = MutableStateFlow<List<Moneda>>(emptyList())
    val monedas: StateFlow<List<Moneda>> = _monedas
    
    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total
    
    private val _estadoCloud = MutableStateFlow("Iniciando...")
    val estadoCloud: StateFlow<String> = _estadoCloud
    
    private var db: AppDatabase? = null
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val ref = firebaseDb.getReference("monedero/eventos")
    private var ultimoTimestampProcesado = 0L
    
    init {
        tts.language = Locale("es", "PE")
        escucharFirebase()
    }
    
    fun initDb(database: AppDatabase) {
        db = database
        cargarDatos()
    }
    
    private fun cargarDatos() {
        CoroutineScope(Dispatchers.IO).launch {
            db?.monedaDao()?.getAll()?.let { lista ->
                _monedas.value = lista
                val nuevoTotal = lista.sumOf { it.monto }
                val totalAnterior = _total.value
                _total.value = nuevoTotal
                
                if (nuevoTotal > totalAnterior && lista.firstOrNull()?.origen?.contains("ESP32") == true) {
                    withContext(Dispatchers.Main) {
                        hablar("¡Un sol! Nuevo saldo: $nuevoTotal soles")
                    }
                }
            }
        }
    }
    
    private fun escucharFirebase() {
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _estadoCloud.value = "Conectado ☁️"
                for (eventoSnapshot in snapshot.children) {
                    val evento = eventoSnapshot.getValue(EventoMoneda::class.java)
                    if (evento != null && evento.timestamp > ultimoTimestampProcesado) {
                        ultimoTimestampProcesado = evento.timestamp
                        agregarMoneda(origen = "ESP32-${evento.deviceId}")
                        eventoSnapshot.ref.removeValue()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _estadoCloud.value = "Sin conexión"
            }
        })
    }
    
    private fun hablar(texto: String) {
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "MONEDA_ID")
    }
    
    fun agregarMoneda(origen: String = "Manual") {
        CoroutineScope(Dispatchers.IO).launch {
            db?.monedaDao()?.insert(Moneda(origen = origen))
            cargarDatos()
        }
    }
    
    fun retirarTodo() {
        CoroutineScope(Dispatchers.IO).launch {
            db?.monedaDao()?.deleteAll()
            cargarDatos()
        }
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var db: AppDatabase
    private lateinit var tts: TextToSpeech
    private var viewModel: MonederoViewModel? = null
    private val ttsReady = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "monedero-db"
        ).fallbackToDestructiveMigration().build()
        
        tts = TextToSpeech(this, this)
        
        setContent {
            MaterialTheme {
                if (ttsReady.value && viewModel != null) {
                    MonederoScreen(viewModel!!)
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.Cyan)
                            Spacer(Modifier.height(16.dp))
                            Text("Iniciando MonederoSmart...", color = Color.White)
                        }
                    }
                }
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            viewModel = MonederoViewModel(tts)
            viewModel?.initDb(db)
            ttsReady.value = true
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
    val total by viewModel.total.collectAsState()
    val estadoCloud by viewModel.estadoCloud.collectAsState()
    val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("MonederoSmart 💰", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = "Cloud", tint = Color(0xFF00E5FF))
                    Spacer(Modifier.width(4.dp))
                    Text(estadoCloud, color = Color.Gray, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Saldo Total", fontSize = 18.sp, color = Color.Gray)
            Text("S/ $total", fontSize = 48.sp, color = Color(0xFF00FF00), fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.agregarMoneda() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Agregar S/1 Prueba", fontSize = 16.sp)
                }
                Button(
                    onClick = { viewModel.retirarTodo() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Retirar Todo", fontSize = 16.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Divider(color = Color(0xFF333333))
            Text("Historial de Ingresos", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
            
            if (monedas.isEmpty()) {
                Text("Esperando monedas del ESP32...", color = Color.Gray, modifier = Modifier.padding(32.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(monedas) { moneda ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("S/ ${moneda.monto}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        moneda.origen, 
                                        color = if(moneda.origen.contains("ESP32")) Color(0xFF00E5FF) else Color(0xFFFFEB3B), 
                                        fontSize = 12.sp
                                    )
                                }
                                Text(formato.format(Date(moneda.fecha)), color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
