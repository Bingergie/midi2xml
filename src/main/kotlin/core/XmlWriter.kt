package core

import java.io.File
import java.io.FileWriter
import java.math.BigDecimal
import java.math.BigInteger
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class XmlWriter {
    private var currentScore: Score? = null
    private var xmlScorePartwise: musicxml.ScorePartwise? = null
    private var currentTimeSignature: TimeSignature = TimeSignature(0, 4, 4)
    private var currentKeySignature: KeySignature = KeySignature(0, 0)
    private var carryOverNotesStack: MutableList<Note> = mutableListOf()
    private var currentMeasureNumber: Int = 0

    fun writeScoreToXml(score: Score, outputFile: File) {
        currentScore = score
        val xmlPartList = musicxml.PartList()
        xmlScorePartwise = musicxml.ScorePartwise().apply {
            this.version = "4.0"
            this.partList = xmlPartList
        }

        for ((index, staff) in score.staves.withIndex()) {
            val xmlPartId = "P${index + 1}"
            val xmlPartName = musicxml.PartName().apply { value = xmlPartId + "TODO" } // TODO: trackData.trackName
            val xmlScorePart = musicxml.ScorePart().apply {
                this.id = xmlPartId
                this.partName = xmlPartName
            }
            if (index == 0) xmlPartList.scorePart = xmlScorePart else xmlPartList.partGroupOrScorePart.add(xmlScorePart)

            val xmlMeasures = createMeasuresFromStaff(staff)
            val xmlPart = musicxml.ScorePartwise.Part().apply {
                this.id = xmlScorePart
                for (xmlMeasure in xmlMeasures) {
                    this.measure.add(xmlMeasure)
                }
            }
            xmlScorePartwise!!.part.add(xmlPart)
        }

        val writer = FileWriter(outputFile)

        val context = JAXBContext.newInstance("musicxml")
        val marshaller = context.createMarshaller().apply {
            setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            setProperty(Marshaller.JAXB_FRAGMENT, false)
            setProperty(
                "com.sun.xml.internal.bind.xmlHeaders",
                """
                    <!DOCTYPE score-partwise PUBLIC
                        "-//Recordare//DTD MusicXML 4.0 Partwise//EN"
                        "http://www.musicxml.org/dtds/partwise.dtd">
                    
                """.trimIndent()
            )
        }
        marshaller.marshal(xmlScorePartwise, writer)
    }

    private fun createMeasuresFromStaff(staff: Staff): List<musicxml.ScorePartwise.Part.Measure> {
        val xmlMeasures = mutableListOf<musicxml.ScorePartwise.Part.Measure>()
        val conductorStaffStack =
            this.currentScore!!.conductorStaff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        val staffStack = staff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        carryOverNotesStack = mutableListOf<Note>()
        // todo: grab all elements of conductorStaffStack with anchorTick 0 and initialize current clef, key, etc...

        var currentMeasureStartTick = 0L
        var currentTick = 0L
        currentMeasureNumber = 0
        var currentTimeSignature = TimeSignature(0, 4, 4)
        while (conductorStaffStack.isNotEmpty() or staffStack.isNotEmpty() or carryOverNotesStack.isNotEmpty()) {
            val xmlMeasure = this.createMeasure(conductorStaffStack, staffStack, carryOverNotesStack)
            xmlMeasures.add(xmlMeasure)
        }
        return xmlMeasures
    }

    private fun createMeasure(
        conductorStaffStack: MutableList<StaffSymbol>,
        currentStaffStack: MutableList<StaffSymbol>,
        carryOverNotesStack: MutableList<Note> // todo: side effect: return new carry over notes and handle ties
    ): musicxml.ScorePartwise.Part.Measure {
        val xmlMeasure = musicxml.ScorePartwise.Part.Measure().apply {
            val factory = musicxml.ObjectFactory()
            if (currentMeasureNumber == 1) {
                noteOrBackupOrForward.add(
                    musicxml.Attributes().apply { // todo: extract this into createFirstMeasureAttributes
                        divisions = BigDecimal(currentScore!!.ticksPerQuarterNote)
                        key.add(musicxml.Key().apply {
                            fifths = BigInteger.valueOf(currentKeySignature.fifthsAboveC)
                        })
                        time.add(musicxml.Time().apply {
                            this.timeSignature.add(factory.createTimeBeats(currentTimeSignature.numerator.toString()))
                            this.timeSignature.add(factory.createTimeBeatType(currentTimeSignature.denominator.toString()))
                        })
                        clef.add(musicxml.Clef().apply {
                            this.sign = musicxml.ClefSign.valueOf("G") // todo
                            this.line = BigInteger.valueOf(2L) // clef.anchorLine
                        })
                    })
            }
            number = currentMeasureNumber.toString()
            while (conductorStaffStack.isNotEmpty() || currentStaffStack.isNotEmpty() || carryOverNotesStack.isNotEmpty()) {
                val candidateNextStaffSymbols = listOfNotNull(
                    conductorStaffStack.firstOrNull(),
                    currentStaffStack.firstOrNull(),
                    carryOverNotesStack.firstOrNull(),
                )
                val minTick = candidateNextStaffSymbols.minOf { it.anchorTick }
                val nextStaffSymbol = when (minTick) {
                    conductorStaffStack.firstOrNull()?.anchorTick -> conductorStaffStack.removeFirst()
                    currentStaffStack.firstOrNull()?.anchorTick -> currentStaffStack.removeFirst()
                    carryOverNotesStack.firstOrNull()?.anchorTick -> carryOverNotesStack.removeFirst()
                    else -> throw Exception("WTF??")
                }
                when (nextStaffSymbol) {
                    is Note -> {
                        val xmlNote = createNote(nextStaffSymbol)
                        this.noteOrBackupOrForward.add(xmlNote)
                    }
                }

            }
        }
        return xmlMeasure
    }

    private fun createNote(note: Note): musicxml.Note {
        return musicxml.Note().apply {
//                            this.voice = 1.toString()
            this.pitch = musicxml.Pitch().apply {
                step = note.notationInfo.step ?: musicxml.Step.C // TODO: convert midi pitch to step, octave, alter
                octave = note.pitch / 12
            }
            this.duration = BigDecimal(note.durationInTicks/* ?: throw Exception("Missing notation duration!!")*/)
            this.type = musicxml.NoteType().apply {
                this.value = "16th" // todo: change to notationInfo.noteType
            }
            if (note.notationInfo.isChord == true)
                this.chord = musicxml.Empty()
        }
    }
}