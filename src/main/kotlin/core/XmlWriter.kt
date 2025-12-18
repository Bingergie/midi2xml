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
    private var conductorStaffStack: MutableList<StaffSymbol> = mutableListOf()
    private var currentStaffStack: MutableList<StaffSymbol> = mutableListOf()
    private var carryOverNotesStack: MutableList<Note> = mutableListOf()
    private var currentMeasureNumber: Int = 0
    private var currentTick: Long = 0L

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

            val xmlMeasures = createXmlMeasuresFromStaff(staff)
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

    private fun createXmlMeasuresFromStaff(staff: Staff): List<musicxml.ScorePartwise.Part.Measure> {
        val xmlMeasures = mutableListOf<musicxml.ScorePartwise.Part.Measure>()
        conductorStaffStack = this.currentScore!!.conductorStaff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        currentStaffStack = staff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        carryOverNotesStack = mutableListOf<Note>()
        currentMeasureNumber = 1
        this.initSignaturesStates()
        while (conductorStaffStack.isNotEmpty() || currentStaffStack.isNotEmpty() || carryOverNotesStack.isNotEmpty()) {
            val xmlMeasure = this.createXmlMeasure()
            xmlMeasures.add(xmlMeasure)
            currentMeasureNumber++
        }
        return xmlMeasures
    }

    private fun initSignaturesStates() {
        while (conductorStaffStack.firstOrNull()?.anchorTick == 0L) {
            when (val nextConductorStaffSymbol = conductorStaffStack.removeFirst()) {
                is TimeSignature -> this.currentTimeSignature = nextConductorStaffSymbol
                is KeySignature -> this.currentKeySignature = nextConductorStaffSymbol
                else -> throw Exception("Staff symbol of type $nextConductorStaffSymbol unsupported")
            }
        }

    }

    private fun createXmlMeasure(): musicxml.ScorePartwise.Part.Measure {
        val measureStartTick = currentTick
        val nextMeasureStartTick = measureStartTick + currentScore!!.ticksPerQuarterNote * currentMeasureNumber
        val xmlMeasure = musicxml.ScorePartwise.Part.Measure().apply {
            if (currentMeasureNumber == 1) {
                noteOrBackupOrForward.add(createXmlFirstMeasureAttributes())
            }
            number = currentMeasureNumber.toString()
            while (currentTick < nextMeasureStartTick && (conductorStaffStack.isNotEmpty() || currentStaffStack.isNotEmpty() || carryOverNotesStack.isNotEmpty())) {
                val candidateNextStaffSymbols = listOfNotNull(
                    conductorStaffStack.firstOrNull(),
                    carryOverNotesStack.firstOrNull(),
                    currentStaffStack.firstOrNull(),
                )
                val minTick = candidateNextStaffSymbols.minOf { it.anchorTick }
                val nextStaffSymbol = when (minTick) {
                    conductorStaffStack.firstOrNull()?.anchorTick -> conductorStaffStack.removeFirst()
                    carryOverNotesStack.firstOrNull()?.anchorTick -> carryOverNotesStack.removeFirst()
                    currentStaffStack.firstOrNull()?.anchorTick -> currentStaffStack.removeFirst()
                    else -> throw Exception("WTF?? the code logic is wrong")
                }
                when (nextStaffSymbol) {
                    is Note -> {
                        var note = nextStaffSymbol
                        if (currentTick + note.durationInTicks > nextMeasureStartTick) {
                            val overFlowDurationInTicks = currentTick + note.durationInTicks - nextMeasureStartTick
                            note = Note(note.anchorTick, note.pitch, note.durationInTicks - overFlowDurationInTicks, note.velocity) // todo: copy notationInfo, add tie
                            carryOverNotesStack.add(Note(nextMeasureStartTick, note.pitch, overFlowDurationInTicks, note.velocity))
                        }
                        val xmlNote = createXmlNote(note)
                        this.noteOrBackupOrForward.add(xmlNote)
                        currentTick += note.durationInTicks
                    }
                }

            }
        }
        return xmlMeasure
    }

    fun createXmlFirstMeasureAttributes(): musicxml.Attributes {
        return musicxml.Attributes().apply {
            divisions = BigDecimal(currentScore!!.ticksPerQuarterNote)
            key.add(musicxml.Key().apply {
                fifths = BigInteger.valueOf(currentKeySignature.fifthsAboveC)
            })
            time.add(musicxml.Time().apply {
                val factory = musicxml.ObjectFactory()
                this.timeSignature.add(factory.createTimeBeats(currentTimeSignature.numerator.toString()))
                this.timeSignature.add(factory.createTimeBeatType(currentTimeSignature.denominator.toString()))
            })
            clef.add(musicxml.Clef().apply {
                this.sign = musicxml.ClefSign.valueOf("G") // todo: currentClef
                this.line = BigInteger.valueOf(2L) // clef.anchorLine
            })
            // todo: if top staff, add tempo as direction
        }

    }

    private fun createXmlNote(note: Note): musicxml.Note {
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