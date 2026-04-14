package com.raviraj.emergencymesh

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.random.Random

class MeshRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val neonBlue = ContextCompat.getColor(context, R.color.neon_blue)
    
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonBlue
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonBlue
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonBlue
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 40
    }

    private var pulseRadius = 0f
    private var pulseAlpha = 255

    private val dots = mutableListOf<RadarDot>()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val progress = it.animatedValue as Float
            pulseRadius = progress * (width / 2f)
            pulseAlpha = ((1f - progress) * 255).toInt()
            invalidate()
        }
    }

    data class RadarDot(val x: Float, val y: Float, var alpha: Int = 255, val timestamp: Long = System.currentTimeMillis())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun addDiscovery(senderId: String) {
        // Random position within the radar circle
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val radius = Random.nextDouble(20.0, (width / 2.5).toDouble())
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        val x = (centerX + radius * Math.cos(angle)).toFloat()
        val y = (centerY + radius * Math.sin(angle)).toFloat()
        
        dots.add(RadarDot(x, y))
        if (dots.size > 15) dots.removeAt(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = width / 2f

        // Draw Grid
        canvas.drawCircle(centerX, centerY, maxRadius * 0.3f, gridPaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.6f, gridPaint)
        canvas.drawCircle(centerX, centerY, maxRadius * 0.9f, gridPaint)
        canvas.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius, gridPaint)

        // Draw Pulse
        pulsePaint.alpha = pulseAlpha
        canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)

        // Draw Discovery Dots
        val currentTime = System.currentTimeMillis()
        val iterator = dots.iterator()
        while (iterator.hasNext()) {
            val dot = iterator.next()
            val age = currentTime - dot.timestamp
            if (age > 5000) {
                iterator.remove()
                continue
            }
            
            dotPaint.alpha = ((1f - age / 5000f) * 255).toInt()
            canvas.drawCircle(dot.x, dot.y, 8f, dotPaint)
        }
    }
}
