
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
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
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
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        askNotificationPermission()
        
        viewModel = MonederoViewModel()
        viewModel.initDatabase(this)
        
        // FIREBASE -
