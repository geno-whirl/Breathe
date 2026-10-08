package com.genowhirl.breathe.ui

import android.content.res.Resources
import com.genowhirl.breathe.R
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.SessionLength
import java.math.BigDecimal
import java.math.RoundingMode

/** 16.0 -> "16", 5.5 -> "5.5", 3.7499 -> "3.75". */
fun formatSeconds(value: Double, decimals: Int = 2): String =
    BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Milliseconds as m:ss, or h:mm:ss past an hour. */
fun formatClock(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "cycle 3", or "cycle 3 of 20" when the session is a number of cycles. */
fun cycleLabel(res: Resources, cycle: Int, settings: AppSettings): String =
    if (settings.sessionLength == SessionLength.CYCLES) {
        res.getString(R.string.cycle_n_of, cycle, settings.sessionCycles)
    } else {
        res.getString(R.string.cycle_n, cycle)
    }
