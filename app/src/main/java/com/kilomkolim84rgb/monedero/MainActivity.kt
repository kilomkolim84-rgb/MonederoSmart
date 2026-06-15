package com.kilomkolim84rgb.monedero

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var txtMontoTotal: TextView
    private lateinit var monederoRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this)
        layout.gravity = Gravity.CENTER
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)
        
        txtMontoTotal = TextView(this)
        txtMontoTotal.text = "Monto Total: S/ 0.00"
        txtMontoTotal.textSize = 28f
        txtMontoTotal.gravity = Gravity.CENTER
        
        layout.addView(txtMontoTotal)
        setContentView(layout)
        
        monederoRef = FirebaseDatabase.getInstance().getReference("monedero")
        
        monederoRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var total = 0.0
                for (hijo in snapshot.children) {
                    val valor = hijo.getValue(Int::class.java)
                    if (valor != null) total += valor
                }
                txtMontoTotal.text = "Monto Total: S/ %.2f".format(total)
            }

            override fun onCancelled(error: DatabaseError) {
                txtMontoTotal.text = "Error: ${error.message}"
            }
        })
    }
}
