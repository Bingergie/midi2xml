import core.MidiParser
import core.ScoreDesigner
import core.XmlWriter
import java.io.File


fun main(args: Array<String>) {
//    val midiFile = File("src/test/resources/tsmidi/TS02-01 Who's licking it.mid")
//    val midiFile = File("src/test/resources/mary.mid")
    val midiFile = File("src/test/resources/happy-birthday-to-you-c-major.mid")
//    val midiFile = File("src/test/resources/tsmidi/TS05-01 It's Magic #1.MID")
    val outputFile = File("_test/tes_Jan5-7.musicxml")
//    val midiFile = File(args[0])
//    val outputFile = File(args[1])
    println("Parsing...")
    val midiParser = MidiParser()
    val score = midiParser.parse(midiFile)
    println("Designing...")
    val scoreDesigner = ScoreDesigner()
    scoreDesigner.transformScore(score)
    println("Exporting...")
    val xmlWriter = XmlWriter()
    xmlWriter.writeScoreToXml(score, outputFile)
}