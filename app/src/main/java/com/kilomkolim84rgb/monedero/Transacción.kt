package com.kilomkolim84rgb.monedero

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transacciones")
data class Transaccion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monto: String,
    val origen: String,
    val fecha: Long = System.currentTimeMillis()
)
