/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves and restores settings as one JSON file in Download/Ashutool.
 * Only settings are included. Passwords, PINs, the vault code, the lock list and anything that records the
 * original state of the phone are left out on purpose, so a backup is safe to share.
 */
object Backup {
    private const val DIR = "/sdcard/Download/Ashutool"
    private const val FILE = "$DIR/settings-backup.json"

    private val KEEP = Regex("^(g_|n_|calls_|bs_|cl_|reel_|theme|anim_bg|glass_x|limits_system|lock_blur|bg_all_off|protect_self|use_a11y|swap_devs).*")
    private val SKIP = Regex(".*(prev|orig|state).*")

    private fun wanted(k: String) = KEEP.matches(k) && !SKIP.matches(k)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ashutool", Context.MODE_PRIVATE)

    /** Writes the backup. Returns how many settings were saved, or -1 if the file could not be written. */
    fun export(ctx: Context): Int {
        val out = JSONObject()
        for ((k, v) in prefs(ctx).all) {
            if (!wanted(k) || v == null) continue
            val e = JSONObject()
            when (v) {
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Set<*> -> { e.put("t", "x"); e.put("v", JSONArray(v.map { it.toString() })) }
                else -> continue
            }
            out.put(k, e)
        }
        val doc = JSONObject()
            .put("app", Credits.APP)
            .put("by", Credits.AUTHOR)
            .put("telegram", Credits.TELEGRAM)
            .put("settings", out)
        return try {
            val tmp = File(ctx.cacheDir, "settings-backup.json")
            tmp.writeText(doc.toString(2))
            if (Root.ok("mkdir -p $DIR", "cp ${Root.q(tmp.absolutePath)} $FILE")) out.length() else -1
        } catch (e: Exception) { -1 }
    }

    /** Reads the backup file. Returns how many settings were restored, or -1 if there is no usable file. */
    fun import(ctx: Context): Int {
        return try {
            val text = Root.run("cat $FILE").joinToString("\n")
            if (text.isBlank()) return -1
            val doc = JSONObject(text)
            if (doc.optString("app") != Credits.APP) return -1
            val s = doc.getJSONObject("settings")
            val ed = prefs(ctx).edit()
            var n = 0
            for (k in s.keys()) {
                if (!wanted(k)) continue
                val e = s.getJSONObject(k)
                val v = e.get("v")
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, v.toString())
                    "x" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> continue
                }
                n++
            }
            ed.apply()
            n
        } catch (e: Exception) { -1 }
    }

    const val PATH = "Download/Ashutool/settings-backup.json"
}
