package com.revisionapp.domain.sync

/**
 * How a record payload is turned into the bytes that are stored.
 *
 * A seam, not a feature: nothing in the engine, the providers or the UI looks
 * inside a payload, they only carry strings around, so a codec can be swapped
 * without touching any of them. The id is written into the manifest so that a
 * location stored one way is never read another -- see
 * [SyncFailure.CodecMismatch].
 *
 * Only the identity codec ships today. Records sit in a folder the user chose
 * on a machine they own, and keeping them readable means a sync problem can be
 * diagnosed by opening a file. Encrypting becomes worth it the moment a
 * provider puts those bytes on someone else's hardware, which is the point at
 * which a real implementation goes behind this interface. See D43.
 */
interface PayloadCodec {

    /** Stable identifier, recorded in the manifest. Never changes once shipped. */
    val id: String

    fun encode(plain: String): String

    fun decode(stored: String): String
}

/** Stores payloads exactly as they were produced. */
object PlainPayloadCodec : PayloadCodec {

    const val CODEC_ID: String = "plain"

    override val id: String = CODEC_ID

    override fun encode(plain: String): String = plain

    override fun decode(stored: String): String = stored
}
