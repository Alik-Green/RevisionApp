package com.revisionapp.domain.model

/**
 * One row in the Library, and one hit in a search.
 *
 * Sealed so that adding notebooks later means adding one case here and letting the
 * compiler list every `when` that has to handle it, instead of hunting down the
 * screens that quietly assumed a row was always a topic or a card.
 */
sealed interface LibraryItem {

    /** Stable identity, used as a lazy-list key across recompositions. */
    val key: String

    /** What the row is called, for display and for search matching. */
    val title: String

    /** True when the user may rename, move or delete it. */
    val isEditable: Boolean

    /** A sub-topic of the location being viewed: a folder in the explorer. */
    data class TopicFolder(val node: TopicNode) : LibraryItem {
        override val key: String get() = "topic:" + node.id.value
        override val title: String get() = node.name
        override val isEditable: Boolean get() = node.isEditable
    }

    /** A card that lives directly in the location being viewed. */
    data class CardItem(val card: Card) : LibraryItem {
        override val key: String get() = "card:" + card.id.value
        override val title: String get() = card.front
        override val isEditable: Boolean get() = card.isEditable
    }
}
