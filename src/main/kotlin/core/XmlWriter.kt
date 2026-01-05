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
    private var carryOverNotesStack: MutableList<Note> = mutableListOf()
    private var carryOverRestsStack: MutableList<Rest> = mutableListOf()
    private var currentMeasureNumber: Int = 0
    private var currentTick: Long = 0L

    /**
     * Converts a score object into musicxml then writes it to a specified file.
     */
    fun writeScoreToXml(score: Score, outputFile: File) {

        createXmlScorePartwise(score)

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
        marshaller.marshal(xmlScorePartwise!!, writer)
    }

    private fun createXmlScorePartwise(score: Score) {
        // set state variables
        currentScore = score
        quantizer = Quantizer(currentScore!!.ticksPerQuarterNote)
        val xmlPartList = musicxml.PartList()
        xmlScorePartwise = musicxml.ScorePartwise().apply {
            this.version = "4.0"
            this.partList = xmlPartList
        }

        // create parts from staves
        for ((index, staff) in currentScore!!.staves.withIndex()) {
            val xmlPartId = "P${index + 1}"
            val xmlPartName = musicxml.PartName().apply {
                value = staff.notationInfo.partName // todo: staff.currentInstrument.name and handle midi instrument info
            }
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
    }

    private fun createXmlMeasuresFromStaff(staff: Staff): List<musicxml.ScorePartwise.Part.Measure> {

        // creates copies with intention to be destroyed in the process of creating measures
        val conductorStaffStack = this.currentScore!!.conductorStaff.staffSymbols.toMutableList()
        val currentStaffStack = staff.staffSymbols.toMutableList()

        // init staff state
        currentTick = 0L
        currentMeasureNumber = 1
        while (conductorStaffStack.firstOrNull()?.anchorTick == 0L) {
            when (val nextConductorStaffSymbol = conductorStaffStack.removeFirst()) {
                is TimeSignature -> this.currentTimeSignature = nextConductorStaffSymbol
                is KeySignature -> this.currentKeySignature = nextConductorStaffSymbol
                // todo: tempo,
                else -> TODO("Staff symbol of type $nextConductorStaffSymbol unsupported in initState function")
            }
        }

        // carry over symbols with duration for next measure. They are replaced every time `createXmlMeasure` is called
        carryOverNotesStack = mutableListOf<Note>()
        carryOverRestsStack = mutableListOf<Rest>()

        // create measures
        val xmlMeasures = mutableListOf<musicxml.ScorePartwise.Part.Measure>()
        while (
            conductorStaffStack.isNotEmpty() ||
            currentStaffStack.isNotEmpty() ||
            carryOverNotesStack.isNotEmpty() ||
            carryOverRestsStack.isNotEmpty()
        ) {
            val xmlMeasure = this.createXmlMeasure(
                conductorStaffStack,
                currentStaffStack,
            )
            xmlMeasures.add(xmlMeasure)
            currentMeasureNumber++
        }
        return xmlMeasures
    }

    private fun createXmlMeasure(
        conductorStaffStack: MutableList<StaffSymbol>,
        currentStaffStack: MutableList<StaffSymbol>
    ): musicxml.ScorePartwise.Part.Measure {
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

            while (
                conductorStaffStack.isNotEmpty() ||
                currentStaffStack.isNotEmpty() ||
                carryOverNotesStack.isNotEmpty() ||
                carryOverRestsStack.isNotEmpty()
            ) { // see another breakout condition a few lines later

                // get the first symbol's anchorTick in these stacks
                val candidateNextStaffSymbols = listOfNotNull(
                    conductorStaffStack.firstOrNull(),
                    carryOverNotesStack.firstOrNull(),
                    carryOverRestsStack.firstOrNull(),
                    currentStaffStack.firstOrNull(),
                )
                val minTick = candidateNextStaffSymbols.minOf { it.anchorTick }

                // BREAKOUT CONDITION: next measure should start —— this is a wierd place to put breakout condition, but it seems to be the most convenient
                if (minTick >= nextMeasureStartTick) {
                    currentTick = nextMeasureStartTick // advance current tick to start of next measure
                    break
                }

                // get first symbol via anchorTick
                val nextStaffSymbol = when (minTick) {
                    conductorStaffStack.firstOrNull()?.anchorTick -> conductorStaffStack.removeFirst()
                    carryOverNotesStack.firstOrNull()?.anchorTick -> carryOverNotesStack.removeFirst()
                    carryOverRestsStack.firstOrNull()?.anchorTick -> carryOverRestsStack.removeFirst()
                    currentStaffStack.firstOrNull()?.anchorTick -> currentStaffStack.removeFirst()
                    else -> throw Exception("Error!! the code logic is wrong")
                }

                // handle nextStaffSymbol
                when (nextStaffSymbol) {
                    is Note -> {
                        var note = nextStaffSymbol

                        // handle if note goes over measure
                        if (!note.notationInfo.isChord && (currentTick + note.durationInTicks > nextMeasureStartTick)) { // note exceeds measure
                            val originalNote = note

                            // break originalNote into two, separating at bar line
                            val overFlowDurationInTicks = currentTick + originalNote.durationInTicks - nextMeasureStartTick
                            note = Note(
                                originalNote.anchorTick,
                                originalNote.pitch,
                                originalNote.durationInTicks - overFlowDurationInTicks, // take all remaining duration of measure
                                originalNote.velocity
                            ).apply {
                                this.quantizedAnchorTick = originalNote.quantizedAnchorTick
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(originalNote.durationInTicks)
                                this.quantizedDurationInTicks = closestDurationInTicks
                                notationInfo = originalNote.notationInfo.copy(noteType = closestDurationType, tieStart = true)
                            }
                            val carryOverNote =
                                Note(nextMeasureStartTick, originalNote.pitch, overFlowDurationInTicks, originalNote.velocity).apply {
                                    this.quantizedAnchorTick = nextMeasureStartTick
                                    val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                    this.quantizedDurationInTicks = closestDurationInTicks
                                    notationInfo = originalNote.notationInfo.copy(noteType = closestDurationType, tieEnd = true)
                                }

                            // add remaining to carryOverStack
                            nextCarryOverNotesStack.add(carryOverNote)
                        }

                        // add note to measure
                        val xmlNote = createXmlNote(note)
                        noteOrBackupOrForward.add(xmlNote)

                        // advance currentTick if not part of a chord
                        if (!note.notationInfo.isChord) {
                            currentTick += note.durationInTicks
                        }
                    }

                    is Rest -> {
                        var rest = nextStaffSymbol

                        // handle if rest goes over measure
                        if (currentTick + rest.durationInTicks > nextMeasureStartTick) {
                            val originalRest = rest

                            // split originalRest into two, separating at bar line
                            val overFlowDurationInTicks = currentTick + rest.durationInTicks - nextMeasureStartTick
                            rest = Rest(
                                rest.anchorTick,
                                rest.durationInTicks - overFlowDurationInTicks // take all remaining duration of measure
                            ).apply {
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                this.quantizedDurationInTicks = closestDurationInTicks
                                notationInfo = originalRest.notationInfo.copy(restType = closestDurationType)
                            }
                            val carryOverRest = Rest(nextMeasureStartTick, overFlowDurationInTicks).apply {
                                val (closestDurationInTicks, closestDurationType) = quantizer!!.getClosestNoteDurationAndType(durationInTicks)
                                this.quantizedAnchorTick = measureStartTick
                                this.quantizedDurationInTicks = closestDurationInTicks
                                notationInfo = originalRest.notationInfo.copy(restType = closestDurationType)
                            }

                            // add remaining to carryOverStack
                            nextCarryOverRestsStack.add(carryOverRest)
                        }

                        // add rest to measure
                        val xmlRest = createXmlRest(rest)
                        noteOrBackupOrForward.add(xmlRest)

                        // advance currentTick
                        currentTick += rest.durationInTicks
                    }

                    is KeySignature -> {
                        currentKeySignature = nextStaffSymbol

                        // get attributes of current measure or create new attributes
                        val xmlMeasureAttributes: musicxml.Attributes =
                            (this.noteOrBackupOrForward.firstOrNull { it is musicxml.Attributes }
                                ?: musicxml.Attributes()) as musicxml.Attributes

                        // add key signature to measure attributes
                        xmlMeasureAttributes.apply {
                            this.key.add(createXmlKeySignature(currentKeySignature))
                        }
                    }
                }

            }
            // update carry-over stacks
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