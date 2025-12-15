package core

import java.io.File
import javax.sound.midi.*


class MidiParser {
    var currentScore: Score? = null
    var currentStaff: Staff? = null

    fun parse(file: File): Score {
        val sequence = MidiSystem.getSequence(file)
        require(sequence.divisionType == Sequence.PPQ) { TODO("PPQ not supported yet") }
        val ticksPerQuarterNote = sequence.resolution
        currentScore = Score(ticksPerQuarterNote)
        for (track in sequence.tracks) {
            currentStaff = Staff()
            for (i in 0..<track.size()) {
                val event: MidiEvent = track.get(i)
                when (val message = event.message) {
                    is ShortMessage -> this.handleShortMessage(event.tick, message)

                    is MetaMessage -> this.handleMetaMessage(event.tick, message)

                    is SysexMessage -> {

                    }
                }
            }
        }
        return currentScore!!
    }

    private fun handleMetaMessage(tick: Long, message: MetaMessage) {
        when (message.metaMessageType) {
            MetaMessageType.TIME_SIGNATURE -> this.handleTimeSignature(tick, message)
            MetaMessageType.END_OF_TRACK -> {}
            MetaMessageType.KEY_SIGNATURE -> {}
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
        }
    }

    private fun handleTimeSignature(tick: Long, message: MetaMessage) {
        val numerator = message.data[0].toInt()
        val denominator = 1 shl message.data[1].toInt()
        val timeSignature = TimeSignature(tick, numerator, denominator)
        this.currentScore!!.conductorStaff.add(timeSignature)
        println("staff: ${timeSignature.numerator} / ${timeSignature.denominator}")
    }

    private fun handleShortMessage(tick: Long, message: ShortMessage) {
        when (message.command) {
            ShortMessage.NOTE_ON -> this.handleNoteEvent(tick, message)
            ShortMessage.NOTE_OFF -> this.handleNoteEvent(tick, message)
            ShortMessage.POLY_PRESSURE -> {} // note aftertouch [note number, aftertouch value]
            ShortMessage.CONTROL_CHANGE -> {}
            ShortMessage.PROGRAM_CHANGE -> {}
            ShortMessage.CHANNEL_PRESSURE -> {} // channel aftertouch
            ShortMessage.PITCH_BEND -> {} // pitch bend
        }
    }

    private val tempNotes =
        HashMap<Pair<Int, Int>, MutableList<Pair<Long, Int>>>() // <channel, pitch> -> stack(<startTick, velocity>)

    private fun handleNoteEvent(tick: Long, message: ShortMessage) {
        val channel = message.channel
        val pitch = message.data1
        val velocity = message.data2
        if (message.command == ShortMessage.NOTE_OFF || velocity == 0) {
            this.handleNoteEnd(channel, pitch, tick)
        } else {
            this.handleNoteStart(channel, pitch, velocity, tick)
        }
    }

    private fun handleNoteStart(channel: Int, pitch: Int, velocity: Int, startTick: Long) {
        val startTicks = tempNotes.getOrPut(channel to pitch) { mutableListOf() }
        startTicks.add(startTick to velocity)
        return
    }

    private fun handleNoteEnd(channel: Int, pitch: Int, endTick: Long) {
        // remove note from tempNotes
        val noteStartData = tempNotes[channel to pitch]
            ?: throw IllegalStateException("No tempNote found for channel $channel and pitch $pitch")
        val (startTick, velocity) = noteStartData.removeFirst()
        val duration = endTick - startTick
        this.currentStaff!!.add(Note(startTick, pitch, duration, velocity))
    }
}