package core

import java.io.File
import java.io.FileWriter
import java.math.BigDecimal
import java.math.BigInteger
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller

class XmlWriter {
    private var currentScore: Score? = null
    private var quantizer: Quantizer? = null
    private var xmlScorePartwise: musicxml.ScorePartwise? = null
    private var currentTimeSignature: TimeSignature = TimeSignature(0, 4, 4)
    private var currentKeySignature: KeySignature = KeySignature(0, 0, KeySignature.Mode.MAJOR)
    private var conductorStaffStack: MutableList<StaffSymbol> = mutableListOf()
    private var currentStaffStack: MutableList<StaffSymbol> = mutableListOf()
    private var carryOverNotesStack: MutableList<Note> = mutableListOf()
    private var carryOverRestsStack: MutableList<Rest> = mutableListOf()
    private var currentMeasureNumber: Int = 0
    private var currentTick: Long = 0L

    /**
     * Converts a score object into musicxml then writes it to a specified file.
     */
    fun writeScoreToXml(score: Score, outputFile: File) {

        currentScore = score
        quantizer = Quantizer(currentScore!!.ticksPerQuarterNote)
        val xmlPartList = musicxml.PartList()
        xmlScorePartwise = musicxml.ScorePartwise().apply {
            this.version = "4.0"
            this.partList = xmlPartList
        }

        for ((index, staff) in score.staves.withIndex()) {
            val xmlPartId = "P${index + 1}"
            val xmlPartName = musicxml.PartName().apply { value = xmlPartId + "TODO" } // TODO: staff.notationInfo.partName
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
        conductorStaffStack =
            this.currentScore!!.conductorStaff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        currentStaffStack = staff.staffSymbols.toMutableList() // creates a copy with intention to be destroyed
        carryOverNotesStack = mutableListOf<Note>()
        currentMeasureNumber = 1
        this.initState()
        while (conductorStaffStack.isNotEmpty() || currentStaffStack.isNotEmpty() || carryOverNotesStack.isNotEmpty()) {
            val xmlMeasure = this.createXmlMeasure()
            xmlMeasures.add(xmlMeasure)
            currentMeasureNumber++
        }
        return xmlMeasures
    }

    private fun initState() {
        currentTick = 0L
        while (conductorStaffStack.firstOrNull()?.anchorTick == 0L) {
            when (val nextConductorStaffSymbol = conductorStaffStack.removeFirst()) {
                is TimeSignature -> this.currentTimeSignature = nextConductorStaffSymbol
                is KeySignature -> this.currentKeySignature = nextConductorStaffSymbol
                // todo: tempo,
                else -> TODO("Staff symbol of type $nextConductorStaffSymbol unsupported in initState function")
            }
        }

    }

    private fun createXmlMeasure(): musicxml.ScorePartwise.Part.Measure {
        val measureStartTick = currentTick
        val nextMeasureStartTick =
            measureStartTick + currentScore!!.ticksPerQuarterNote * currentTimeSignature.numerator / currentTimeSignature.denominator * 4
        val xmlMeasure = musicxml.ScorePartwise.Part.Measure().apply {
            if (currentMeasureNumber == 1) {
                noteOrBackupOrForward.add(createXmlFirstMeasureAttributes())
            }
            number = currentMeasureNumber.toString()
            val nextCarryOverNotesStack = mutableListOf<Note>()
            val nextCarryOverRestsStack = mutableListOf<Rest>()
            while ((conductorStaffStack.isNotEmpty() || currentStaffStack.isNotEmpty() || carryOverNotesStack.isNotEmpty() || carryOverRestsStack.isNotEmpty())) {
                val candidateNextStaffSymbols = listOfNotNull(
                    conductorStaffStack.firstOrNull(),
                    carryOverNotesStack.firstOrNull(),
                    carryOverRestsStack.firstOrNull(),
                    currentStaffStack.firstOrNull(),
                )
                val minTick = candidateNextStaffSymbols.minOf { it.anchorTick }
                if (minTick >= nextMeasureStartTick) {
                    break
                }
                val nextStaffSymbol = when (minTick) {
                    conductorStaffStack.firstOrNull()?.anchorTick -> conductorStaffStack.removeFirst()
                    carryOverNotesStack.firstOrNull()?.anchorTick -> carryOverNotesStack.removeFirst()
                    carryOverRestsStack.firstOrNull()?.anchorTick -> carryOverRestsStack.removeFirst()
                    currentStaffStack.firstOrNull()?.anchorTick -> currentStaffStack.removeFirst()
                    else -> throw Exception("WTF?? the code logic is wrong")
                }
                when (nextStaffSymbol) {
                    is Note -> {
                        var note = nextStaffSymbol
                        if (!note.notationInfo.isChord && (currentTick + note.durationInTicks > nextMeasureStartTick)) { // note exceeds measure
                            // break note into two, separating at bar line
                            val overFlowDurationInTicks = currentTick + note.durationInTicks - nextMeasureStartTick
                            note = Note(
                                note.anchorTick,
                                note.pitch,
                                note.durationInTicks - overFlowDurationInTicks,
                                note.velocity
                            ).apply {
                                this.quantizedAnchorTick = note.quantizedAnchorTick
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(note.durationInTicks)
                                this.quantizedDurationInTicks = closestDurationInTicks
                                notationInfo = note.notationInfo.copy(noteType = closestDurationType, tieStart = true)
                            }
                            val carryOverNote =
                                Note(nextMeasureStartTick, note.pitch, overFlowDurationInTicks, note.velocity).apply {
                                    this.quantizedAnchorTick = nextMeasureStartTick
                                    val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                    this.quantizedDurationInTicks = closestDurationInTicks
                                    notationInfo = note.notationInfo.copy(noteType = closestDurationType, tieEnd = true)
                                }
                            nextCarryOverNotesStack.add(carryOverNote)
                        }
                        val xmlNote = createXmlNote(note)
                        noteOrBackupOrForward.add(xmlNote)
                        if (!note.notationInfo.isChord) {
                            currentTick += note.durationInTicks
                        }
                    }

                    is Rest -> {
                        var rest = nextStaffSymbol
                        val restAnchorTick = rest.anchorTick
                        val restDurationInTicks = rest.durationInTicks
                        if (currentTick + restDurationInTicks > nextMeasureStartTick) {
                            val overFlowDurationInTicks = currentTick + restDurationInTicks - nextMeasureStartTick
                            rest = Rest(restAnchorTick, restDurationInTicks - overFlowDurationInTicks).apply {
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                this.quantizedDurationInTicks = closestDurationInTicks
                                this.notationInfo.restType = closestDurationType
                            }
                            val carryOverRest = Rest(nextMeasureStartTick, overFlowDurationInTicks).apply {
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                this.quantizedAnchorTick = measureStartTick
                                this.quantizedDurationInTicks = closestDurationInTicks
                                this.notationInfo.restType = closestDurationType
                            }
                            nextCarryOverRestsStack.add(carryOverRest)
                        }
                        val xmlRest = createXmlRest(rest)
                        noteOrBackupOrForward.add(xmlRest)
                        currentTick += rest.durationInTicks
                    }

                    is KeySignature -> {
                        currentKeySignature = nextStaffSymbol
                        val xmlMeasureAttributes: musicxml.Attributes =
                            (this.noteOrBackupOrForward.firstOrNull { it is musicxml.Attributes }
                                ?: musicxml.Attributes()) as musicxml.Attributes
                        xmlMeasureAttributes.apply {
                            this.key.add(createXmlKeySignature(currentKeySignature))
                        }
                    }
                }

            }
            carryOverNotesStack = nextCarryOverNotesStack
            carryOverRestsStack = nextCarryOverRestsStack
        }
        return xmlMeasure
    }

    fun createXmlRest(rest: Rest): musicxml.Note {
        return musicxml.Note().apply {
            this.rest = musicxml.Rest()
            this.duration = rest.durationInTicks.toBigDecimal()
            if (rest.notationInfo.restType != null) {
                this.type = musicxml.NoteType().apply {
                    value = rest.notationInfo.restType
                }
            }
        }
    }

    private fun createXmlKeySignature(keySignature: KeySignature): musicxml.Key {
        return musicxml.Key().apply {
            fifths = BigInteger.valueOf(keySignature.fifthsAboveC.toLong())
            mode = keySignature.mode.toString().lowercase()
        }
    }

    private fun createXmlTimeSignature(timeSignature: TimeSignature): musicxml.Time {
        return musicxml.Time().apply {
            val factory = musicxml.ObjectFactory()
            this.timeSignature.add(factory.createTimeBeats(timeSignature.numerator.toString()))
            this.timeSignature.add(factory.createTimeBeatType(timeSignature.denominator.toString()))
        }
    }

    private fun createXmlFirstMeasureAttributes(): musicxml.Attributes {
        return musicxml.Attributes().apply {
            divisions = BigDecimal(currentScore!!.ticksPerQuarterNote)
            key.add(createXmlKeySignature(currentKeySignature))
            time.add(createXmlTimeSignature(currentTimeSignature))
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
            if (note.notationInfo.isChord) {
                this.chord = musicxml.Empty()
            }
            this.pitch = musicxml.Pitch().apply {
                step = note.notationInfo.step ?: musicxml.Step.C // todo: come up with better error handling
                if (note.notationInfo.alter != null) {
                    alter = note.notationInfo.alter
                }
                octave = note.pitch / 12 - 1
            }
            this.duration = BigDecimal(note.durationInTicks)
            if (note.notationInfo.noteType != null) {
                this.type = musicxml.NoteType().apply {
                    this.value = note.notationInfo.noteType
                }
            }
            if (note.notationInfo.tieEnd) {
                this.tie.add(musicxml.Tie().apply {
                    this.type = musicxml.StartStop.STOP
                })
            }
            if (note.notationInfo.tieStart) {
                this.tie.add(musicxml.Tie().apply {
                    this.type = musicxml.StartStop.START
                })
            }
        }
    }
}