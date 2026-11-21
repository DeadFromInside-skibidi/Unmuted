package com.unmuted.app

import com.unmuted.app.core.buildSyncedCaptionSegments
import com.unmuted.app.data.VoicePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun wordSyncedCaptionSegments_coverFullDurationWithWordTimings() {
        val segments = buildSyncedCaptionSegments(
            transcript = "Open debate thrives when voices stay unmuted and fearless.",
            durationMs = 6000L
        )
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { it.words.isNotEmpty() })
        assertEquals(0L, segments.first().startMs)
        assertEquals(6000L, segments.last().endMs)
        val firstWord = segments.first().words.first()
        assertTrue(firstWord.endMs > firstWord.startMs)
    }

    @Test
    fun downvoteVeilThreshold_evaluatesNetDownvotesAccurately() {
        val post = VoicePost(id = "p1", likeCount = 2, dislikeCount = 8)
        assertEquals(6L, post.netDownvotes())
        assertTrue(post.isHeavilyDownvoted(threshold = 5))
        assertFalse(post.isHeavilyDownvoted(threshold = 10))
    }
}
