package com.example.bpmheartbeat.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View


class SemicircleProgressBar(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var progress: Int = 10
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.SQUARE
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(70f, 10f, 330f, 250f, -180f, (progress / 10f) * 180f, false, paint)
    }

    fun updateProgress(progress: Int) {
        this.progress = progress.coerceIn(0, 10)
        invalidate()
    }


}
