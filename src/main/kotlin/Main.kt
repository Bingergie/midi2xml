import core.MidiParser
import musicxml.*
import java.io.File
import java.io.FileWriter
import java.math.BigDecimal
import java.math.BigInteger
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller


fun exportNotesToXML(midiData: MidiParser.Companion.MidiData): ScorePartwise {

    val scorePartwise = ScorePartwise().apply {
        this.version = "4.0"
    }
    val partList = PartList()
    scorePartwise.partList = partList

    val tracksData = midiData.tracksData
    for ((index, trackData) in tracksData.withIndex()) {
        val partId = "P${index + 1}"
        val partName = PartName().apply { value = partId + "TODO" } // TODO: trackData.trackName
        val scorePart = ScorePart().apply {
            this.id = partId
            this.partName = partName
        }

        if (index == 0) partList.scorePart = scorePart else partList.partGroupOrScorePart.add(scorePart)

        fun createMeasures(): List<ScorePartwise.Part.Measure> {
            val measures = mutableListOf<ScorePartwise.Part.Measure>()

            val notesCopy = trackData.notesData.toMutableList()
            val timeSignaturesCopy = trackData.timeSignatureData.toMutableList()
            var currentTimeSignature = timeSignaturesCopy.removeAt(0)
            var currentTick = 0L

            val carryOverNotes = mutableListOf<MidiParser.Companion.NoteData>()
            var measureNumber = 1
            while (notesCopy.isNotEmpty() or carryOverNotes.isNotEmpty()) { // loop over measures
                val notesQueue = mutableListOf<MidiParser.Companion.NoteData>()
                val timeSignatureQueue = mutableListOf<MidiParser.Companion.TimeSignatureData>()
                // Move notes to queues until we reach a tick that is not in the current measure
                val tickMax =
                    currentTimeSignature.numerator / currentTimeSignature.denominator * midiData.divisionsPerQuarterNote * 4
                while (notesCopy.firstOrNull() != null) {
                    val tempNote = notesCopy.first()
                    if (tempNote.startTick + tempNote.duration <= tickMax) {
                        notesQueue.add(notesCopy.removeFirst())
                    } else {
                        break
                    }
                }
                // todo: do the same as above for time signatures and other things
                // start creating measure
                val measure = ScorePartwise.Part.Measure().apply {
                    val factory = ObjectFactory()
                    if (measureNumber == 1) {
                        noteOrBackupOrForward.add(Attributes().apply {
                            divisions = BigDecimal(midiData.divisionsPerQuarterNote)
                            key.add(Key().apply {
                                fifths = BigInteger.valueOf(0L)
                            })
                            time.add(Time().apply {
                                this.timeSignature.add(factory.createTimeBeats(currentTimeSignature.numerator.toString()))
                                this.timeSignature.add(factory.createTimeBeatType(currentTimeSignature.denominator.toString()))
                            })
                            clef.add(Clef().apply {
                                this.sign = ClefSign.valueOf("G")
                                this.line = BigInteger.valueOf(2L)
                            })
                        })
                    }
                    number = measureNumber++.toString()
                    while (notesQueue.isNotEmpty()) {
                        val noteData = notesQueue.removeFirst()
                        val note = Note().apply {
//                            this.voice = 1.toString()
                            this.pitch = Pitch().apply {
                                step = Step.C // TODO: convert midi pitch to step, octave, alter
                                octave = noteData.pitch / 12
                            }
//                            add duration
                            this.duration = BigDecimal(noteData.duration)
                            this.type = NoteType().apply {
                                this.value = "16th"
                            }
                            // todo: handle concurrent notes
//                            check next note
//                             if same startTick
//                             check duration
//                             if same duration: <chord/> for next note
//                             if different duration:
                        }
                        this.noteOrBackupOrForward.add(note)
                    }
                }

                // add any remaining duration of notes to carryOverNotes

                measures.add(measure)
                currentTick += trackData.timeSignatureData.first().numerator * midiData.divisionsPerQuarterNote
            }

            return measures.toList()
        }
        scorePartwise.part.add(ScorePartwise.Part().apply {
            this.id = scorePart
            val measures = createMeasures()
            for (measure in measures) {
                this.measure.add(measure)
            }
        })
    }


    return scorePartwise
}


fun main(args: Array<String>) {
//    val midiInput: File = File("src/test/resources/tsmidi/TS02-01 Who's licking it.mid")
    val midiInput: File = File("src/test/resources/mary.mid")
    val notes = MidiParser.getNotesFromMidi(midiInput)
    println(notes)
    val score = exportNotesToXML(notes)

    val writer = FileWriter("_test/test_sep16.musicxml")

    val context = JAXBContext.newInstance("musicxml")
    val marshaller = context.createMarshaller().apply {
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        setProperty(Marshaller.JAXB_FRAGMENT, false)
        setProperty(
            "com.sun.xml.internal.bind.xmlHeaders", """
            <!DOCTYPE score-partwise PUBLIC
                "-//Recordare//DTD MusicXML 4.0 Partwise//EN"
                "http://www.musicxml.org/dtds/partwise.dtd">
            
        """.trimIndent()
        )
    }
    marshaller.marshal(score, writer)
    println(writer.toString())
}