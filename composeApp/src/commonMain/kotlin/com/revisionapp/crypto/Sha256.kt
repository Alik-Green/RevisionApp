package com.revisionapp.crypto

/**
 * SHA-256 in pure Kotlin.
 *
 * Content packs are verified by hash before they are imported, and
 * `java.security.MessageDigest` is JVM-only — using it would either leak a
 * platform type into common code or need an expect/actual pair for eighty lines
 * of portable integer arithmetic. See docs/DECISIONS.md D8.
 */
object Sha256 {

    /** Lowercase hex digest of the UTF-8 encoding of [input]. */
    fun hex(input: String): String = hex(digest(input.encodeToByteArray()))

    fun hex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xff
            builder.append(HEX_DIGITS[unsigned shr 4])
            builder.append(HEX_DIGITS[unsigned and 0x0f])
        }
        return builder.toString()
    }

    fun digest(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * BITS_PER_BYTE
        val paddedLength = ((message.size + PADDING_OVERHEAD + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE
        val padded = ByteArray(paddedLength)
        message.copyInto(padded)
        padded[message.size] = TERMINATOR
        for (index in 0 until LENGTH_BYTES) {
            padded[paddedLength - 1 - index] = (bitLength ushr (BITS_PER_BYTE * index)).toByte()
        }

        val hash = INITIAL_HASH.copyOf()
        val schedule = IntArray(ROUNDS)

        var offset = 0
        while (offset < paddedLength) {
            for (index in 0 until 16) {
                val start = offset + index * BYTES_PER_WORD
                schedule[index] = ((padded[start].toInt() and 0xff) shl 24) or
                    ((padded[start + 1].toInt() and 0xff) shl 16) or
                    ((padded[start + 2].toInt() and 0xff) shl 8) or
                    (padded[start + 3].toInt() and 0xff)
            }
            for (index in 16 until ROUNDS) {
                val twoBack = schedule[index - 2]
                val fifteenBack = schedule[index - 15]
                val sigma0 = fifteenBack.rotateRight(7) xor fifteenBack.rotateRight(18) xor (fifteenBack ushr 3)
                val sigma1 = twoBack.rotateRight(17) xor twoBack.rotateRight(19) xor (twoBack ushr 10)
                schedule[index] = schedule[index - 16] + sigma0 + schedule[index - 7] + sigma1
            }

            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]

            for (round in 0 until ROUNDS) {
                // Σ1 and Ch act on e, f and g; Σ0 and Maj act on a, b and c.
                val bigSigma1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choose = (e and f) xor (e.inv() and g)
                val temp1 = h + bigSigma1 + choose + ROUND_CONSTANTS[round] + schedule[round]
                val bigSigma0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = bigSigma0 + majority

                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h

            offset += BLOCK_SIZE
        }

        val digest = ByteArray(DIGEST_BYTES)
        for (index in 0 until 8) {
            val word = hash[index]
            digest[index * 4] = (word ushr 24).toByte()
            digest[index * 4 + 1] = (word ushr 16).toByte()
            digest[index * 4 + 2] = (word ushr 8).toByte()
            digest[index * 4 + 3] = word.toByte()
        }
        return digest
    }

    private const val HEX_DIGITS = "0123456789abcdef"
    private const val BLOCK_SIZE = 64
    private const val DIGEST_BYTES = 32
    private const val ROUNDS = 64
    private const val BYTES_PER_WORD = 4
    private const val BITS_PER_BYTE = 8
    private const val LENGTH_BYTES = 8
    private const val PADDING_OVERHEAD = 9
    private const val TERMINATOR: Byte = 0x80.toByte()

    private val INITIAL_HASH = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )

    private val ROUND_CONSTANTS = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
}
