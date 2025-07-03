package core

import java.io.File
import javax.sound.midi.*


class MidiParser {

    companion object {
        fun getNotesFromMidi(file: File): MidiData {
            val sequence = MidiSystem.getSequence(file)
            require(sequence.divisionType == Sequence.PPQ) { TODO("PPQ not supported yet") }
            val divisions = sequence.resolution
            val tracksData = mutableListOf<TrackData>()
            mutableListOf<TimeSignatureData>()
            for (track in sequence.tracks) {
                val notes = mutableListOf<NoteData>()
                HashMap<Pair<Int, Int>, Long>() // <channel, pitch> -> startTick
                for (i in 0..<track.size()) {
                    val event: MidiEvent = track.get(i)
                    val message = event.message
                    when (message) {
                        is ShortMessage -> {
                            when (message.command) {
                                ShortMessage.NOTE_ON -> {
                                    val pitch = message.data1
                                    val velocity = message.data2
                                    val startTick = event.tick
                                    if (velocity > 0) {
                                        for (j in i..<track.size()) {
                                            val event2 = track.get(j)
                                            val message2 = event2.message
                                            if (message2 is ShortMessage && message2.data1 == pitch) {
                                                val velocity2 = message2.data2
                                                if (message2.command == ShortMessage.NOTE_OFF || (message2.command == ShortMessage.NOTE_ON && velocity2 == 0)) {
                                                    val duration = event2.tick - startTick
                                                    notes.add(NoteData(startTick, duration, pitch, velocity))
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is MetaMessage -> {
                            when (message.metaMessageType) {
                                MetaMessageType.TIME_SIGNATURE -> {

                                }

                                MetaMessageType.END_OF_TRACK -> {
                                }

                                else -> {

                                }
                            }
                        }
                    }
                }
                tracksData.add(TrackData(notes, listOf(TimeSignatureData(0L, 4, 4))))
            }
            return MidiData(divisions, tracksData)
        }

        data class MidiData(val divisionsPerQuarterNote: Int, val tracksData: List<TrackData>)
        data class NoteData(val startTick: Long, val duration: Long, val pitch: Int, val velocity: Int)
        data class TimeSignatureData(val tick: Long, val numerator: Int, val denominator: Int)
        data class TrackData(val notesData: List<NoteData>, val timeSignatureData: List<TimeSignatureData>)
    }

}