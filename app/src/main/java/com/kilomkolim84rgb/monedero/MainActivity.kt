package com.kilomkolim84rgb.monedero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "MONEDERO SMART",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BotonSimple(texto = "TEMPERATURA")
                BotonSimple(texto = "VOLTAJE")
                BotonSimple(texto = "ENERGÍA")
            }

            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL ACUMULADO", fontSize = 14.sp)
                    Text("0 SOLES", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Último ingreso: -", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun BotonSimple(texto: String) {
    Button(
        onClick = {},
        modifier = Modifier.size(80.dp, 50.dp),
        contentPadding = PaddingValues(2.dp)
    ) {
        Text(texto, fontSize = 10.sp)
    }
}
