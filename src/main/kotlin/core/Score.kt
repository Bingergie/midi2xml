package core

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
        var isChord: Boolean? = null
        var duration: Int? = null
    }

    override val notationInfo: NoteNotationInfo = NoteNotationInfo()
}

class KeySignature(
    anchorTick: Long,
    val fifthsAboveC: Long,
) : StaffSymbol(anchorTick) {
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