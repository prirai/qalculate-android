package com.jherkenhoff.qalculate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "currency_rates")
data class CurrencyRateEntity(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val json: String // Raw JSON from API
)
