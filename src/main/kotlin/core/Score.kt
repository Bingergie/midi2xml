package core

import java.math.BigDecimal


class Score(val ticksPerQuarterNote: Int) {
    val staves = mutableListOf<Staff>()
    val conductorStaff = Staff()

    override fun toString(): String {
        return "Score(ticksPerQuarterNote=$ticksPerQuarterNote, staves(len=${staves.size})=[${staves.joinToString(", ")}], conductorStaff=$conductorStaff)"
    }
}

class Staff() {
    val staffSymbols = mutableListOf<StaffSymbol>()
    fun add(staffSymbol: StaffSymbol) {
        staffSymbols.add(staffSymbol)
    }

    override fun toString(): String {
        return "Staff(staffSymbols=${staffSymbols.joinToString(", ")})"
    }
}

class Instrument(val instrumentName: String)

abstract class StaffSymbol(open val anchorTick: Long) {
    open val notationInfo: NotationInfo = NotationInfo()
//    override fun toString(): String {
//        return this::class.simpleName + "(" +
//                this::class.members
//                    .filterIsInstance<kotlin.reflect.KProperty<*>>()
//                    .joinToString(", ") { "${it.name}=${it.getter.call(this)}" } +
//                ")"
//    }
}

/*
abstract class StaffSymbol(anchorTick: Long) {
    open val notationInfo: NotationInfo = NotationInfo()
    private val exactAnchorTick: Long = anchorTick
    private val quantizedAnchorTick: Long? = null
    val anchorTick: Long = quantizedAnchorTick ?: exactAnchorTick

}
 */

open class NotationInfo()
/*
abstract interface HasDuration {
    // overrides duration get
}
*/

class Note(
    anchorTick: Long,
    val pitch: Int,
    durationInTicks: Long,
    val velocity: Int
) : StaffSymbol(anchorTick) {
    val exactDurationInTicks: Long = durationInTicks
    class NoteNotationInfo() : NotationInfo() {
        var step: musicxml.Step? = null
        var isChord: Boolean = false
        @Deprecated(
            message = "Use note.anchorTick instead; it automatically uses the quantized value when present.",
            level = DeprecationLevel.WARNING
        )
        var quantizedAnchorTick: Long? = null
        @Deprecated(
            message = "Use note.durationInTicks instead; it automatically uses the quantized value when present.",
            level = DeprecationLevel.WARNING
        )
        var quantizedDurationInTicks: Long? = null
        var noteType: String? = null
        var alter: BigDecimal? = null
        var tieStart: Boolean = false
        var tieEnd: Boolean = false
    }

    override val notationInfo: NoteNotationInfo = NoteNotationInfo()
    override val anchorTick: Long
        get() = notationInfo.quantizedAnchorTick ?: super.anchorTick

    val durationInTicks: Long
        get() = notationInfo.quantizedDurationInTicks ?: exactDurationInTicks
}

class Rest(
    anchorTick: Long,
    durationInTicks: Long,
) : StaffSymbol(anchorTick) {
    val exactDurationInTicks: Long = durationInTicks
    class RestNotationInfo() : NotationInfo() {
        @Deprecated(
            message = "Use note.anchorTick instead; it automatically uses the quantized value when present.",
            level = DeprecationLevel.WARNING
        )
        var quantizedAnchorTick: Long? = null
        @Deprecated(
            message = "Use note.durationInTicks instead; it automatically uses the quantized value when present.",
            level = DeprecationLevel.WARNING
        )
        var quantizedDurationInTicks: Long? = null
        var restType: String? = null
    }
    override val notationInfo: RestNotationInfo = RestNotationInfo()
    override val anchorTick: Long
        get() = notationInfo.quantizedAnchorTick ?: super.anchorTick

    val durationInTicks: Long
        get() = notationInfo.quantizedDurationInTicks ?: exactDurationInTicks
}

class KeySignature(
    anchorTick: Long,
    val fifthsAboveC: Int,
    val mode: Mode,
) : StaffSymbol(anchorTick) {
    enum class Mode(val number: Int) {
        MAJOR(0),
        MINOR(1);

        companion object {
            fun fromInt(number: Int): Mode {
                return entries.firstOrNull() { it.number == number }
                    ?: throw IllegalArgumentException("Invalid mode number: $number")
            }
        }
    }
}

class TimeSignature(
    anchorTick: Long,
    val numerator: Int,
    val denominator: Int,
) : StaffSymbol(anchorTick) {
}

class Clef(
    anchorTick: Long,
    val type: String,
) : StaffSymbol(anchorTick) {
}