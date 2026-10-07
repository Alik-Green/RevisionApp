package com.revisionapp.data

import kotlinx.serialization.json.Json

/** The one [Json] configuration the app uses, for both content and storage. */
object AppJson {
    val instance: Json = Json {
        // Content packs are versioned separately from the app, so an older client
        // must still be able to read a pack that gained a field.
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }
}
