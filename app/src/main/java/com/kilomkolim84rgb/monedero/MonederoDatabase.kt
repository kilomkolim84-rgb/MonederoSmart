
package com.kilomkolim84rgb.monedero

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Moneda::class], version = 1, exportSchema = false)
abstract class MonederoDatabase : RoomDatabase() {
    abstract fun monedaDao(): MonedaDao

    companion object {
        @Volatile
        private var INSTANCE: MonederoDatabase? = null

        fun getDatabase(context: Context): MonederoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MonederoDatabase::class.java,
                    "monedero_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
