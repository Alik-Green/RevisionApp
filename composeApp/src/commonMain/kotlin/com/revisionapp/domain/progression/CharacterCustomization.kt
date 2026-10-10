package com.revisionapp.domain.progression

/** A small, earned-only character style. Costs are paid with in-app learning coins. */
data class CharacterCosmetic(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val cost: Long,
    val accentArgb: Long,
)

object CharacterCosmetics {
    const val STARTER_ID = "trailblazer"

    val all: List<CharacterCosmetic> = listOf(
        CharacterCosmetic(
            id = STARTER_ID,
            name = "Trailblazer",
            emoji = "🧭",
            description = "Your original companion for the learning path.",
            cost = 0,
            accentArgb = 0xFF4878D0,
        ),
        CharacterCosmetic(
            id = "sprout",
            name = "Sprout",
            emoji = "🌱",
            description = "A little green for every new beginning.",
            cost = 55,
            accentArgb = 0xFF4D9A63,
        ),
        CharacterCosmetic(
            id = "comet",
            name = "Comet",
            emoji = "☄️",
            description = "Bright ideas, flying into orbit.",
            cost = 80,
            accentArgb = 0xFF7552C7,
        ),
        CharacterCosmetic(
            id = "fox",
            name = "Clever Fox",
            emoji = "🦊",
            description = "Curious, quick, and ready to explore.",
            cost = 110,
            accentArgb = 0xFFE17A36,
        ),
        CharacterCosmetic(
            id = "owl",
            name = "Night Owl",
            emoji = "🦉",
            description = "A calm companion for focused study.",
            cost = 140,
            accentArgb = 0xFF557B8D,
        ),
        CharacterCosmetic(
            id = "robot",
            name = "Study Bot",
            emoji = "🤖",
            description = "A friendly little machine for big ideas.",
            cost = 175,
            accentArgb = 0xFF278F9B,
        ),
        CharacterCosmetic(
            id = "dragon",
            name = "Pocket Dragon",
            emoji = "🐉",
            description = "For the long quests and brave attempts.",
            cost = 230,
            accentArgb = 0xFFCC536B,
        ),
        CharacterCosmetic(
            id = "butterfly",
            name = "Brightwing",
            emoji = "🦋",
            description = "A colourful reminder that learning changes you.",
            cost = 300,
            accentArgb = 0xFFB34FA8,
        ),
    )

    val starter: CharacterCosmetic get() = all.first { it.id == STARTER_ID }

    fun find(id: String): CharacterCosmetic? = all.firstOrNull { it.id == id }
}
