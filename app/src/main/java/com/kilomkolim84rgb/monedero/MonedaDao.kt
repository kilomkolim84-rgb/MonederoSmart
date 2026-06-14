
package com.kilomkolim84rgb.monedero

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonedaDao {
    @Insert
    suspend fun insertar(moneda: Moneda)

    @Query("SELECT * FROM Moneda ORDER BY timestamp DESC")
    fun obtenerTodas(): Flow<List<Moneda>>

    @Query("SELECT SUM(valor) FROM Moneda")
    fun obtenerTotal(): Flow<Double?>
}
