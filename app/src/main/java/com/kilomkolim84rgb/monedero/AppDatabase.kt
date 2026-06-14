
package com.kilomkolim84rgb.monedero

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Moneda::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monedaDao(): MonedaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monedero_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
