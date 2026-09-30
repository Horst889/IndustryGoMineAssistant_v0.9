package com.example.industrygoassistant

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Detects the large 96 m green probing circle. */
object GreenAreaDetector {
    fun detect(bitmap: Bitmap): LocalArea? {
        val mapTop = (bitmap.height * 0.25f).toInt()
        val mapBottom = (bitmap.height * 0.79f).toInt() // never include bottom action/navigation bars
        val center = detectLocationPin(bitmap, mapTop, mapBottom) ?: return null

        // On the supplied phone layout the 96 m circle is a large ring. Restricting
        // the radius prevents the many small green mine rings from being mistaken for it.
        val minRadius = max(150f, bitmap.width * 0.24f)
        val maxRadius = min(bitmap.width * 0.43f, (mapBottom - mapTop) * 0.48f)
        if (maxRadius <= minRadius) return null

        var bestRadius = 0f
        var bestScore = 0f
        var r = minRadius
        while (r <= maxRadius) {
            val score = circleGreenScore(bitmap, center, r, mapTop, mapBottom)
            if (score > bestScore) { bestScore = score; bestRadius = r }
            r += 2f
        }

        // Fail closed: if the large ring is not clear, do not probe anywhere.
        return if (bestScore >= 0.30f) LocalArea(center, bestRadius, bestScore) else null
    }

    /**
     * Finds the filled red map pin as a connected component. The old implementation
     * averaged every red pixel and was pulled away by the many red rings around mines.
     */
    private fun detectLocationPin(bitmap: Bitmap, top: Int, bottom: Int): PointF? {
        val left = (bitmap.width * 0.18f).toInt()
        val right = (bitmap.width * 0.82f).toInt()
        val step = 2
        val w = (right - left + step - 1) / step
        val h = (bottom - top + step - 1) / step
        val red = BooleanArray(w * h)
        fun idx(x: Int, y: Int) = y * w + x
        for (gy in 0 until h) for (gx in 0 until w) {
            val x = left + gx * step; val y = top + gy * step
            if (x >= bitmap.width || y >= bitmap.height) continue
            red[idx(gx, gy)] = isPinRed(bitmap.getPixel(x, y))
        }
        val seen = BooleanArray(red.size)
        data class C(val count:Int,val minX:Int,val maxX:Int,val minY:Int,val maxY:Int,val sx:Long,val sy:Long)
        val comps = mutableListOf<C>()
        val q = ArrayDeque<Int>()
        for (gy in 0 until h) for (gx in 0 until w) {
            val start = idx(gx, gy); if (!red[start] || seen[start]) continue
            seen[start] = true; q.add(start)
            var count=0; var minX=gx; var maxX=gx; var minY=gy; var maxY=gy; var sx=0L; var sy=0L
            while(q.isNotEmpty()) {
                val v=q.removeFirst(); val x=v%w; val y=v/w
                count++; minX=min(minX,x); maxX=max(maxX,x); minY=min(minY,y); maxY=max(maxY,y); sx+=x; sy+=y
                val ns=intArrayOf(v-1,v+1,v-w,v+w)
                for(n in ns) {
                    if(n<0 || n>=red.size || seen[n] || !red[n]) continue
                    val nx=n%w; val ny=n/w
                    if (kotlin.math.abs(nx-x)+kotlin.math.abs(ny-y)!=1) continue
                    seen[n]=true; q.add(n)
                }
            }
            comps += C(count,minX,maxX,minY,maxY,sx,sy)
        }
        val screenCx = bitmap.width * 0.5f
        val best = comps.mapNotNull { c ->
            val bw=(c.maxX-c.minX+1)*step; val bh=(c.maxY-c.minY+1)*step
            val fill=c.count.toFloat()/((c.maxX-c.minX+1)*(c.maxY-c.minY+1)).toFloat()
            if (bw !in 18..85 || bh !in 20..95 || c.count !in 45..1100 || fill < 0.22f) null
            else {
                val cx=left+(c.sx.toFloat()/c.count)*step; val cy=top+(c.sy.toFloat()/c.count)*step
                val score = fill*4f - kotlin.math.abs(cx-screenCx)/bitmap.width
                Triple(score,cx,cy)
            }
        }.maxByOrNull { it.first } ?: return null
        return PointF(best.second,best.third)
    }

    private fun circleGreenScore(bitmap: Bitmap, center: PointF, radius: Float, top: Int, bottom: Int): Float {
        var hits=0; var considered=0
        val samples=144
        for(i in 0 until samples) {
            val a=2.0*PI*i/samples; val ux=cos(a).toFloat(); val uy=sin(a).toFloat()
            var valid=false; var hit=false
            for(dr in -5..5) {
                val x=(center.x+ux*(radius+dr)).toInt(); val y=(center.y+uy*(radius+dr)).toInt()
                if(x !in 0 until bitmap.width || y !in top until bottom) continue
                valid=true
                if(isGreen(bitmap.getPixel(x,y))) { hit=true; break }
            }
            if(valid) { considered++; if(hit) hits++ }
        }
        return if(considered < samples/2) 0f else hits.toFloat()/considered
    }

    private fun isPinRed(c:Int):Boolean {
        val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
        return r>215 && g in 45..155 && b in 45..155 && r>g+70 && r>b+65
    }
    private fun isGreen(c:Int):Boolean {
        val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
        return g>=115 && g>=r+15 && g>=b+6
    }
}
