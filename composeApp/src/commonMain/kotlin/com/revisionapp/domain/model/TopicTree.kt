package com.revisionapp.domain.model

/** A topic plus its subtree and the counts shown next to it in the browser. */
data class TopicNode(
    val topic: Topic,
    val children: List<TopicNode> = emptyList(),
    /** Cards in this topic *and* every descendant. */
    val cardCount: Int = 0,
    /** Cards due for review in this topic *and* every descendant. */
    val dueCount: Int = 0,
    val depth: Int = 0,
) {
    val isLeaf: Boolean get() = children.isEmpty()
    val id: TopicId get() = topic.id
    val name: String get() = topic.name
    val isEditable: Boolean get() = topic.isEditable
}

/** An immutable, already-aggregated topic forest. */
data class TopicTree(val roots: List<TopicNode>) {

    /** Every node, depth first, in display order. */
    fun flatten(): List<TopicNode> = roots.flatMap { flatten(it) }

    private fun flatten(node: TopicNode): List<TopicNode> = listOf(node) + node.children.flatMap { flatten(it) }

    fun find(id: TopicId): TopicNode? = flatten().firstOrNull { it.id == id }

    /** The chain of nodes from a root down to [id], used for breadcrumbs. */
    fun pathTo(id: TopicId): List<TopicNode> {
        for (root in roots) {
            val path = pathTo(root, id)
            if (path != null) return path
        }
        return emptyList()
    }

    private fun pathTo(node: TopicNode, id: TopicId): List<TopicNode>? {
        if (node.id == id) return listOf(node)
        for (child in node.children) {
            val path = pathTo(child, id)
            if (path != null) return listOf(node) + path
        }
        return null
    }

    fun breadcrumbs(id: TopicId): List<Topic> = pathTo(id).map { it.topic }

    /**
     * [id] plus every descendant — the expansion the brief requires for a topic
     * selection. Returns an empty set for an unknown id so callers can treat
     * "nothing selected" and "unknown selection" the same way.
     */
    fun descendantsIncluding(id: TopicId): Set<TopicId> {
        val node = find(id) ?: return emptySet()
        val result = LinkedHashSet<TopicId>()
        collect(node, result)
        return result
    }

    private fun collect(node: TopicNode, into: MutableSet<TopicId>) {
        into.add(node.id)
        node.children.forEach { collect(it, into) }
    }

    /** Expands every selected topic to include its descendants. */
    fun selectedTopicIds(selection: Set<TopicId>): Set<TopicId> {
        val result = LinkedHashSet<TopicId>()
        for (id in selection) result.addAll(descendantsIncluding(id))
        return result
    }

    companion object {
        val Empty: TopicTree = TopicTree(emptyList())
    }
}

/**
 * Builds a [TopicTree] from a flat topic list. Tolerates the data problems a
 * locally editable tree can produce — a missing parent, a self-parent, or a
 * cycle — by promoting the affected topics to roots or dropping the cyclic edge,
 * rather than recursing forever.
 */
object TopicTreeBuilder {

    fun build(
        topics: List<Topic>,
        cardCounts: Map<TopicId, Int> = emptyMap(),
        dueCounts: Map<TopicId, Int> = emptyMap(),
    ): TopicTree {
        if (topics.isEmpty()) return TopicTree.Empty

        val byId = LinkedHashMap<TopicId, Topic>(topics.size)
        for (topic in topics) {
            if (byId[topic.id] == null) byId[topic.id] = topic
        }

        val childrenOf = LinkedHashMap<TopicId?, MutableList<TopicId>>()
        for (topic in topics) {
            if (!byId.containsKey(topic.id)) continue
            val parent = topic.parentId?.takeIf { it != topic.id && byId.containsKey(it) }
            childrenOf.getOrPut(parent) { mutableListOf() }.add(topic.id)
        }

        val rootIds = childrenOf[null].orEmpty()
        val visiting = HashSet<TopicId>()
        val roots = rootIds.mapNotNull { buildNode(it, 0, byId, childrenOf, cardCounts, dueCounts, visiting) }
        return TopicTree(roots)
    }

    private fun buildNode(
        id: TopicId,
        depth: Int,
        byId: Map<TopicId, Topic>,
        childrenOf: Map<TopicId?, List<TopicId>>,
        cardCounts: Map<TopicId, Int>,
        dueCounts: Map<TopicId, Int>,
        visiting: MutableSet<TopicId>,
    ): TopicNode? {
        val topic = byId[id] ?: return null
        // A cycle would otherwise recurse forever; break it by dropping the node.
        if (!visiting.add(id)) return null

        val children = childrenOf[id]
            .orEmpty()
            .mapNotNull { childId -> buildNode(childId, depth + 1, byId, childrenOf, cardCounts, dueCounts, visiting) }
            .sortedWith(compareBy<TopicNode> { it.topic.sortOrder }.thenBy { it.topic.name })

        visiting.remove(id)

        val ownCards = cardCounts[id] ?: 0
        val ownDue = dueCounts[id] ?: 0
        return TopicNode(
            topic = topic,
            children = children,
            cardCount = ownCards + children.sumOf { it.cardCount },
            dueCount = ownDue + children.sumOf { it.dueCount },
            depth = depth,
        )
    }
}
