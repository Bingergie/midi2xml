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

    private fun closestNoteDurationAndType(durationInTicks: Long, ticksPerQuarterNote: Int): Pair<Int, String> {
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
//            ticksPerQuarterNote / 8 to "32th",
        )
        val closestDuration = quantizedDurationsInTicks.keys.minByOrNull { quantizedDurationsInTicks ->
            abs(durationInTicks - quantizedDurationsInTicks)
        }
        return Pair<Int, String>(closestDuration as Int, quantizedDurationsInTicks[closestDuration] as String)
    }


    private fun quantizeNotes() {
        val ticksPerQuarterNote = currentScore!!.ticksPerQuarterNote
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
//            ticksPerQuarterNote / 8 to "32th",
        )
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        val note = staffSymbol
                        val (closestDuration, closestNoteType) = closestNoteDurationAndType(
                            note.durationInTicks, ticksPerQuarterNote
                        )
                        val originalTick = note.anchorTick

                        // Find the closest quantized tick division
                        val closestTickDivision = quantizedDurationsInTicks.keys.minByOrNull { quantizedTick ->
                            abs(originalTick % quantizedTick)
                        }

                        // Quantize the note's anchorTick
                        if (closestTickDivision != null) {
                            val quantizedTick = (originalTick / closestTickDivision.toDouble()).roundToInt() * closestTickDivision
                            note.notationInfo.quantizedAnchorTick = quantizedTick.toLong()
                        }
                        note.notationInfo.quantizedDurationInTicks = closestDuration.toLong()
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
                val currentAnchorTick = note.notationInfo.quantizedAnchorTick ?: note.anchorTick
                val previousAnchorTick = previousNote?.notationInfo?.quantizedAnchorTick ?: previousNote?.anchorTick
                if (currentAnchorTick == previousAnchorTick) {
                    note.notationInfo.isChord = true
                }
                previousNote = note
            }
        }
    }

    private fun addRests() {
        currentScore!!.staves.forEach { staff ->
            var previousNote: Note? = Note(0L, 1, 0L, 0)
            for (index in 0 until staff.staffSymbols.size) {
                when (val staffSymbol = staff.staffSymbols[index]) {
                    is Note -> {
                        val note = staffSymbol
                        if (!(note.notationInfo.isChord == true || previousNote == null)) {
                            val previousNoteAnchorTick =
                                previousNote.notationInfo.quantizedAnchorTick ?: previousNote.anchorTick
                            val previousNoteDurationInTicks =
                                previousNote.notationInfo.quantizedDurationInTicks ?: previousNote.durationInTicks
                            val previousNoteEndTick = previousNoteAnchorTick + previousNoteDurationInTicks
                            val currentNoteAnchorTick = note.notationInfo.quantizedAnchorTick ?: note.anchorTick
                            val restDurationInTicks = currentNoteAnchorTick - previousNoteEndTick
                            if (restDurationInTicks > 0) {
                                val rest = Rest(previousNoteEndTick, restDurationInTicks)
                                staff.staffSymbols.add(index, rest.apply {
                                    this.notationInfo.apply {
                                        val ticksPerQuarterNote = currentScore!!.ticksPerQuarterNote
                                        val (closestDuration, closestDurationType) = closestNoteDurationAndType(rest.durationInTicks, ticksPerQuarterNote)
                                        val (closestAnchorTick, _) = closestNoteDurationAndType(rest.anchorTick % (ticksPerQuarterNote * 4), ticksPerQuarterNote)
                                        this.restType = closestDurationType
                                        this.quantizedAnchorTick = closestAnchorTick.toLong()
                                        this.quantizedDurationInTicks = closestDuration.toLong()
                                    }
                                })
                            }
                        }
                        previousNote = note
                    }
                }
            }
        }
    }

}