package com.kilomkolim84rgb.monedero

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Transaccion::class], version = 1, exportSchema = false)
abstract class MonederoDatabase : RoomDatabase() {
    abstract fun transaccionDao(): TransaccionDao
}
