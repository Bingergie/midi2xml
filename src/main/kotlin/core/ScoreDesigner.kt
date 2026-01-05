package core

import musicxml.Step
import kotlin.Int
import kotlin.String
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * This class adds and modifies notation information of a score to make it more readable
 */
class ScoreDesigner {
    private var currentScore: Score? = null

    fun transformScore(score: Score) {
        currentScore = score
        addNoteNotationStepAndAlter()
        quantizeNotes()
        addNoteNotationChordAndVoice()
        addRests()
    }

    private fun addNoteNotationStepAndAlter() {
        // todo: use currentKeySignature to generate the following two variables
        val stepLookup =
            listOf(Step.C, Step.C, Step.D, Step.D, Step.E, Step.F, Step.F, Step.G, Step.G, Step.A, Step.A, Step.B)
        val alterLookup = listOf(null, 1, null, 1, null, null, 1, null, 1, null, 1, null)
        var currentKeySignature: KeySignature =
            KeySignature(0, 0, KeySignature.Mode.MAJOR) // default key signature is C major
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        val note = staffSymbol
                        note.notationInfo.step = stepLookup[note.pitch % 12]
                        note.notationInfo.alter = alterLookup[note.pitch % 12]?.toBigDecimal()
                    }

                    is KeySignature -> currentKeySignature = staffSymbol
                }
            }
        }

    }

    private fun getClosestNoteDurationAndType(durationInTicks: Long, ticksPerQuarterNote: Int): Pair<Int, String> {
        val quantizedDurationsInTicks = mapOf<Int, String>(
            ticksPerQuarterNote * 4 to "whole",
            ticksPerQuarterNote * 2 to "half",
//            ticksPerQuarterNote * 3 / 2 to "half",
            ticksPerQuarterNote to "quarter",
//            ticksPerQuarterNote * 2 / 3 to "quarter",
            ticksPerQuarterNote / 2 to "eighth",
//            ticksPerQuarterNote / 3 to "eighth",
            ticksPerQuarterNote / 4 to "16th",
//            ticksPerQuarterNote / 6 to "16th",
//            ticksPerQuarterNote / 8 to "32nd",
        )
        val closestDuration = quantizedDurationsInTicks.keys.minBy { quantizedDurationsInTicks ->
            abs(durationInTicks - quantizedDurationsInTicks)
        }
        return Pair<Int, String>(closestDuration, quantizedDurationsInTicks[closestDuration] as String)
    }

    private fun getClosestQuantizedAnchorTick(anchorTick: Long, ticksPerQuarterNote: Int): Int {
        val quantizedDurationsInTicks = mapOf<Int, String>(
            ticksPerQuarterNote * 4 to "whole",
            ticksPerQuarterNote * 2 to "half",
//            ticksPerQuarterNote * 3 / 2 to "half",
            ticksPerQuarterNote to "quarter",
//            ticksPerQuarterNote * 2 / 3 to "quarter",
            ticksPerQuarterNote / 2 to "eighth",
//            ticksPerQuarterNote / 3 to "eighth",
            ticksPerQuarterNote / 4 to "16th",
//            ticksPerQuarterNote / 6 to "16th",
//            ticksPerQuarterNote / 8 to "32nd",
        )
        val closestTickDivision = quantizedDurationsInTicks.keys.minBy { quantizedTick -> abs(anchorTick % quantizedTick) }
        val quantizedTick = (anchorTick / closestTickDivision.toDouble()).roundToInt() * closestTickDivision
        return quantizedTick
    }


    private fun quantizeNotes() {
        val ticksPerQuarterNote = currentScore!!.ticksPerQuarterNote
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        val note = staffSymbol

                        // quantize anchor tick
                        val originalTick = note.anchorTick
                        val closestTickDivision = getClosestQuantizedAnchorTick(note.anchorTick, ticksPerQuarterNote)

                        // quantize duration and type
                        val (closestDuration, closestNoteType) = getClosestNoteDurationAndType(
                            note.durationInTicks, ticksPerQuarterNote
                        )
                        note.quantizedDurationInTicks = closestDuration.toLong()
                        note.notationInfo.noteType = closestNoteType
                    }
                }
            }
        }
    }

    private fun addNoteNotationChordAndVoice() {
        for (staff in currentScore!!.staves) {
            val notes = staff.staffSymbols.filterIsInstance<Note>()
            var previousNote: Note? = null
            notes.forEach { note ->
                val currentAnchorTick = note.anchorTick
                val previousAnchorTick = previousNote?.anchorTick
                if (currentAnchorTick == previousAnchorTick) {
                    note.notationInfo.isChord = true
                }
                previousNote = note
            }
        }
    }

    private fun addRests() {
        val lastNoteOfScoreEndTick = currentScore!!.staves.maxOf { staff -> staff.staffSymbols.maxOf { it.anchorTick } }
        currentScore!!.staves.forEach { staff ->
            var previousNote: Note? = Note(0L, 1, 0L, 0)
            for (index in 0 until staff.staffSymbols.size) {
                when (val staffSymbol = staff.staffSymbols[index]) {
                    is Note -> {
                        val currentNote = staffSymbol
                        if (!(currentNote.notationInfo.isChord || previousNote == null)) {
                            val previousNoteEndTick = previousNote.anchorTick + previousNote.durationInTicks
                            val restDurationInTicks = currentNote.anchorTick - previousNoteEndTick
                            if (restDurationInTicks > 0) {
                                val rest = Rest(previousNoteEndTick, restDurationInTicks).apply {
                                    val ticksPerQuarterNote = currentScore!!.ticksPerQuarterNote
                                    val (closestDuration, closestDurationType) = getClosestNoteDurationAndType(durationInTicks, ticksPerQuarterNote)
                                    this.notationInfo.restType = closestDurationType
                                    this.quantizedDurationInTicks = closestDuration.toLong()
                                    val closestAnchorTick = getClosestQuantizedAnchorTick(anchorTick, ticksPerQuarterNote)
                                    this.quantizedAnchorTick = closestAnchorTick.toLong()
                                }
                                staff.staffSymbols.add(index, rest)
                            }
                        }
                        previousNote = currentNote
                    }
                }
            }
            val lastNoteOfStaff = staff.staffSymbols.lastOrNull {it is Note} as Note
            val lastNoteOfStaffEndTick = lastNoteOfStaff.anchorTick + lastNoteOfStaff.durationInTicks
            if (lastNoteOfStaffEndTick < lastNoteOfScoreEndTick) {
                val restDuration = lastNoteOfScoreEndTick - lastNoteOfStaffEndTick
                val rest = Rest(lastNoteOfStaffEndTick, restDuration)
                staff.staffSymbols.add(rest)
            }
        }
    }

}