package com.resqmesh.data.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Persentase baterai perangkat ini.
 *
 * Nilainya ikut beacon dan SOS_LOC supaya tim rescue bisa memperkirakan berapa
 * lama node pengirim masih akan hidup. `null` berarti tidak diketahui, dan itu
 * dibedakan dari 0 supaya "baterai habis" tidak tertukar dengan "tidak ada
 * laporan baterai".
 */
object BatteryReader {

    fun read(context: Context): Int? {
        val manager =
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val intent: Intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return readFromManager(manager)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return readFromManager(manager)
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    private fun readFromManager(manager: BatteryManager): Int? {
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return percent.takeIf { it in 1..100 }
    }
}
