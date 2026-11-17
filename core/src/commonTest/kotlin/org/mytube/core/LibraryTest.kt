package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LibraryIdTest {
    @Test
    fun `generated id is 32 lowercase hex chars`() {
        val id = LibraryId.generate()
        assertTrue(id.value.matches(Regex("^[0-9a-f]{32}$")), "unexpected id: ${id.value}")
    }

    @Test
    fun `generated ids are unique`() {
        assertNotEquals(LibraryId.generate(), LibraryId.generate())
    }

    @Test
    fun `rejects malformed identifiers`() {
        assertFailsWith<IllegalArgumentException> { LibraryId("not-hex") }
        assertFailsWith<IllegalArgumentException> { LibraryId("ABCDEF0123456789ABCDEF0123456789") } // uppercase
        assertFailsWith<IllegalArgumentException> { LibraryId("0123456789abcdef") } // too short
    }

    @Test
    fun `accepts well formed identifier`() {
        assertEquals("000102030405060708090a0b0c0d0e0f", LibraryId("000102030405060708090a0b0c0d0e0f").value)
    }
}

class LibraryTest {
    @Test
    fun `rejects blank name`() {
        assertFailsWith<IllegalArgumentException> { Library(LibraryId.generate(), " ") }
    }

    @Test
    fun `two libraries with same name remain independent by id`() {
        val a = Library(LibraryId.generate(), "Personal")
        val b = Library(LibraryId.generate(), "Personal")
        assertNotEquals(a.id, b.id)
    }
}
