package com.bydmate.app.data.trips

import com.bydmate.app.data.local.entity.TripPointEntity

/**
 * Kom-BYDMate: how much of a trip the car was moving, from its GPS points (each carries the speed
 * at its time). Between two points the car counts as moving when the first one says so; a gap
 * longer than [MAX_STEP_MS] (no fix, or thinned old points) is counted only up to that long, so
 * a lost fix does not turn into minutes of driving or standing. Standing = the trip's length minus this.
 */
object TripMovingTime {
    const val MOVING_KMH = 1.0
    const val MAX_STEP_MS = 60_000L

    /** Moving time in ms for points of one trip in time order; null without two points. */
    fun movingMs(points: List<TripPointEntity>): Long? {
        if (points.size < 2) return null
        var moving = 0L
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val dt = (points[i].timestamp - a.timestamp).coerceIn(0L, MAX_STEP_MS)
            if ((a.speedKmh ?: 0.0) >= MOVING_KMH) moving += dt
        }
        return moving
    }
}
