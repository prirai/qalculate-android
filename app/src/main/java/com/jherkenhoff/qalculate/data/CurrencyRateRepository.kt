package com.jherkenhoff.qalculate.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.time.LocalDate

class CurrencyRateRepository(private val dao: CurrencyRateDao, private val context: Context) {
    companion object {
        private const val API_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
    }

    suspend fun getRatesForToday(): String? = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        val cached = dao.getRatesForDate(today)
        if (cached != null) return@withContext cached.json
        // Fetch from API
        val json = URL(API_URL).readText()
        dao.insertRates(CurrencyRateEntity(today, json))
        json
    }
}
