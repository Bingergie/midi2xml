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

abstract class StaffSymbol(anchorTick: Long) {
    open val notationInfo: NotationInfo = NotationInfo()
    val exactAnchorTick: Long = anchorTick
    var quantizedAnchorTick: Long? = null
    val anchorTick: Long = quantizedAnchorTick ?: exactAnchorTick
}

open class NotationInfo()

interface HasDuration {
    val exactDurationInTicks: Long
    var quantizedDurationInTicks: Long?
    val durationInTicks: Long
        get() = quantizedDurationInTicks ?: exactDurationInTicks
}

class Note(
    anchorTick: Long,
    val pitch: Int,
    durationInTicks: Long,
    val velocity: Int
) : StaffSymbol(anchorTick), HasDuration {
//    override val exactDurationInTicks: Long = durationInTicks
    class NoteNotationInfo() : NotationInfo() {
        var step: musicxml.Step? = null
        var isChord: Boolean = false
        var noteType: String? = null
        var alter: BigDecimal? = null
        var tieStart: Boolean = false
        var tieEnd: Boolean = false
    }

    override val notationInfo: NoteNotationInfo = NoteNotationInfo()
    override val exactDurationInTicks: Long = durationInTicks
    override var quantizedDurationInTicks: Long? = null
}

class Rest(
    anchorTick: Long,
    durationInTicks: Long,
) : StaffSymbol(anchorTick), HasDuration {
    class RestNotationInfo() : NotationInfo() {
        var restType: String? = null
    }
    override val notationInfo: RestNotationInfo = RestNotationInfo()
    override val exactDurationInTicks: Long = durationInTicks
    override var quantizedDurationInTicks: Long? = null
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