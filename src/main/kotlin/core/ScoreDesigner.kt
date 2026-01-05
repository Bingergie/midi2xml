package core

import musicxml.Step

/**
 * This class adds and modifies notation information of a score to make it more readable
 */
class ScoreDesigner {
    private var currentScore: Score? = null
    private var quantizer: Quantizer? = null

    fun transformScore(score: Score) {
        currentScore = score
        quantizer = Quantizer(currentScore!!.ticksPerQuarterNote)
        // order sometimes matter... I know it's bad but it works right now
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

        // loop over every note in every staff
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        val note = staffSymbol

                        // set step and alter
                        note.notationInfo.step = stepLookup[note.pitch % 12]
                        note.notationInfo.alter = alterLookup[note.pitch % 12]?.toBigDecimal()
                    }

                    is KeySignature -> currentKeySignature = staffSymbol
                }
            }
        }

    }

    private fun quantizeNotes() {
        assert(currentScore != null)
        assert(quantizer != null)

        // loop over every note in every staff
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        val note = staffSymbol

                        // quantize anchor tick
                        val closestTickDivision = quantizer!!.getClosestQuantizedAnchorTick(note.anchorTick)
                        note.quantizedAnchorTick = closestTickDivision

                        // quantize duration and type
                        val (closestDuration, closestNoteType) = quantizer!!.getClosestNoteDurationAndType(note.durationInTicks)
                        note.quantizedDurationInTicks = closestDuration
                        note.notationInfo.noteType = closestNoteType
                    }
                }
            }
        }
    }

    private fun addNoteNotationChordAndVoice() {
        for (staff in currentScore!!.staves) {
            val notes = staff.staffSymbols.filterIsInstance<Note>()

            // reset previous note
            var previousNote: Note? = null
            notes.forEach { note ->
                // note is part of chord if it has the same anchor tick as previous note
                // todo: this breaks if the two notes have different lengths
                if (note.anchorTick == previousNote?.anchorTick) {
                    note.notationInfo.isChord = true
                }
                previousNote = note
            }
        }
    }

    private fun addRests() {
        // store last note to fill in until last note later
        val lastNoteOfScoreEndTick = currentScore!!.staves.maxOf { staff -> staff.staffSymbols.maxOf { it.anchorTick } }
        currentScore!!.staves.forEach { staff ->

            // clear previous note at beginning of staff
            var previousNote: Note = Note(0L, 1, 0L, 0)

            // loop over every note in every staff
            for (index in 0 until staff.staffSymbols.size) {
                when (val staffSymbol = staff.staffSymbols[index]) {
                    is Note -> {
                        val currentNote = staffSymbol

                        // add rest if there is a gap between previous and current note
                        if (!(currentNote.notationInfo.isChord)) { // not part of chord
                            val previousNoteEndTick = previousNote.anchorTick + previousNote.durationInTicks
                            val restDurationInTicks = currentNote.anchorTick - previousNoteEndTick
                            if (restDurationInTicks > 0) { // there is a gap between previous and current note

                                // create rest that fills this gap
                                val rest = Rest(previousNoteEndTick, restDurationInTicks).apply {
                                    // quantize the rest (maybe redundant since this function is called after quantize notes; restType is missing, but it is not required by musicxml)
                                    val (closestDuration, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                    this.notationInfo.restType = closestDurationType
                                    this.quantizedDurationInTicks = closestDuration
                                    val closestAnchorTick = quantizer!!.getClosestQuantizedAnchorTick(anchorTick)
                                    this.quantizedAnchorTick = closestAnchorTick
                                }

                                // insert rest after between previous and current note
                                staff.staffSymbols.add(index, rest)
                            }
                        }
                        previousNote = currentNote
                    }
                }
            }

            // add rest if there is gap between last note of staff and last note of score
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