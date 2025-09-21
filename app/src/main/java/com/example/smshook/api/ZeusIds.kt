package com.example.smshook.api

import android.content.Context
import java.util.*

object ZeusIds {
    fun deviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("zeus", Context.MODE_PRIVATE)
        return prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }
    }
}







