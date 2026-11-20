package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrackIdTest {
    @Test
    fun `generated id is 32 lowercase hex chars`() {
        assertTrue(TrackId.generate().value.matches(Regex("^[0-9a-f]{32}$")))
    }

    @Test
    fun `generated ids are unique`() {
        assertNotEquals(TrackId.generate(), TrackId.generate())
    }

    @Test
    fun `rejects malformed identifiers`() {
        assertFailsWith<IllegalArgumentException> { TrackId("zzzz") }
    }
}

class TrackMetadataTest {
    @Test
    fun `rejects blank title`() {
        assertFailsWith<IllegalArgumentException> { TrackMetadata(title = "  ") }
    }

    @Test
    fun `rejects invalid year duration track number`() {
        assertFailsWith<IllegalArgumentException> { TrackMetadata(title = "t", year = 0) }
        assertFailsWith<IllegalArgumentException> { TrackMetadata(title = "t", durationMs = -1) }
        assertFailsWith<IllegalArgumentException> { TrackMetadata(title = "t", trackNumber = -1) }
    }

    @Test
    fun `optional fields default to null or empty`() {
        val m = TrackMetadata(title = "Song")
        assertEquals(null, m.artist)
        assertEquals(emptyMap(), m.customTags)
    }
}

class TrackTest {
    @Test
    fun `identical tracks in different libraries stay distinct`() {
        val metadata = TrackMetadata(title = "Same Song", artist = "A", album = "B", durationMs = 1000)
        val a = Track(TrackId.generate(), LibraryId.generate(), metadata)
        val b = Track(TrackId.generate(), LibraryId.generate(), metadata)
        assertNotEquals(a, b)
        assertNotEquals(a.libraryId, b.libraryId)
    }
}
