package com.example.industrygoassistant

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.max

object ScreenSafety {
    /**
     * Conservative pre-check for a candidate. Existing mine icons are usually
     * dark circular objects with coloured rings. We only skip very dark centres;
     * the game itself remains the final authority on whether a position is valid.
     */
    fun looksOccupied(bitmap: Bitmap, p: PointF): Boolean {
        val rr = max(8, (bitmap.width * 0.014f).toInt())
        var dark = 0
        var total = 0
        for (dy in -rr..rr step 2) {
            for (dx in -rr..rr step 2) {
                if (dx * dx + dy * dy > rr * rr) continue
                val x = p.x.toInt() + dx
                val y = p.y.toInt() + dy
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                val c = bitmap.getPixel(x, y)
                val lum = (Color.red(c) * 0.21 + Color.green(c) * 0.72 + Color.blue(c) * 0.07)
                if (lum < 72) dark++
                total++
            }
        }
        return total > 0 && dark.toFloat() / total > 0.58f
    }

    /**
     * Detects the large bright-blue confirmation button in the build dialog.
     * Industry GO does not always expose this button through Accessibility or OCR,
     * so image-based detection is the most reliable final fallback.
     */
    fun findBlueConfirmationButton(bitmap: Bitmap): PointF? {
        // Dialog-Button from Screenshot_6: x~50-72 %, y~56-62 %.
        // Tight bounds avoid the blue map controls behind the dimmed dialog.
        val left = (bitmap.width * 0.46f).toInt()
        val right = (bitmap.width * 0.76f).toInt()
        val top = (bitmap.height * 0.54f).toInt()
        val bottom = (bitmap.height * 0.65f).toInt()
        val step = 2
        val w = ((right - left) + step - 1) / step
        val h = ((bottom - top) + step - 1) / step
        if (w <= 0 || h <= 0) return null

        val mask = BooleanArray(w * h)
        fun idx(x: Int, y: Int) = y * w + x
        for (gy in 0 until h) {
            for (gx in 0 until w) {
                val x = left + gx * step
                val y = top + gy * step
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                mask[idx(gx, gy)] = b >= 150 && g >= 70 && b >= r + 55 && b >= g + 35
            }
        }

        val seen = BooleanArray(mask.size)
        val q = java.util.ArrayDeque<Int>()
        data class Comp(val count: Int, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)
        val comps = mutableListOf<Comp>()
        for (gy in 0 until h) for (gx in 0 until w) {
            val start = idx(gx, gy)
            if (!mask[start] || seen[start]) continue
            seen[start] = true
            q.add(start)
            var count = 0
            var minX = gx; var maxX = gx; var minY = gy; var maxY = gy
            while (q.isNotEmpty()) {
                val v = q.removeFirst()
                val x = v % w; val y = v / w
                count++
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                val nbs = intArrayOf(v - 1, v + 1, v - w, v + w)
                for (n in nbs) {
                    if (n !in mask.indices || seen[n] || !mask[n]) continue
                    val nx = n % w; val ny = n / w
                    if (kotlin.math.abs(nx - x) + kotlin.math.abs(ny - y) != 1) continue
                    seen[n] = true
                    q.add(n)
                }
            }
            comps += Comp(count, minX, maxX, minY, maxY)
        }

        val best = comps.mapNotNull { c ->
            val bw = (c.maxX - c.minX + 1) * step
            val bh = (c.maxY - c.minY + 1) * step
            val area = bw * bh
            if (bw < bitmap.width * 0.12f || bh < bitmap.height * 0.028f || area < 3500) null
            else c
        }.maxByOrNull { it.count } ?: return null

        val cx = left + ((best.minX + best.maxX + 1) * step) / 2f
        val cy = top + ((best.minY + best.maxY + 1) * step) / 2f
        return PointF(cx, cy)
    }

}
