package core

import javax.sound.midi.MetaMessage

enum class MetaMessageType(val typeByte: Int) {
    SEQUENCE_NUMBER(0x00),
    TEXT(0x01),
    COPYRIGHT_NOTICE(0x02),
    TRACK_NAME(0x03),
    INSTRUMENT_NAME(0x04),
    LYRICS(0x05),
    MARKER(0x06),
    CUE_POINT(0x07),
    PROGRAM_NAME(0x08),
    DEVICE_NAME(0x09),
    MIDI_CHANNEL_PREFIX(0x20),
    END_OF_TRACK(0x2F),
    SET_TEMPO(0x51),
    TIME_SIGNATURE(0x58),
    KEY_SIGNATURE(0x59),
    SEQUENCER_SPECIFIC(0x7F);

    companion object {
        private val map = entries.associateBy { it.typeByte }

        fun fromType(typeByte: Int): MetaMessageType? {
            return map[typeByte]
        }
    }
}

val MetaMessage.metaMessageType: MetaMessageType?
    get() {
        return MetaMessageType.fromType(this.type and 0xFF)
    }