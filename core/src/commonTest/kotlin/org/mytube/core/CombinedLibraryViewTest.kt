package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinedLibraryViewTest {
    private fun track(lib: LibraryId, title: String, artist: String? = null, album: String? = null, genre: String? = null, durationMs: Long? = null) =
        Track(TrackId.generate(), lib, TrackMetadata(title = title, artist = artist, album = album, genre = genre, durationMs = durationMs))

    @Test
    fun `filterByLibrary keeps library association`() {
        val libA = LibraryId.generate()
        val libB = LibraryId.generate()
        val a = track(libA, "A")
        val b = track(libB, "B")
        val view = CombinedLibraryView(listOf(a, b))
        assertEquals(listOf(a), view.filterByLibrary(libA))
    }

    @Test
    fun `artists albums genres aggregate across libraries`() {
        val libA = LibraryId.generate()
        val libB = LibraryId.generate()
        val view = CombinedLibraryView(
            listOf(
                track(libA, "1", artist = "X", album = "Al1", genre = "Rock"),
                track(libB, "2", artist = "Y", album = "Al2", genre = "Jazz"),
                track(libB, "3", artist = "X", album = "Al1", genre = "Rock"),
            )
        )
        assertEquals(setOf("X", "Y"), view.artists())
        assertEquals(setOf("Al1", "Al2"), view.albums())
        assertEquals(setOf("Rock", "Jazz"), view.genres())
    }

    @Test
    fun `duplicate candidates group by title artist album duration`() {
        val libA = LibraryId.generate()
        val libB = LibraryId.generate()
        val a = track(libA, "Song", artist = "A", album = "L", durationMs = 100)
        val aCopy = track(libB, "Song", artist = "A", album = "L", durationMs = 100)
        val other = track(libA, "Song", artist = "A", album = "L", durationMs = 999)
        val view = CombinedLibraryView(listOf(a, other, aCopy))
        val groups = view.duplicateCandidates()
        assertEquals(1, groups.size)
        assertTrue(groups[0].containsAll(listOf(a, aCopy)))
    }

    @Test
    fun `identical tracks are not merged`() {
        val libA = LibraryId.generate()
        val libB = LibraryId.generate()
        val a = track(libA, "Song")
        val b = track(libB, "Song")
        val view = CombinedLibraryView(listOf(a, b))
        assertEquals(listOf(a, b), view.filterByLibrary(libA) + view.filterByLibrary(libB))
        assertEquals(1, view.filterByLibrary(libA).size)
        assertEquals(1, view.filterByLibrary(libB).size)
    }
}
