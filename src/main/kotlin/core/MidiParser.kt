package core

import java.io.File
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

fun getNotesFromMidi(file: File): MutableList<MutableList<MidiParser.ParsedNote>> {
    val staves = mutableListOf<MutableList<MidiParser.ParsedNote>>()
    val seq = MidiSystem.getSequence(file)
    for ((trackIndex, track) in seq.tracks.withIndex()) {
        val notes = ArrayList<MidiParser.ParsedNote>()
        println("track: $trackIndex has size ${track.size()}")
        for (i in 0..<track.size()) {
            val event: MidiEvent = track.get(i)
            val deltaTicks = event.tick
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
                                        if (message2.command == ShortMessage.NOTE_OFF ||
                                            (message2.command == ShortMessage.NOTE_ON && velocity2 == 0)
                                        ) {
                                            val duration = event2.tick - startTick
                                            notes.add(MidiParser.ParsedNote(startTick, duration, pitch, velocity))
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        staves.add(notes)
    }
    return staves
}

class MidiParser {
    private data class ParsingNote(val startTime: Long, val pitch: Int, val velocity: Int)
    data class ParsedNote(val startTime: Long, val duration: Long, val pitch: Int, val velocity: Int)

}