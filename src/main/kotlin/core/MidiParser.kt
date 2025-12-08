package core

import java.io.File
import javax.sound.midi.*


class MidiParser {

    private val notes = HashMap<Long, NoteData>() // startTick -> NoteData
    private val tempNotes = HashMap<Pair<Int, Int>, MutableList<Pair<Long, Int>>>() // <channel, pitch> -> stack(<startTick, velocity>)

    private fun handleNoteStart(channel: Int, pitch: Int, velocity: Int, startTick: Long): Unit {
        val startTicks = tempNotes.getOrPut(channel to pitch) { mutableListOf() }
        startTicks.add(startTick to velocity)
        return
    }

    private fun handleNoteEnd(channel: Int, pitch: Int, endTick: Long): Unit {
        // remove note from tempNotes
        val noteStartData = tempNotes[channel to pitch]
            ?: throw IllegalStateException("No tempNote found for channel $channel and pitch $pitch")
        val (startTick, velocity) = noteStartData.removeFirst()
        notes[startTick] = (NoteData(pitch, velocity, endTick - startTick))
        return
    }

    fun getNotesFromMidi(file: File): MidiData {
        val sequence = MidiSystem.getSequence(file)
        require(sequence.divisionType == Sequence.PPQ) { TODO("PPQ not supported yet") }
        val divisions = sequence.resolution // ticks per beat
        val tracksData = mutableListOf<StaffData>()
        mutableListOf<TimeSignatureData>()
        for (track in sequence.tracks) {

            for (i in 0..<track.size()) {
                val event: MidiEvent = track.get(i)
                val message = event.message
                when (message) {
                    is ShortMessage -> when (message.command) {
                        ShortMessage.NOTE_ON -> {
                            val pitch = message.data1
                            val velocity = message.data2
                            val channel = message.channel
                            val tick = event.tick
                            if (velocity > 0) {
                                // Handle Note start
                                this.handleNoteStart(channel, pitch, velocity, tick)
                            } else { // velocity == 0
                                // Handle note end
                                this.handleNoteEnd(channel, pitch, tick)
                            }
                        }

                        ShortMessage.NOTE_OFF -> {
                            val pitch = message.data1
                            val channel = message.channel
                            val endTick = event.tick
                            this.handleNoteEnd(channel, pitch, endTick)
                        }
                        ShortMessage.POLY_PRESSURE -> {} // note aftertouch [note number, aftertouch value]
                        ShortMessage.CONTROL_CHANGE -> {}
                        ShortMessage.PROGRAM_CHANGE -> {}
                        ShortMessage.CHANNEL_PRESSURE -> {} // channel aftertouch
                        ShortMessage.PITCH_BEND -> {
                            println("yooo its a pitch bend!!!!")
                        }
                    }

                    is MetaMessage -> when (message.metaMessageType) {
                        MetaMessageType.TIME_SIGNATURE -> {

                        }

                        MetaMessageType.END_OF_TRACK -> {
                        }

                        MetaMessageType.KEY_SIGNATURE -> {

                        }


                        MetaMessageType.SEQUENCE_NUMBER -> {}
                        MetaMessageType.TEXT -> {}
                        MetaMessageType.COPYRIGHT_NOTICE -> {}
                        MetaMessageType.TRACK_NAME -> {}
                        MetaMessageType.INSTRUMENT_NAME -> {}
                        MetaMessageType.LYRICS -> {}
                        MetaMessageType.MARKER -> {}
                        MetaMessageType.CUE_POINT -> {}
                        MetaMessageType.PROGRAM_NAME -> {}
                        MetaMessageType.DEVICE_NAME -> {}
                        MetaMessageType.MIDI_CHANNEL_PREFIX -> {}
                        MetaMessageType.SET_TEMPO -> {}
                        MetaMessageType.SEQUENCER_SPECIFIC -> {}
                        null -> println("Warning: null MetaMessage received!")
                        else -> {

                        }
                    }

                    is SysexMessage -> {

                    }
                }
            }
            tracksData.add(StaffData(notes, listOf(TimeSignatureData(4, 4))))
        }
        return MidiData(divisions, tracksData)
    }

    data class MidiData(val divisionsPerQuarterNote: Int, val tracksData: List<StaffData>)
    abstract class StaffObject()
    data class NoteData(val pitch: Int, val velocity: Int, val duration: Long)
    data class TimeSignatureData(val numerator: Int, val denominator: Int)
    class StaffData(val notesData: HashMap<Long, NoteData>, val timeSignatureData: List<TimeSignatureData>) {

    }

}