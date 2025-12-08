package core

class Score(val ticksPerBeat: Int) {
    val staves = mutableListOf<Staff>()
}

class Staff(val instrument: Instrument) {
    val staffSymbols = mutableListOf<StaffSymbol>()
}

class Instrument(val instrumentName: String)

abstract class StaffSymbol(val anchorTick: Long) {
    val notationInfo = NotationInfo()
}

class NotationInfo()


class Note(
    anchorTick: Long,
    val durationInTicks: Long,
    val velocity: Int
) : StaffSymbol(anchorTick) {
}

class KeySignature(
    anchorTick: Long,
    val fifthsAboveC: Int,
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