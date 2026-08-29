package me.pinfort.tsselect

class TsStatus(
    val pid: Int,
) {
    var lastContinuityCounter: Int = -1
    var first: Long = -1
    var total: Long = 0
    var error: Long = 0
    var drop: Long = 0
    var scrambling: Long = 0
    val lastPacket = ByteArray(188)
    var duplicateCount: Int = 0
}

class ResyncReport {
    var miss: Long = 0
    var sync: Long = 0
    var dropCount: Long = 0
    val dropPid = IntArray(4)
    val dropPos = LongArray(4)
}

class TsHeader {
    var sync: Int = 0
    var transportErrorIndicator: Int = 0
    var payloadUnitStartIndicator: Int = 0
    var transportPriority: Int = 0
    var pid: Int = 0
    var transportScramblingControl: Int = 0
    var adaptationFieldControl: Int = 0
    var continuityCounter: Int = 0
}

class AdaptationField {
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
    var ltwFlag: Int = 0
    var piecewiseRateFlag: Int = 0
    var seamlessSpliceFlag: Int = 0
    var ltwValidFlag: Int = 0
    var ltwOffset: Int = 0
    var piecewiseRate: Int = 0
    var spliceType: Int = 0
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
