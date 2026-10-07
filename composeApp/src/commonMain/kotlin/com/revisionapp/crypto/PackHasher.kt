package com.revisionapp.crypto

/**
 * The pack hash the manifest promises and the content validator enforces.
 *
 * It covers `pack.json` *and* every card file, so a changed card cannot slip
 * through as "unchanged". Files are concatenated in sorted filename order with a
 * single newline after each one; `tools/validate` on the `content` branch
 * computes exactly the same thing. See docs/DECISIONS.md D11.
 */
object PackHasher {

    /**
     * @param packJson the raw text of `pack.json`, byte for byte as downloaded.
     * @param cardFiles raw card file text keyed by file name.
     */
    fun hash(packJson: String, cardFiles: Map<String, String>): String = Sha256.hex(payload(packJson, cardFiles))

    /** The exact string that is hashed, exposed so tests can pin the layout. */
    fun payload(packJson: String, cardFiles: Map<String, String>): String {
        val builder = StringBuilder()
        builder.append(packJson).append('\n')
        for (name in cardFiles.keys.sorted()) {
            builder.append(cardFiles.getValue(name)).append('\n')
        }
        return builder.toString()
    }
}
