package com.revisionapp.domain.progression

import kotlinx.serialization.Serializable

/** One independently unlockable cosmetic used to build a learner's character. */
data class AppearanceItem(
    val id: String,
    val category: AppearanceCategory,
    val name: String,
    val description: String,
    val cost: Long,
    val swatchArgb: Long? = null,
    val previewMark: String = "✦",
)

enum class AppearanceCategory(val title: String, val storeTitle: String) {
    SKIN_TONE("Skin", "Skin colours"),
    HAIR_STYLE("Hair", "Hair styles"),
    HAIR_COLOR("Hair colour", "Hair colours"),
    EYE_STYLE("Eyes", "Eye styles"),
    EYE_COLOR("Eye colour", "Eye colours"),
    NOSE_STYLE("Nose", "Nose styles"),
}

/** The selected pieces and free shape controls for the profile character editor. */
@Serializable
data class CharacterAppearance(
    val skinToneId: String = AppearanceCatalog.SKIN_MEDIUM,
    val hairStyleId: String = AppearanceCatalog.HAIR_SHORT,
    val hairColorId: String = AppearanceCatalog.HAIR_COLOR_BROWN,
    val eyeStyleId: String = AppearanceCatalog.EYE_CLASSIC,
    val eyeColorId: String = AppearanceCatalog.EYE_COLOR_BROWN,
    val noseStyleId: String = AppearanceCatalog.NOSE_CLASSIC,
    /** All shape controls use a 0..100 range and are free after a piece is unlocked. */
    val hairSize: Int = 50,
    val hairHeight: Int = 48,
    val eyeSize: Int = 50,
    val eyeSpacing: Int = 50,
    val eyeHeight: Int = 47,
    val noseSize: Int = 50,
    val noseHeight: Int = 52,
)

object AppearanceCatalog {
    const val SKIN_PORCELAIN = "skin-porcelain"
    const val SKIN_FAIR = "skin-fair"
    const val SKIN_LIGHT = "skin-light"
    const val SKIN_MEDIUM = "skin-medium"
    const val SKIN_DEEP = "skin-deep"
    const val SKIN_DARK = "skin-dark"

    const val HAIR_SHORT = "hair-short"
    const val HAIR_LONG = "hair-long"
    const val HAIR_COLOR_BROWN = "hair-colour-brown"
    const val EYE_CLASSIC = "eyes-classic"
    const val EYE_COLOR_BROWN = "eye-colour-brown"
    const val NOSE_CLASSIC = "nose-classic"

    private val naturalSkinTones = listOf(
        AppearanceItem(SKIN_PORCELAIN, AppearanceCategory.SKIN_TONE, "Porcelain", "A very light, rosy tone.", 0, 0xFFFFE5D2, "●"),
        AppearanceItem(SKIN_FAIR, AppearanceCategory.SKIN_TONE, "Fair", "A light warm tone.", 0, 0xFFF4CBA8, "●"),
        AppearanceItem(SKIN_LIGHT, AppearanceCategory.SKIN_TONE, "Light", "A light golden tone.", 0, 0xFFE3B48D, "●"),
        AppearanceItem(SKIN_MEDIUM, AppearanceCategory.SKIN_TONE, "Medium", "A balanced warm tone.", 0, 0xFFC9916D, "●"),
        AppearanceItem(SKIN_DEEP, AppearanceCategory.SKIN_TONE, "Deep", "A rich brown tone.", 0, 0xFF956047, "●"),
        AppearanceItem(SKIN_DARK, AppearanceCategory.SKIN_TONE, "Dark", "A deep espresso tone.", 0, 0xFF57372F, "●"),
    )

    val starterItemIds: List<String> = listOf(
        SKIN_PORCELAIN,
        SKIN_FAIR,
        SKIN_LIGHT,
        SKIN_MEDIUM,
        SKIN_DEEP,
        SKIN_DARK,
        HAIR_SHORT,
        HAIR_LONG,
        HAIR_COLOR_BROWN,
        EYE_CLASSIC,
        EYE_COLOR_BROWN,
        NOSE_CLASSIC,
    )

    val all: List<AppearanceItem> = naturalSkinTones + listOf(
        AppearanceItem("skin-mint", AppearanceCategory.SKIN_TONE, "Mint", "An adventurous mint-green complexion.", 95, 0xFF91D7A8, "●"),
        AppearanceItem("skin-lavender", AppearanceCategory.SKIN_TONE, "Lavender", "A soft violet fantasy tone.", 105, 0xFFC6A3E8, "●"),
        AppearanceItem("skin-sky", AppearanceCategory.SKIN_TONE, "Sky", "A bright blue character look.", 105, 0xFF86C8E8, "●"),
        AppearanceItem("skin-sunrise", AppearanceCategory.SKIN_TONE, "Sunrise", "A warm peach-and-gold fantasy tone.", 115, 0xFFE9A568, "●"),

        AppearanceItem(HAIR_SHORT, AppearanceCategory.HAIR_STYLE, "Short crop", "A neat, classic cut.", 0, previewMark = "⌁"),
        AppearanceItem(HAIR_LONG, AppearanceCategory.HAIR_STYLE, "Long", "Long hair that frames the face.", 0, previewMark = "〰"),
        AppearanceItem("hair-wavy", AppearanceCategory.HAIR_STYLE, "Wavy", "Soft waves with extra movement.", 40, previewMark = "≈"),
        AppearanceItem("hair-curly", AppearanceCategory.HAIR_STYLE, "Curly", "A lively halo of curls.", 50, previewMark = "✺"),
        AppearanceItem("hair-bob", AppearanceCategory.HAIR_STYLE, "Rounded bob", "A rounded cut with a fringe.", 45, previewMark = "◠"),
        AppearanceItem("hair-bun", AppearanceCategory.HAIR_STYLE, "Top knot", "A high bun with a tidy shape.", 55, previewMark = "♧"),
        AppearanceItem("hair-locs", AppearanceCategory.HAIR_STYLE, "Twists", "Textured twists and a bold silhouette.", 65, previewMark = "≋"),
        AppearanceItem("hair-fringe", AppearanceCategory.HAIR_STYLE, "Side fringe", "A sweeping fringe across the forehead.", 40, previewMark = "⌒"),

        AppearanceItem(HAIR_COLOR_BROWN, AppearanceCategory.HAIR_COLOR, "Chestnut", "Your natural starter colour.", 0, 0xFF59382F, "●"),
        AppearanceItem("hair-colour-black", AppearanceCategory.HAIR_COLOR, "Midnight", "A deep, cool black.", 25, 0xFF211A1A, "●"),
        AppearanceItem("hair-colour-blonde", AppearanceCategory.HAIR_COLOR, "Honey blonde", "Warm golden blonde.", 35, 0xFFE5BE65, "●"),
        AppearanceItem("hair-colour-copper", AppearanceCategory.HAIR_COLOR, "Copper", "Bright, warm copper.", 35, 0xFFC6663B, "●"),
        AppearanceItem("hair-colour-silver", AppearanceCategory.HAIR_COLOR, "Silver", "A cool silver-grey.", 45, 0xFFB9C2CC, "●"),
        AppearanceItem("hair-colour-rose", AppearanceCategory.HAIR_COLOR, "Rose pink", "A playful pink colour.", 55, 0xFFD56F9A, "●"),
        AppearanceItem("hair-colour-blue", AppearanceCategory.HAIR_COLOR, "Electric blue", "A bright blue fantasy colour.", 65, 0xFF4B77E8, "●"),

        AppearanceItem(EYE_CLASSIC, AppearanceCategory.EYE_STYLE, "Classic", "Friendly, balanced eyes.", 0, previewMark = "◉"),
        AppearanceItem("eyes-round", AppearanceCategory.EYE_STYLE, "Round", "Open, round eyes.", 40, previewMark = "●"),
        AppearanceItem("eyes-almond", AppearanceCategory.EYE_STYLE, "Almond", "A softly tapered shape.", 45, previewMark = "◖"),
        AppearanceItem("eyes-sleepy", AppearanceCategory.EYE_STYLE, "Sleepy", "A relaxed, half-lidded look.", 45, previewMark = "◡"),
        AppearanceItem("eyes-sparkle", AppearanceCategory.EYE_STYLE, "Sparkle", "Bright eyes with a little glint.", 55, previewMark = "✧"),
        AppearanceItem("eyes-cat", AppearanceCategory.EYE_STYLE, "Upturned", "A subtle, lifted outer corner.", 55, previewMark = "⌁"),

        AppearanceItem(EYE_COLOR_BROWN, AppearanceCategory.EYE_COLOR, "Warm brown", "Your natural starter colour.", 0, 0xFF68442F, "●"),
        AppearanceItem("eye-colour-blue", AppearanceCategory.EYE_COLOR, "Ocean blue", "A clear blue gaze.", 30, 0xFF438DB8, "●"),
        AppearanceItem("eye-colour-green", AppearanceCategory.EYE_COLOR, "Forest green", "A rich green gaze.", 35, 0xFF3B8964, "●"),
        AppearanceItem("eye-colour-hazel", AppearanceCategory.EYE_COLOR, "Hazel", "A golden-green mix.", 35, 0xFFA47A39, "●"),
        AppearanceItem("eye-colour-grey", AppearanceCategory.EYE_COLOR, "Storm grey", "A cool grey-blue shade.", 40, 0xFF75889B, "●"),
        AppearanceItem("eye-colour-violet", AppearanceCategory.EYE_COLOR, "Violet", "A rare violet fantasy shade.", 60, 0xFF8958BA, "●"),

        AppearanceItem(NOSE_CLASSIC, AppearanceCategory.NOSE_STYLE, "Classic", "A simple, soft nose shape.", 0, previewMark = "⌄"),
        AppearanceItem("nose-button", AppearanceCategory.NOSE_STYLE, "Button", "A small, rounded tip.", 30, previewMark = "•"),
        AppearanceItem("nose-wide", AppearanceCategory.NOSE_STYLE, "Soft wide", "A broader, rounded nose.", 35, previewMark = "⌵"),
        AppearanceItem("nose-upturned", AppearanceCategory.NOSE_STYLE, "Upturned", "A small lifted tip.", 40, previewMark = "⌃"),
        AppearanceItem("nose-straight", AppearanceCategory.NOSE_STYLE, "Straight", "A longer, clean bridge.", 45, previewMark = "│"),
    )

    fun find(id: String): AppearanceItem? = all.firstOrNull { it.id == id }

    fun inCategory(category: AppearanceCategory): List<AppearanceItem> = all.filter { it.category == category }

    fun defaultAppearance(): CharacterAppearance = CharacterAppearance()
}
