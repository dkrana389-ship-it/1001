package com.deepinnovations.reelcounter

import android.content.Context
import java.time.LocalDate

/** Saari counting sirf phone mein, din ke hisaab se save hoti hai. */
object Store {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("reelcounter", Context.MODE_PRIVATE)

    fun today(): String = LocalDate.now().toString()
    fun reels(c: Context, date: String = today()): Int = sp(c).getInt("reels_$date", 0)
    fun millis(c: Context, date: String = today()): Long = sp(c).getLong("ms_$date", 0L)

    fun addReel(c: Context): Int {
        val n = reels(c) + 1
        sp(c).edit().putInt("reels_${today()}", n).apply()
        return n
    }

    fun addMillis(c: Context, ms: Long) {
        if (ms <= 0) return
        sp(c).edit().putLong("ms_${today()}", millis(c) + ms).apply()
    }

    fun resetToday(c: Context) {
        sp(c).edit().remove("reels_${today()}").remove("ms_${today()}").apply()
    }

    fun username(c: Context): String = sp(c).getString("username", "") ?: ""
    fun setUsername(c: Context, v: String) = sp(c).edit().putString("username", v).apply()
    fun strict(c: Context): Boolean = sp(c).getBoolean("strict", true)
    fun setStrict(c: Context, v: Boolean) = sp(c).edit().putBoolean("strict", v).apply()

    fun setDiag(c: Context, v: String) =
        sp(c).edit().putString("diag", v).putLong("diag_at", System.currentTimeMillis()).apply()
    fun diag(c: Context): String = sp(c).getString("diag", "") ?: ""
    fun diagAt(c: Context): Long = sp(c).getLong("diag_at", 0L)
}
