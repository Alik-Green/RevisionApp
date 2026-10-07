package com.revisionapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicTreeTest {

    private fun topic(
        id: String,
        name: String = id,
        parent: String? = null,
        sortOrder: Int = 0,
    ): Topic = Topic(
        id = TopicId(id),
        name = name,
        parentId = parent?.let { TopicId(it) },
        sortOrder = sortOrder,
    )

    private val physicsTree: List<Topic> = listOf(
        topic("physics", "Physics"),
        topic("module-4", "Newtonian world", parent = "physics", sortOrder = 4),
        topic("module-1", "Foundations", parent = "physics", sortOrder = 1),
        topic("circular-motion", "Circular motion", parent = "module-4", sortOrder = 2),
        topic("moments", "Moments", parent = "module-4", sortOrder = 1),
        topic("centripetal-force", "Centripetal force", parent = "circular-motion"),
    )

    @Test
    fun buildsANestedForestFromAFlatList() {
        val tree = TopicTreeBuilder.build(physicsTree)

        assertEquals(1, tree.roots.size)
        val physics = tree.roots.single()
        assertEquals("Physics", physics.name)
        // Sorted by sortOrder, not by insertion order.
        assertEquals(listOf("Foundations", "Newtonian world"), physics.children.map { it.name })

        val newtonian = physics.children.last()
        assertEquals(listOf("Moments", "Circular motion"), newtonian.children.map { it.name })

        val circularMotion = newtonian.children.last()
        assertEquals(listOf("Centripetal force"), circularMotion.children.map { it.name })
        assertEquals(2, circularMotion.depth)
        assertEquals(3, tree.find(TopicId("centripetal-force"))?.depth)
        assertTrue(tree.find(TopicId("centripetal-force"))?.isLeaf == true)
    }

    @Test
    fun aggregatesCardAndDueCountsUpTheTree() {
        val tree = TopicTreeBuilder.build(
            topics = physicsTree,
            cardCounts = mapOf(
                TopicId("centripetal-force") to 3,
                TopicId("moments") to 4,
                TopicId("module-1") to 2,
            ),
            dueCounts = mapOf(
                TopicId("centripetal-force") to 1,
                TopicId("module-1") to 2,
            ),
        )

        val newtonian = tree.find(TopicId("module-4"))
        assertEquals(7, newtonian?.cardCount)
        assertEquals(1, newtonian?.dueCount)
        assertEquals(9, tree.roots.single().cardCount)
        assertEquals(3, tree.roots.single().dueCount)
        // A leaf with cards of its own and nothing due reports zero, not null.
        assertEquals(4, tree.find(TopicId("moments"))?.cardCount)
        assertEquals(0, tree.find(TopicId("moments"))?.dueCount)
    }

    @Test
    fun aSelectionExpandsToAllDescendants() {
        val tree = TopicTreeBuilder.build(physicsTree)

        val selection = tree.descendantsIncluding(TopicId("module-4"))

        assertEquals(
            setOf(TopicId("module-4"), TopicId("circular-motion"), TopicId("moments"), TopicId("centripetal-force")),
            selection,
        )
        assertTrue(tree.descendantsIncluding(TopicId("centripetal-force")) == setOf(TopicId("centripetal-force")))
        assertTrue(tree.descendantsIncluding(TopicId("nope")).isEmpty())
    }

    @Test
    fun expandsSeveralSelectedTopicsAtOnce() {
        val tree = TopicTreeBuilder.build(physicsTree)

        val selection = tree.selectedTopicIds(setOf(TopicId("module-1"), TopicId("circular-motion")))

        assertEquals(
            setOf(TopicId("module-1"), TopicId("circular-motion"), TopicId("centripetal-force")),
            selection,
        )
    }

    @Test
    fun breadcrumbsRunFromTheRootDownToTheNode() {
        val tree = TopicTreeBuilder.build(physicsTree)

        assertEquals(
            listOf("Physics", "Newtonian world", "Circular motion", "Centripetal force"),
            tree.breadcrumbs(TopicId("centripetal-force")).map { it.name },
        )
        assertTrue(tree.breadcrumbs(TopicId("missing")).isEmpty())
    }

    @Test
    fun aTopicWhoseParentIsMissingBecomesARoot() {
        val tree = TopicTreeBuilder.build(
            listOf(
                topic("child", parent = "gone"),
                topic("sibling", parent = "child"),
            ),
        )

        assertEquals(1, tree.roots.size)
        assertEquals(TopicId("child"), tree.roots.single().id)
        assertEquals(1, tree.roots.single().children.size)
    }

    @Test
    fun aCycleTerminatesInsteadOfRecurringForever() {
        val tree = TopicTreeBuilder.build(
            listOf(
                topic("a", parent = "b"),
                topic("b", parent = "a"),
                topic("free"),
            ),
        )

        assertEquals(listOf(TopicId("free")), tree.roots.map { it.id })
        assertNull(tree.find(TopicId("a")))
    }

    @Test
    fun aSelfParentedTopicIsPromotedToARoot() {
        val tree = TopicTreeBuilder.build(listOf(topic("loop", parent = "loop")))

        assertEquals(1, tree.roots.size)
        assertTrue(tree.roots.single().children.isEmpty())
    }

    @Test
    fun flattenVisitsEveryNodeOnce() {
        val tree = TopicTreeBuilder.build(physicsTree)

        assertEquals(physicsTree.size, tree.flatten().size)
        assertEquals(physicsTree.map { it.id }.toSet(), tree.flatten().map { it.id }.toSet())
    }
}
