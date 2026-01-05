package core

import kotlin.Int
import kotlin.String
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Provides functions that quantize durations and anchor ticks.
 */
class Quantizer(val ticksPerQuarterNote: Int) {
    private val quantizedDurationsInTicks: Map<Int, String> = mapOf(
        ticksPerQuarterNote * 4 to "whole",
        ticksPerQuarterNote * 2 to "half",
//            ticksPerQuarterNote * 3 / 2 to "half",
        ticksPerQuarterNote * 1 to "quarter",
//            ticksPerQuarterNote * 2 / 3 to "quarter",
        ticksPerQuarterNote / 2 to "eighth",
//            ticksPerQuarterNote / 3 to "eighth",
        ticksPerQuarterNote / 4 to "16th",
//            ticksPerQuarterNote / 6 to "16th",
//            ticksPerQuarterNote / 8 to "32nd",
    )

    fun getClosestNoteDurationAndType(durationInTicks: Long): Pair<Long, String> {
        val closestDuration = quantizedDurationsInTicks.keys.minBy { durations -> abs(durationInTicks - durations) }
        return Pair(closestDuration.toLong(), quantizedDurationsInTicks[closestDuration] as String)
    }

    fun getClosestQuantizedAnchorTick(anchorTick: Long): Long {
        val closestTickDivision = quantizedDurationsInTicks.keys.minBy { durations -> abs(anchorTick % durations) }
        val quantizedTick = (anchorTick / closestTickDivision.toDouble()).roundToLong() * closestTickDivision
        return quantizedTick
    }


}