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

abstract class StaffSymbol(val anchorTick: Long) {
    open val notationInfo: NotationInfo = NotationInfo()
//    override fun toString(): String {
//        return this::class.simpleName + "(" +
//                this::class.members
//                    .filterIsInstance<kotlin.reflect.KProperty<*>>()
//                    .joinToString(", ") { "${it.name}=${it.getter.call(this)}" } +
//                ")"
//    }
}

open class NotationInfo()


class Note(
    anchorTick: Long,
    val pitch: Int,
    val durationInTicks: Long,
    val velocity: Int
) : StaffSymbol(anchorTick) {
    class NoteNotationInfo() : NotationInfo() {
        var step: musicxml.Step? = null
        var isChord: Boolean? = null
        var quantizedAnchorTick: Long? = null
        var quantizedDurationInTicks: Long? = null
        var noteType: String? = null
        var alter: BigDecimal? = null
    }

    override val notationInfo: NoteNotationInfo = NoteNotationInfo()
}

class Rest(
    anchorTick: Long,
    val durationInTicks: Long,
) : StaffSymbol(anchorTick) {
    class RestNotationInfo() : NotationInfo() {
        var quantizedAnchorTick: Long? = null
        var quantizedDurationInTicks: Long? = null
        var restType: String? = null
    }
    override val notationInfo: RestNotationInfo = RestNotationInfo()
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