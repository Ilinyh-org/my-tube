package org.mytube.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoFormatTest {
    @Test
    fun `format version constant is pinned`() {
        assertEquals(1, CRYPTO_FORMAT_VERSION)
    }
}
