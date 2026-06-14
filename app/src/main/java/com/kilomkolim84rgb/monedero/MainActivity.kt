package com.kilomkolim84rgb.monedero

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        var showDialogBorrarTodo by remember { mutableStateOf(false) }
        var password by remember { mutableStateOf("") }
        var transaccionABorrar by remember { mutableStateOf<Transaccion?>(null) }

        val total = remember(transacciones) {
            transacciones.sumOf { 
                it.monto.replace("S/", "")
                    .replace(",", ".")
                    .replace(" ", "")
                    .toDoubleOrNull() ?: 0.0 
            }
        }

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
                    title = { Text("MonederoSmart", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    actions = {
                        // BOTÓN BORRAR TODO
                        IconButton(onClick = { showDialogBorrarTodo = true }) {
                            Icon(Icons.Default.DeleteForever, "Borrar todo", tint = Color.Red)
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE1D5F7),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Monedero Paoyhan",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Monto Total: S/ %.2f".format(total),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (transacciones.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Esperando Yapeos de Paoyhan...", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transacciones) { trans ->
                            Card(
                                Modifier.fillMaxWidth(),
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
                                        Icon(Icons.Default.Delete, "Borrar", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DIALOG BORRAR UNO
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
                    }) { 
                        Text("Borrar") 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDialog = false 
                        password = ""
                    }) { 
                        Text("Cancelar") 
                    }
                }
            )
        }

        // DIALOG BORRAR TODO
        if (showDialogBorrarTodo) {
            AlertDialog(
                onDismissRequest = { showDialogBorrarTodo = false },
                title = { Text("Borrar Todo") },
                text = {
                    Column {
                        Text("¿Seguro que quieres borrar todos los yapeos?")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Clave de seguridad") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (password == "123456") {
                            myRef.removeValue() // BORRA TODO FIREBASE
                            showDialogBorrarTodo = false
                            password = ""
                        }
                    }) { 
                        Text("Borrar Todo", color = Color.Red) 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDialogBorrarTodo = false 
                        password = ""
                    }) { 
                        Text("Cancelar") 
                    }
                }
            )
        }
    }
}
