package com.example.trimvideopro

import com.example.trimvideopro.processor.VideoCompositionProcessor
import org.junit.Test

class MergeSizingTest {
    @Test fun normalizesLandscapeAndPortraitWithoutStretching() {
        check(VideoCompositionProcessor.outputSize(1920, 1080) == (1280 to 720))
        check(VideoCompositionProcessor.outputSize(1080, 1920) == (720 to 1280))
        check(VideoCompositionProcessor.outputSize(640, 480) == (640 to 480))
    }

    @Test fun encoderDimensionsAreEvenAndBounded() {
        for ((width, height) in listOf(4033 to 3025, 1081 to 1921, 1 to 1, 1000 to 1000)) {
            val (w, h) = VideoCompositionProcessor.outputSize(width, height)
            check(w >= 2 && h >= 2 && w % 2 == 0 && h % 2 == 0)
            check(maxOf(w, h) <= 1280 && minOf(w, h) <= 720)
        }
    }
}
