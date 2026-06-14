package com.kilomkolim84rgb.monedero

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.firebase.database.FirebaseDatabase

class YapeListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pack = sbn.packageName
        if (pack == "com.bcp.innovacxion.yapeapp") {
            val extras = sbn.notification.extras
            val texto = extras.getString("android.text") ?: return
            
            // Solo si viene de Paoyhan y tiene monto
            if (texto.contains("Paoyhan", ignoreCase = true) && 
                (texto.contains("un sol") || texto.contains("soles") || texto.contains("S/"))) {
                
                val monto = extraerMonto(texto)
                val database = FirebaseDatabase.getInstance()
                val myRef = database.getReference("transacciones")
                
                val trans = Transaccion(monto = monto)
                myRef.push().setValue(trans)
            }
        }
    }
    
    private fun extraerMonto(texto: String): String {
        return when {
            texto.contains("un sol", ignoreCase = true) -> "un sol"
            else -> {
                val regex = Regex("""S/\s?(\d+(\.\d{1,2})?)""")
                val match = regex.find(texto)
                match?.groupValues?.get(1)?.let { "$it soles" } ?: "monto desconocido"
            }
        }
    }
}
