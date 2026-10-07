package com.revisionapp.ui

/**
 * Features that are designed for but not built yet.
 *
 * Notes will live in the same topic tree and use the same tag filters as cards, so
 * the Library already shows a `Cards | Notes` segmented control with Notes
 * disabled. Showing the shape now means the screen does not have to be relaid out
 * when notebooks land, and [com.revisionapp.domain.model.LibraryItem] and
 * [com.revisionapp.domain.usecase.SearchSection] are already sealed so that adding
 * them is one case each rather than a rewrite.
 */
object FeatureFlags {

    /** Off: no notebook model, storage or editor exists yet. */
    const val NOTES_ENABLED: Boolean = false
}
