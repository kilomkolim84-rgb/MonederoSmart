package com.kilomkolim84rgb.monedero

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.ViewModel
import androidx.room.*
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@Database(entities = [Moneda::class], version = 1, exportSchema = false)
abstract class MonederoDatabase : RoomDatabase() {
    abstract fun monedaDao(): MonedaDao

    companion object {
        @Volatile
        private var INSTANCE: MonederoDatabase? = null

        fun getDatabase(context: android.content.Context): MonederoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MonederoDatabase::class.java,
                    "monedero_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MonederoViewModel : ViewModel() {
    private val _monedas = MutableStateFlow<List<Moneda>>(emptyList())
    val monedas: StateFlow<List<Moneda>> = _monedas

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total

    private var db: MonederoDatabase? = null
    
    fun initDatabase(context: android.content.Context) {
        db = MonederoDatabase.getDatabase(context)
    }

    fun insertarMoneda(monto: Int) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db?.monedaDao()?.insert(Moneda(monto = monto))
            cargarMonedas()
        }
    }
    
    private suspend fun cargarMonedas() {
        val lista = db?.monedaDao()?.getAll() ?: emptyList()
        _monedas.value = lista
        _total.value = lista.sumOf { it.monto }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = MonederoViewModel()
        viewModel.initDatabase(this)
        
        setContent {
            MaterialTheme {
                MonederoScreen(viewModel)
            }
        }
    }
}

@Composable
fun MonederoScreen(viewModel: MonederoViewModel) {
    val monedas by viewModel.monedas.collectAsState()
    val total by viewModel.total.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Total: S/ $total", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        
        Button(onClick = { viewModel.insertarMoneda(1) }) {
            Text("Agregar S/ 1")
        }
        
        LazyColumn {
            items(monedas) { moneda ->
                Text("S/ ${moneda.monto} - ${moneda.origen}")
            }
        }
    }
}
