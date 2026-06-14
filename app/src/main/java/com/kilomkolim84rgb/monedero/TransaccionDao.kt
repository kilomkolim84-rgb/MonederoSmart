package com.kilomkolim84rgb.monedero

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransaccionDao {
    @Query("SELECT * FROM transacciones ORDER BY fecha DESC")
    fun getAll(): Flow<List<Transaccion>>

    @Insert
    suspend fun insert(transaccion: Transaccion)

    @Query("DELETE FROM transacciones")
    suspend fun deleteAll()
}
