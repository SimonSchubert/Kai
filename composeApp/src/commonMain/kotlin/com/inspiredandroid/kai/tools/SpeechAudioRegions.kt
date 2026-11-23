package com.inspiredandroid.kai.tools

internal data class SpeechSampleRange(val start: Int, val endExclusive: Int) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }
}

/**
 * Adds context around detected speech and merges overlapping regions so a short
 * pause cannot duplicate samples when the padded regions are concatenated.
 */
internal fun paddedSpeechRanges(
    regions: List<SpeechSampleRange>,
    totalSamples: Int,
    preRollSamples: Int,
    postRollSamples: Int,
): List<SpeechSampleRange> {
    require(totalSamples >= 0)
    require(preRollSamples >= 0)
    require(postRollSamples >= 0)
    if (totalSamples == 0 || regions.isEmpty()) return emptyList()

    val sorted = regions.mapNotNull { region ->
        val start = (region.start - preRollSamples).coerceAtLeast(0)
        val end = (region.endExclusive + postRollSamples).coerceAtMost(totalSamples)
        if (end > start) SpeechSampleRange(start, end) else null
    }.sortedBy { it.start }

    if (sorted.isEmpty()) return emptyList()
    val merged = mutableListOf<SpeechSampleRange>()
    var currentStart = sorted.first().start
    var currentEnd = sorted.first().endExclusive
    for (region in sorted.drop(1)) {
        if (region.start <= currentEnd) {
            currentEnd = maxOf(currentEnd, region.endExclusive)
        } else {
            merged += SpeechSampleRange(currentStart, currentEnd)
            currentStart = region.start
            currentEnd = region.endExclusive
        }
    }
    merged += SpeechSampleRange(currentStart, currentEnd)
    return merged
}

internal fun concatenateSpeechRegions(
    samples: FloatArray,
    regions: List<SpeechSampleRange>,
): FloatArray {
    val valid = regions.filter { it.start >= 0 && it.endExclusive <= samples.size && it.endExclusive > it.start }
    val output = FloatArray(valid.sumOf { it.endExclusive - it.start })
    var destination = 0
    for (region in valid) {
        val count = region.endExclusive - region.start
        samples.copyInto(output, destination, region.start, region.endExclusive)
        destination += count
    }
    return output
}
