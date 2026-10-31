/*
 * Ashutool - root toolkit for custom ROMs
 * Copyright (C) 2026 Ashutosh Nishad
 * Telegram: @ashutosh_07x (https://t.me/ashutosh_07x)
 *
 * Free software under the GNU GPL v3.0 or later, with the attribution terms in NOTICE.
 * Keep this header and the author credit in every copy and modified version.
 */

package com.ashu.ashutool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Battery pages that show what the numbers do over time: the ongoing session, the electric current and the temperature alarm.

/** 53s, 4m 10s, 1h 5m. Short spans keep their seconds, which the minutes-only helper would round away. */
private fun fmtSpan(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    return when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
        s >= 60 -> "${s / 60}m ${s % 60}s"
        else -> "${s}s"
    }
}

private fun mahText(v: Float): String = when {
    v < 0f -> "n/a"
    v < 10f -> "${"%.1f".format(v)} mAh"
    else -> "${v.toInt()} mAh"
}

/** Average current in mA for a milliamp hour total over a span. Null when the span is too short to mean anything. */
private fun maOf(mah: Float, ms: Long): Int? = if (mah < 0f || ms < 60_000L) null else (mah / (ms / 3_600_000f)).toInt()

/** Time left for [remainMah] at [ma]. Measuring until there is enough to go on. */
private fun leftText(remainMah: Float?, ma: Int?): String {
    if (remainMah == null || ma == null) return "Measuring"
    if (ma < 1) return "Measuring"
    val h = remainMah / ma
    return if (h > 99f) ">99h" else fmtSpan((h * 3_600_000f).toLong())
}

@Composable
private fun SplitBar(on: Long, awake: Long, deep: Long) {
    val total = (on + awake + deep).coerceAtLeast(1L).toFloat()
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Tint.copy(alpha = 0.10f))) {
        if (on > 0L) Box(Modifier.weight(on / total).fillMaxHeight().background(Accent))
        if (awake > 0L) Box(Modifier.weight(awake / total).fillMaxHeight().background(Warn))
        if (deep > 0L) Box(Modifier.weight(deep / total).fillMaxHeight().background(Accent2))
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The ongoing session: used, rate, screen on, screen off, deep sleep, held awake and the time left. */
@Composable
internal fun SessionSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    // Read again with every battery reading, so the page counts up while it is open
    val v = Session.view(ctx)
    Page(
        "Ongoing", if (b.charging) "Since it was plugged in" else "Since it was unplugged",
        GGreen, if (b.charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd, onBack = back
    ) {
        if (v == null) {
            item { Glass { Hint("Measuring. The first numbers show up after the next battery reading.") } }
            return@Page
        }
        val used = if (v.charging) v.level - v.startLevel else v.startLevel - v.level
        val hours = v.totalMs / 3_600_000f
        val rate = if (hours >= 0.02f) used / hours else null
        val avgMa = maOf(v.mah, v.totalMs)
        item {
            TileGrid(
                listOf(
                    Tile(Icons.Outlined.Timer, "Total time", fmtSpan(v.totalMs), GTeal),
                    Tile(Icons.Outlined.BatteryStd, if (v.charging) "Gained" else "Used", "${used.coerceAtLeast(0)}%  \u00B7  ${mahText(v.mah)}", GGreen),
                    Tile(Icons.Outlined.Speed, if (v.charging) "Charging rate" else "Discharging rate", rate?.let { "${"%.1f".format(it)}%/h" } ?: "Measuring", GFire),
                    Tile(Icons.Outlined.Bolt, "Average current", avgMa?.let { "~$it mA" } ?: "Measuring", GBlue)
                )
            )
        }
        item {
            Glass {
                SectionTitle("Screen on", fmtSpan(v.onMs))
                LegendRow(Accent, "Used", mahText(v.onMah))
                LegendRow(Accent, "Current", maOf(v.onMah, v.onMs)?.let { "~$it mA" } ?: "Measuring")
                val onH = v.onMs / 3_600_000f
                LegendRow(Accent, "Rate", if (onH >= 0.02f && v.hasMah && b.estMah > 0) "${"%.1f".format(v.onMah / b.estMah * 100f / onH)}%/h" else "Measuring")
            }
        }
        item {
            Glass {
                SectionTitle("Screen off", fmtSpan(v.offMs))
                LegendRow(Accent2, "Used", mahText(v.offMah))
                LegendRow(Accent2, "Current", maOf(v.offMah, v.offMs)?.let { "~$it mA" } ?: "Measuring")
                val offPct = if (v.offMs > 0L) 100f * v.deepMs / v.offMs else 0f
                LegendRow(Accent2, "Deep sleep", "${fmtSpan(v.deepMs)}  \u00B7  ${"%.0f".format(offPct)}% of screen off")
                LegendRow(Warn, "Held awake", "${fmtSpan(v.awakeMs)}  \u00B7  ${"%.0f".format(if (v.offMs > 0L) 100f - offPct else 0f)}% of screen off")
            }
        }
        item {
            Glass {
                SectionTitle("Time split")
                SplitBar(v.onMs, v.awakeMs, v.deepMs)
                LegendRow(Accent, "Screen on", fmtSpan(v.onMs))
                LegendRow(Warn, "Held awake", fmtSpan(v.awakeMs))
                LegendRow(Accent2, "Deep sleep", fmtSpan(v.deepMs))
            }
        }
        if (!v.charging) item {
            val cap = if (b.estMah > 0) b.estMah else b.designMah
            val remain = if (cap > 0) cap * b.level / 100f else null
            Glass {
                SectionTitle("Estimated time left", "gets better with use")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LeftCol("Total", leftText(remain, avgMa))
                    LeftCol("Screen on", leftText(remain, maOf(v.onMah, v.onMs)))
                    LeftCol("Screen off", leftText(remain, maOf(v.offMah, v.offMs)))
                }
            }
        }
        item {
            Glass {
                Hint(
                    if (v.hasMah) "Milliamp hours come from the battery's own charge counter, so they follow what the battery really gave."
                    else "This phone does not report a charge counter, so only time is shown."
                )
            }
        }
    }
}

@Composable
private fun LeftCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextLo, fontSize = 12.sp)
        Text(value, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Electric current with a chart, for charging or discharging, over one minute up to six hours. */
@Composable
internal fun CurrentSub(back: () -> Unit) {
    val s by Monitor.snap.collectAsState()
    val b = s.batt
    var tab by remember { mutableIntStateOf(if (Monitor.snap.value.batt.charging) 0 else 1) }
    var range by remember { mutableIntStateOf(1) }
    val spans = longArrayOf(60_000L, 600_000L, 3_600_000L, 21_600_000L)
    val names = listOf("1m", "10m", "1h", "6h")
    val chg = tab == 0
    val st = CurrentLog.stat(spans[range], chg)
    val series = CurrentLog.series(spans[range], chg)
    val live = b.charging == chg
    Page("Electric current", "Live, with average, lowest and highest", GBlue, Icons.Outlined.Bolt, onBack = back) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Charging", chg, GGreen) { tab = 0 }
                Chip("Discharging", !chg, GFire) { tab = 1 }
            }
        }
        item {
            Glass {
                Text(if (live) "${b.ma} mA" else "-", color = TextHi, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (live) "${"%.1f".format(b.mv / 1000f * b.ma / 1000f)} W  \u00B7  ${b.mv} mV" else "Not ${if (chg) "charging" else "discharging"} right now",
                    color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
                if (series.size >= 2) {
                    LineChart(series, if (chg) GGreen else GBlue, Modifier.fillMaxWidth().height(170.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("-${names[range]}", color = TextLo, fontSize = 11.sp)
                        Text("now", color = TextLo, fontSize = 11.sp)
                    }
                } else Hint("Not enough readings in this window yet. Readings come every few seconds while this page is open.")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n -> Chip(n, range == i, GBlue) { range = i } }
            }
        }
        item {
            Glass {
                SectionTitle("Electric current", "last ${names[range]}")
                if (st.n == 0) Hint("No readings yet.") else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LeftCol("Average", "${st.avg} mA")
                    LeftCol("Minimum", "${st.min} mA")
                    LeftCol("Maximum", "${st.max} mA")
                }
            }
        }
        item {
            Glass {
                Hint("The current is what the battery reports right now. Short peaks are normal when the screen or the radio wakes up.")
            }
        }
    }
}

/** Notification when the battery is too hot or too cold. */
@Composable
internal fun AlarmSub(back: () -> Unit) {
    val ctx = LocalContext.current
    val s by Monitor.snap.collectAsState()
    var hi by remember { mutableFloatStateOf(LockStore.int(ctx, "ta_hi", 42).toFloat()) }
    var lo by remember { mutableFloatStateOf(LockStore.int(ctx, "ta_lo", 5).toFloat()) }
    val colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Tint.copy(alpha = 0.15f))
    Page("Temperature alarm", "Warns when the battery is too hot or cold", GFire, Icons.Outlined.Thermostat, onBack = back) {
        item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Icons.Outlined.Thermostat, tempGrad(s.batt.tempC), 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("${"%.1f".format(s.batt.tempC)}\u00B0C", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Battery temperature now", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
        item { PrefSwitch("ta_on", false, Icons.Outlined.NotificationsActive, GFire, "Temperature alarm", "Notify when the battery goes outside the limits below") }
        item {
            Glass {
                SectionTitle("Too hot above", "${hi.toInt()}\u00B0C")
                Slider(
                    value = hi, onValueChange = { hi = it }, valueRange = 30f..55f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "ta_hi", hi.toInt()) }, colors = colors
                )
            }
        }
        item {
            Glass {
                SectionTitle("Too cold below", "${lo.toInt()}\u00B0C")
                Slider(
                    value = lo, onValueChange = { lo = it }, valueRange = 0f..20f,
                    onValueChangeFinished = { LockStore.setInt(ctx, "ta_lo", lo.toInt()) }, colors = colors
                )
            }
        }
        item {
            Glass {
                Hint("A battery kept hot ages faster, and a hot one is also slow to charge. The alarm repeats at most every ten minutes and uses no timer of its own.")
            }
        }
    }
}
