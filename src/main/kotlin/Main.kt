import java.io.File



fun exportNotesToXML(notes: List<List<musicxml.Note>>): String {
    return ""
}


fun main(args: Array<String>) {
    val midiInput: File = File("src/test/resources/mary.mid")
    print(core.getNotesFromMidi(midiInput))

}