import core.MidiParser
import core.ScoreDesigner
import core.XmlWriter
import java.io.File


fun main(args: Array<String>) {
//    val midiFile = File("src/test/resources/tsmidi/TS02-01 Who's licking it.mid")
//    val midiFile = File("src/test/resources/mary.mid")
//    val midiFile = File("src/test/resources/happy-birthday-to-you-c-major.mid")
//    val midiFile = File("src/test/resources/tsmidi/TS05-01 It's Magic #1.MID")
    print(args.toString())
    val midiFile = File(args[0])
    val midiParser = MidiParser()
    val score = midiParser.parse(midiFile)
    val scoreDesigner = ScoreDesigner()
    scoreDesigner.transformScore(score)
    val xmlWriter = XmlWriter()
    xmlWriter.writeScoreToXml(score, File(args[1]))
}