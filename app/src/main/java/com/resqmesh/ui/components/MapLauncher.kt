package com.resqmesh.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.resqmesh.domain.model.SosIncident

/**
 * Membuka lokasi di aplikasi peta mana pun yang terpasang.
 *
 * Sengaja tanpa SDK peta: paket rescue tidak selalu punya Google Maps, dan
 * menarik pengguna ke Play Store saat sedang darurat adalah cara memastikan
 * tidak ada yang bisa menampilkan peta.
 */
object MapLauncher {

    /**
     * Skema `geo:` dipakai bersama label agar pin diberi nama yang bisa dibaca,
     * misalnya "SOS Medis · 1 orang".
     */
    fun intentFor(incident: SosIncident): Intent? {
        val point = incident.point ?: return null
        val label = buildString {
            append("SOS ").append(incident.kind.label)
            append(" · ").append(incident.victimLabel)
        }
        val uri = Uri.parse(
            "geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}($label)",
        )
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun hasHandler(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
}
