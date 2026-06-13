
package com.kilomkolim84rgb.monedero

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(64, 64, 64, 64)
        }
        
        val title = TextView(this).apply {
            text = "MonederoSmart 💰"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        
        val subtitle = TextView(this).apply {
            text = "\nBuild #32 funcionando\n\nYa quedó el APK bro.\nDespués le metemos la UI chida."
            textSize = 18f
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        setContentView(layout)
    }
}
