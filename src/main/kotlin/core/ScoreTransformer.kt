package core

class ScoreTransformer {
    private var currentScore: Score? = null

    fun transformScore(score: Score) {
        currentScore = score
    }

    private fun addNoteNotationStepAndAlter() {
        var currentKeySignature: KeySignature = KeySignature(0, 0, KeySignature.Mode.MAJOR) // default key signature is C major
        for (currentStaff in currentScore!!.staves) {
            for (staffSymbol in currentStaff.staffSymbols) {
                when (staffSymbol) {
                    is Note -> {
                        // generate step lookup
                    }
                    is KeySignature -> currentKeySignature = staffSymbol
                }
            }
        }

    }

    private fun addNoteNotationChordAndVoice() {

    }

    private fun quantizeNotes() {

    }

    private fun addNoteNotationType() {

    }

    private fun addRests() {

    }

}