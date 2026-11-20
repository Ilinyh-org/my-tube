package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaylistTest {
    private fun track(libraryId: LibraryId = LibraryId.generate()) =
        Track(TrackId.generate(), libraryId, TrackMetadata(title = "t"))

    @Test
    fun `rejects blank name`() {
        assertFailsWith<IllegalArgumentException> { Playlist(PlaylistId.generate(), LibraryId.generate(), "") }
    }

    @Test
    fun `addTrack rejects track from another library`() {
        val lib = LibraryId.generate()
        val playlist = Playlist(PlaylistId.generate(), lib, "Mix")
        val foreign = track()
        assertFailsWith<IllegalArgumentException> { playlist.addTrack(foreign) }
    }

    @Test
    fun `addTrack appends in order`() {
        val lib = LibraryId.generate()
        val playlist = Playlist(PlaylistId.generate(), lib, "Mix")
        val a = track(lib)
        val b = track(lib)
        val updated = playlist.addTrack(a).addTrack(b)
        assertEquals(listOf(a.id, b.id), updated.trackIds)
    }

    @Test
    fun `removeTrack preserves order of remaining`() {
        val lib = LibraryId.generate()
        val a = track(lib)
        val b = track(lib)
        val c = track(lib)
        val playlist = Playlist(PlaylistId.generate(), lib, "Mix", trackIds = listOf(a.id, b.id, c.id))
        assertEquals(listOf(a.id, c.id), playlist.removeTrack(b.id).trackIds)
    }

    @Test
    fun `moveTrack reorders within range and rejects invalid`() {
        val lib = LibraryId.generate()
        val a = track(lib)
        val b = track(lib)
        val c = track(lib)
        val playlist = Playlist(PlaylistId.generate(), lib, "Mix", trackIds = listOf(a.id, b.id, c.id))
        assertEquals(listOf(b.id, c.id, a.id), playlist.moveTrack(a.id, 2).trackIds)
        assertFailsWith<IllegalArgumentException> { playlist.moveTrack(a.id, 5) }
        assertFailsWith<IllegalArgumentException> { playlist.moveTrack(TrackId.generate(), 0) }
    }
}
