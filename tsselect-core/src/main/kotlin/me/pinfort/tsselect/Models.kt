package me.pinfort.tsselect

// Per-PID running state used by TsDumpEngine.processPacket, one instance for
// each of the 8192 possible PIDs (most stay untouched). PidReport in
// Report.kt is the immutable snapshot taken from this at the end of a run.
internal class TsStatus(
    val pid: Int,
) {
    // -1 until the first packet on this PID, meaning "no continuity check
    // yet" - the CC/duplicate logic in processPacket is skipped entirely on
    // that first packet, since there is nothing to compare it against.
    var lastContinuityCounter: Int = -1
    var first: Long = -1
    var total: Long = 0
    var error: Long = 0
    var drop: Long = 0
    var scrambling: Long = 0

    // The previous packet's raw 188 bytes, kept only to detect whether a
    // same-CC retransmission is byte-identical to the packet it repeats (a
    // legitimate duplicate) or actually different data under an unchanged CC
    // (a drop) - see processPacket.
    val lastPacket = ByteArray(188)

    // How many consecutive packets on this PID have repeated the last CC
    // unchanged; a second one in a row is itself flagged as a drop.
    var duplicateCount: Int = 0
}

// Mutable working copy of one ResyncEntry, filled in as TsDumpEngine.run
// detects and recovers from a loss of packet alignment; report() converts
// the first RESYNC_LOG_MAX of these into immutable ResyncEntry values.
internal class ResyncReport {
    var miss: Long = 0
    var sync: Long = 0

    // The true count of drops attributed to this resync (see
    // TsDumpEngine.addDropInfo); may exceed dropPid/dropPos.size (4), which
    // only ever record the first 4.
    var dropCount: Long = 0
    val dropPid = IntArray(4)
    val dropPos = LongArray(4)
}

// The fixed 4-byte MPEG-2 TS packet header, reused across packets (one
// instance per engine/tsSelect run) and overwritten in place by
// TsHeader.parse - see Packet.kt.
internal class TsHeader {
    var sync: Int = 0
    var transportErrorIndicator: Int = 0
    var payloadUnitStartIndicator: Int = 0
    var transportPriority: Int = 0
    var pid: Int = 0
    var transportScramblingControl: Int = 0
    var adaptationFieldControl: Int = 0
    var continuityCounter: Int = 0
}

// The TS adaptation field, present when TsHeader.adaptationFieldControl's
// bit 1 is set. Reused across packets like TsHeader; AdaptationField.parse
// (Packet.kt) fills it in, or clear()s it on any malformed field or when the
// current packet has none at all.
internal class AdaptationField {
    var adaptationFieldLength: Int = 0

    // The C source calls this field discontinuity_counter, but it holds the
    // single-bit discontinuity_indicator ((p[0] >> 7) & 1).
    var discontinuityIndicator: Int = 0
    var randomAccessIndicator: Int = 0
    var elementaryStreamPriorityIndicator: Int = 0
    var pcrFlag: Int = 0
    var opcrFlag: Int = 0
    var splicingPointFlag: Int = 0
    var transportPrivateDataFlag: Int = 0
    var adaptationFieldExtensionFlag: Int = 0

    var programClockReference: Long = 0
    var originalProgramClockReference: Long = 0

    var spliceCountdown: Int = 0

    var transportPrivateDataLength: Int = 0

    var adaptationFieldExtensionLength: Int = 0

    // The three optional sub-fields of the adaptation_field_extension, each
    // gated by its own presence flag (see AdaptationField.parse):

    // legal_time_window: whether ltwOffset is present and valid right now.
    var ltwFlag: Int = 0

    // piecewise_rate: whether piecewiseRate is present.
    var piecewiseRateFlag: Int = 0

    // seamless_splice: whether spliceType/dtsNextAu are present.
    var seamlessSpliceFlag: Int = 0

    // Whether the decoder should start using ltwOffset now, versus the field
    // being present but not yet in effect.
    var ltwValidFlag: Int = 0

    // How many 27MHz clock periods, divided by 300, the current access unit
    // may be held past its nominal removal time - lets a splicer smooth out
    // small timing gaps at a legal_time_window splice point.
    var ltwOffset: Int = 0

    // The instantaneous bitrate (in 400 bit/s units) to use for input timing
    // during traffic shaped by a piecewise-constant-rate splice, distinct
    // from the stream's overall declared rate.
    var piecewiseRate: Int = 0

    // Which of the two access units at a seamless splice point this is,
    // encoded per ISO/IEC 13818-1 Table 2-19 (the value's low bit records
    // whether audio/video decoding may continue seamlessly across the splice).
    var spliceType: Int = 0

    // DTS of the next access unit after a seamless splice, in the same 90kHz
    // units as PCR; lets a decoder verify it can resume without a gap. See
    // AdaptationField.parse's seamless_splice branch for the bit layout.
    var dtsNextAu: Long = 0

    fun clear() {
        adaptationFieldLength = 0
        discontinuityIndicator = 0
        randomAccessIndicator = 0
        elementaryStreamPriorityIndicator = 0
        pcrFlag = 0
        opcrFlag = 0
        splicingPointFlag = 0
        transportPrivateDataFlag = 0
        adaptationFieldExtensionFlag = 0
        programClockReference = 0
        originalProgramClockReference = 0
        spliceCountdown = 0
        transportPrivateDataLength = 0
        adaptationFieldExtensionLength = 0
        ltwFlag = 0
        piecewiseRateFlag = 0
        seamlessSpliceFlag = 0
        ltwValidFlag = 0
        ltwOffset = 0
        piecewiseRate = 0
        spliceType = 0
        dtsNextAu = 0
    }
}
