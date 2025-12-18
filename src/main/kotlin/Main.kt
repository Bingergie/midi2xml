import core.MidiParser
import core.XmlWriter
import java.io.File


fun main(args: Array<String>) {
    val midiFile = File("src/test/resources/tsmidi/TS02-01 Who's licking it.mid")
//    val midiFile = File("src/test/resources/mary.mid")
    val midiParser = MidiParser()
    val score = midiParser.parse(midiFile)
    val xmlWriter = XmlWriter()
    xmlWriter.writeScoreToXml(score, File("_test/test_Dec18-6.musicxml"))
}