package com.kilomkolim84rgb.monedero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PantallaPrincipal() }
    }
}

@Composable
fun PantallaPrincipal() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // TÍTULO
            Text(
                text = "MONEDERO SMART",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // FILA DE 3 BOTONES PRINCIPALES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BotonSimple(texto = "TEMPERATURA")
                BotonSimple(texto = "VOLTAJE")
                BotonSimple(texto = "ENERGÍA")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BOTÓN DE VACIAR / LIMPIAR
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("VACIAR MONEDERO", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // TOTAL MUY GRANDE Y RESALTADO
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("TOTAL ACUMULADO", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "0 SOLES",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Último ingreso: -", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ESPACIO PARA HISTORIAL (LO METEMOS DESPUÉS CON FIREBASE)
            Text("Historial de movimientos", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun BotonSimple(texto: String) {
    Button(
        onClick = {},
        modifier = Modifier.size(90.dp, 45.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(texto, fontSize = 11.sp)
    }
}
