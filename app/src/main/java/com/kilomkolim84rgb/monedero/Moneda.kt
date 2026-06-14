
package com.kilomkolim84rgb.monedero

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Moneda(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val valor: Double,
    val timestamp: Long = System.currentTimeMillis()
)
