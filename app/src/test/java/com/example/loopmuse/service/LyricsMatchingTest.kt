package com.example.loopmuse.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsMatchingTest {
    private fun candidate(
        title: String,
        artist: String,
        duration: Int = 210,
        album: String = ""
    ) = LyricsCandidate(1L, title, artist, album, duration, "lyrics", "")

    @Test
    fun koreanNamesInsideBilingualMetadataCanMatchAutomatically() {
        val results = rankLyricsCandidates(
            "밤편지", "아이유", "", 210_000L,
            listOf(candidate("Through the Night (밤편지)", "IU (아이유)"))
        )

        assertEquals("Through the Night (밤편지)",
            chooseAutomaticLyrics("밤편지", "아이유", "", 210_000L, results)?.title)
    }

    @Test
    fun ambiguousVersionsRemainForTheUserToChoose() {
        val results = rankLyricsCandidates(
            "모든 날, 모든 순간", "폴킴", "", 210_000L,
            listOf(
                candidate("모든 날, 모든 순간", "폴킴", album = "Original"),
                candidate("모든 날, 모든 순간", "폴킴", album = "Compilation")
            )
        )

        assertEquals(2, results.size)
        assertNull(chooseAutomaticLyrics("모든 날, 모든 순간", "폴킴", "", 210_000L, results))
    }

    @Test
    fun artistMismatchAndWrongDurationDoNotSaveAutomatically() {
        val artistMismatch = rankLyricsCandidates(
            "너의 의미 (Feat. 김창완)", "아이유", "", 195_000L,
            listOf(candidate("Meaning of you (너의 의미 (feat. 김창완))", "IU", 196))
        )
        assertEquals(1, artistMismatch.size)
        assertNull(chooseAutomaticLyrics("너의 의미 (Feat. 김창완)", "아이유", "", 195_000L,
            artistMismatch))

        val wrongDuration = rankLyricsCandidates(
            "밤편지", "아이유", "", 195_000L,
            listOf(candidate("밤편지", "아이유", 250))
        )
        assertNull(chooseAutomaticLyrics("밤편지", "아이유", "", 195_000L, wrongDuration))
    }
}
