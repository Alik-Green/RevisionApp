package com.revisionapp.platform

/**
 * A directory tree the app may read and write, addressed by relative paths.
 *
 * The whole of the platform-specific part of folder sync. `commonMain` gets an
 * interface with no `File`, no `Uri` and no `ContentResolver` in it, so
 * [com.revisionapp.data.sync.LocalFolderSyncProvider] can be written -- and
 * tested -- once, against a desktop `java.io.File` tree on the JVM and a SAF
 * document tree on Android.
 *
 * Paths use `/` as the separator on every platform and are always relative to
 * the chosen root; an implementation must refuse to resolve one that climbs out
 * of it.
 */
interface SyncStorage {

    /** Shown on the sync settings card, e.g. the folder's name. */
    val label: String

    /** True if writing a temp file and renaming it is atomic on this backend. */
    val supportsAtomicRename: Boolean

    /**
     * Every file below [prefix], as relative paths, in a stable order.
     *
     * Returns an empty list for a prefix that does not exist rather than
     * throwing: on a first run nothing exists yet, and that is not an error.
     */
    suspend fun list(prefix: String): List<String>

    /** The bytes at [path], or null if there is no such file. */
    suspend fun readBytes(path: String): ByteArray?

    /**
     * Replaces [path] with [bytes], creating parent directories as needed.
     *
     * Implementations that can should write to a sibling temp file and rename
     * it, so that a reader never sees a half-written record. That is what makes
     * an interrupted sync recoverable instead of corrupting.
     */
    suspend fun writeBytes(path: String, bytes: ByteArray)

    suspend fun delete(path: String)

    suspend fun exists(path: String): Boolean = readBytes(path) != null

    /**
     * Adds [bytes] to the end of [path], creating it if necessary.
     *
     * The default reads and rewrites, which is not safe under concurrent
     * writers -- and does not need to be, because the only thing appended to is
     * a review-log segment, and segments are per device. Exactly one device
     * ever writes a given segment, so a read-modify-write cannot lose another
     * device's lines.
     */
    suspend fun appendBytes(path: String, bytes: ByteArray) {
        val existing = readBytes(path) ?: ByteArray(0)
        writeBytes(path, existing + bytes)
    }
}
