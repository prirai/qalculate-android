package com.jherkenhoff.qalculate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates WHERE date = :date LIMIT 1")
    suspend fun getRatesForDate(date: String): CurrencyRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(entity: CurrencyRateEntity)
}
