package com.example.autonix_ridersafety

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import org.osmdroid.views.MapView
import kotlin.math.abs

class SmartMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MapView(context, attrs) {

    private var startX = 0f
    private var startY = 0f
    private val touchSlop = 10f // minimum distance to start scrolling

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // Record initial touch position
                startX = ev.x
                startY = ev.y
                // Disallow parent to intercept at first
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)

                // If vertical movement dominates, allow parent to scroll
                if (dy > dx && dy > touchSlop) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Allow parent to intercept after touch ends
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(ev)
    }
}
