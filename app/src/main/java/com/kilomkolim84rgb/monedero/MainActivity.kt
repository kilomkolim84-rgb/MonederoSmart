package com.kilomkolim84rgb.monedero

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.*

data class Transaccion(
    val id: String = "",
    val monto: String = "",
    val fecha: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    private val database = FirebaseDatabase.getInstance()
    private val myRef = database.getReference("transacciones")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pide permiso de notificaciones
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        
        setContent {
            MaterialTheme {
                MonederoScreen()
            }
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MonederoScreen() {
        var transacciones by remember { mutableStateOf(listOf<Transaccion>()) }
        var showDialog by remember { mutableStateOf(false) }
        var password by remember { mutableStateOf("") }
        var transaccionABorrar by remember { mutableStateOf<Transaccion?>(null) }

        // Escucha Firebase en tiempo real
        LaunchedEffect(Unit) {
            myRef.orderByChild("fecha").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lista = mutableListOf<Transaccion>()
                    for (child in snapshot.children) {
                        val trans = child.getValue(Transaccion::class.java)
                        trans?.let { lista.add(it.copy(id = child.key ?: "")) }
                    }
                    transacciones = lista.reversed()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Monedero Paoyhan", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (transacciones.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Esperando Yapeos de Paoyhan...")
                    }
                } else {
                    LazyColumn(Modifier.padding(16.dp)) {
                        items(transacciones) { trans ->
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                    Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Ingreso ${trans.monto} de monedero Paoyhan",
                                        Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        transaccionABorrar = trans
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Default.Delete, "Borrar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog de clave para borrar
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Clave de seguridad") },
                text = {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Ingresa la clave") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (password == "123456") {
                            transaccionABorrar?.id?.let { myRef.child(it).removeValue() }
                            showDialog = false
                            password = ""
                        }
                    }) { Text("Borrar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}
