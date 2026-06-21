package com.kilomkolim84rgb.monedero

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import java.util.*

data class Moneda(val monto: Int = 0)

class MainActivity : ComponentActivity() {
    
    private lateinit var tts: TextToSpeech
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("es", "ES")
            }
        }
        
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("movimientos")
        
        setContent {
            MaterialTheme {
                var total by remember { mutableStateOf(0) }
                
                LaunchedEffect(Unit) {
                    ref.addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                            val moneda = snapshot.getValue(Moneda::class.java)
                            moneda?.let {
                                total += it.monto
                                hablarMonto(it.monto)
                                snapshot.ref.removeValue() // Borra para no repetir
                            }
                        }
                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
                
                ReceptorScreen(totalCentimos = total)
            }
        }
    }
    
    private fun hablarMonto(centimos: Int) {
        val texto = when (centimos) {
            10 -> "diez céntimos"
            20 -> "veinte céntimos"
            50 -> "cincuenta céntimos"
            100 -> "un sol"
            500 -> "cinco soles"
            else -> "${centimos / 100} soles con ${centimos % 100} céntimos"
        }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun ReceptorScreen(totalCentimos: Int) {
    val soles = totalCentimos / 100
    val centimos = totalCentimos % 100
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Monedero Receptor", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))
        Text("TOTAL", fontSize = 20.sp)
        Text(
            text = "S/ %d.%02d".format(soles, centimos),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
