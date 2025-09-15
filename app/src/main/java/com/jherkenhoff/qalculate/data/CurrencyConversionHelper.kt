package com.jherkenhoff.qalculate.data

import org.json.JSONObject

object CurrencyConversionHelper {
    /**
     * @param amount The amount to convert
     * @param from Source currency code (e.g. "usd")
     * @param to Target currency code (e.g. "inr")
     * @param ratesJson The JSON string from the API (usd.json)
     * @return The converted amount, or null if not possible
     */
    fun convert(amount: Double, from: String, to: String, ratesJson: String): Double? {
        val obj = JSONObject(ratesJson)
        val usd = obj.optJSONObject("usd") ?: return null
        val fromRate = usd.optDouble(from, Double.NaN)
        val toRate = usd.optDouble(to, Double.NaN)
        if (fromRate.isNaN() || toRate.isNaN()) return null
        // All rates are relative to USD
        return amount * toRate / fromRate
    }
}
