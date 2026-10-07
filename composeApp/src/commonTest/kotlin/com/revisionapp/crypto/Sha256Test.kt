package com.revisionapp.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Vectors generated with Python's `hashlib.sha256` and pinned here. The lengths
 * around 55, 56, 63, 64, 65, 119 and 120 bytes are the padding boundaries where
 * a hand-written SHA-256 is most likely to be wrong.
 */
class Sha256Test {

    @Test
    fun matchesTheStandardVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc"),
        )
        assertEquals(
            "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
            Sha256.hex("The quick brown fox jumps over the lazy dog"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        assertEquals(
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            Sha256.hex("a"),
        )
    }

    @Test
    fun handlesEveryPaddingBoundary() {
        assertEquals("9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318", Sha256.hex("a".repeat(55)))
        assertEquals("b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a", Sha256.hex("a".repeat(56)))
        assertEquals("7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34", Sha256.hex("a".repeat(63)))
        assertEquals("ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb", Sha256.hex("a".repeat(64)))
        assertEquals("635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0", Sha256.hex("a".repeat(65)))
        assertEquals("31eba51c313a5c08226adf18d4a359cfdfd8d2e816b13f4af952f7ea6584dcfb", Sha256.hex("a".repeat(119)))
        assertEquals("2f3d335432c70b580af0e8e1b3674a7c020d683aa5f73aaaedfdc55af904c21c", Sha256.hex("a".repeat(120)))
    }

    @Test
    fun hashesTheUtf8EncodingNotTheCharacterCount() {
        assertEquals(
            "ce3da50cab65e603f77434f3c6b2d29bbd14ac269a70c39fea3971f5035aef7a",
            Sha256.hex("unicode: \u03B8 = 2\u03C0, v = 3.2 m s\u207B\u00B9, \u00E9\u00E8\u00FC"),
        )
    }

    @Test
    fun hashesJsonExactlyAsTheContentValidatorDoes() {
        val payload = "{\"packId\":\"demo\",\"version\":1}\n{\"topic\":\"demo\",\"cards\":[]}\n"
        assertEquals("b20884dc818599dab01eb52943c907d24828f9978a32217430949896637c7a9f", Sha256.hex(payload))
    }

    @Test
    fun digestsAreThirtyTwoBytesAndHexIsLowercase() {
        val digest = Sha256.digest("abc".encodeToByteArray())

        assertEquals(32, digest.size)
        val hex = Sha256.hex(digest)
        assertEquals(64, hex.length)
        assertEquals(hex, hex.lowercase())
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun oneBitOfDifferenceChangesTheWholeDigest() {
        assertNotEquals(Sha256.hex("pack v1"), Sha256.hex("pack v2"))
    }

    @Test
    fun hashingIsDeterministic() {
        val payload = "some content"
        assertEquals(Sha256.hex(payload), Sha256.hex(payload))
    }
}
