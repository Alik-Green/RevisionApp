package com.revisionapp.domain.progression

import kotlinx.serialization.Serializable

/** The six independently unlocked/editable pieces of a learner avatar. */
enum class AvatarPartCategory(val title: String, val icon: String) {
    HAIR_STYLE("Hair", "✂️"),
    EYE_STYLE("Eyes", "👁️"),
    NOSE_STYLE("Nose", "◉"),
    SKIN_TONE("Skin tone", "🎨"),
    HAIR_COLOR("Hair colour", "🖌️"),
    EYE_COLOR("Eye colour", "🌈"),
}

data class AvatarPart(
    val id: String,
    val name: String,
    val description: String,
    val category: AvatarPartCategory,
    val costCoins: Long,
    val icon: String,
    val colorArgb: Long? = null,
)

/** Palette and ownership rules for the character designer and cosmetics shop. */
object AvatarPartCatalog {
    const val DEFAULT_HAIR_STYLE = "hair-crop"
    const val DEFAULT_EYE_STYLE = "eyes-round"
    const val DEFAULT_NOSE_STYLE = "nose-button"
    const val DEFAULT_SKIN_TONE = "skin-medium"
    const val DEFAULT_HAIR_COLOR = "hair-dark-brown"
    const val DEFAULT_EYE_COLOR = "eyes-brown"

    private val skinTones = listOf(
        AvatarPart("skin-porcelain", "Porcelain", "A very light natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFFFE9D5),
        AvatarPart("skin-fair", "Fair", "A light natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFF7D7BC),
        AvatarPart("skin-light", "Light", "A warm light natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFE8BC99),
        AvatarPart("skin-medium", "Medium", "A balanced natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFD59D78),
        AvatarPart("skin-olive", "Olive", "A golden olive natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFC2865F),
        AvatarPart("skin-tan", "Tan", "A rich sun-kissed natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFFA96D4D),
        AvatarPart("skin-brown", "Brown", "A deep warm natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFF805038),
        AvatarPart("skin-deep", "Deep", "A very deep natural tone.", AvatarPartCategory.SKIN_TONE, 0, "●", 0xFF593522),
        AvatarPart("skin-mint", "Mint green", "A playful fantasy complexion.", AvatarPartCategory.SKIN_TONE, 95, "●", 0xFF83C9A6),
        AvatarPart("skin-lavender", "Lavender", "A soft violet fantasy complexion.", AvatarPartCategory.SKIN_TONE, 115, "●", 0xFFB59BD7),
        AvatarPart("skin-sky", "Sky blue", "A bright blue fantasy complexion.", AvatarPartCategory.SKIN_TONE, 125, "●", 0xFF84BADB),
        AvatarPart("skin-sunrise", "Sunrise", "A warm peach-and-gold fantasy complexion.", AvatarPartCategory.SKIN_TONE, 115, "●", 0xFFE9A568),
    )

    val all: List<AvatarPart> = listOf(
        AvatarPart("hair-crop", "Soft crop", "Short, tidy shape.", AvatarPartCategory.HAIR_STYLE, 0, "🧑‍🦱"),
        AvatarPart("hair-waves", "Loose waves", "A relaxed wavy shape.", AvatarPartCategory.HAIR_STYLE, 0, "〰️"),
        AvatarPart("hair-curly", "Curly", "Springy curls with extra volume.", AvatarPartCategory.HAIR_STYLE, 75, "🌀"),
        AvatarPart("hair-bob", "Rounded bob", "A neat rounded style.", AvatarPartCategory.HAIR_STYLE, 80, "💇"),
        AvatarPart("hair-long", "Long layers", "Long hair framing the face.", AvatarPartCategory.HAIR_STYLE, 100, "💁"),
        AvatarPart("hair-spiky", "Spiky", "A playful, bold silhouette.", AvatarPartCategory.HAIR_STYLE, 85, "⚡"),
        AvatarPart("hair-ponytail", "Ponytail", "A high ponytail and swept fringe.", AvatarPartCategory.HAIR_STYLE, 105, "🎀"),
        AvatarPart("hair-bun", "Top bun", "A bun with a soft fringe.", AvatarPartCategory.HAIR_STYLE, 95, "🍡"),
        AvatarPart("hair-locs", "Textured twists", "Textured twists with a bold silhouette.", AvatarPartCategory.HAIR_STYLE, 65, "≋"),
        AvatarPart("hair-fringe", "Side fringe", "A sweeping fringe across the forehead.", AvatarPartCategory.HAIR_STYLE, 40, "⌒"),
        AvatarPart("eyes-round", "Bright round", "Friendly open eyes.", AvatarPartCategory.EYE_STYLE, 0, "◉"),
        AvatarPart("eyes-almond", "Almond", "A slightly tapered eye shape.", AvatarPartCategory.EYE_STYLE, 65, "◍"),
        AvatarPart("eyes-sleepy", "Sleepy", "Relaxed half-lidded eyes.", AvatarPartCategory.EYE_STYLE, 75, "⌒"),
        AvatarPart("eyes-sparkle", "Sparkle", "Extra-bright eyes with a glint.", AvatarPartCategory.EYE_STYLE, 100, "✦"),
        AvatarPart("eyes-wink", "Wink", "A cheerful one-eye wink.", AvatarPartCategory.EYE_STYLE, 90, "😉"),
        AvatarPart("eyes-cat", "Upturned", "A subtle, lifted outer corner.", AvatarPartCategory.EYE_STYLE, 95, "⌁"),
        AvatarPart("nose-button", "Button", "A small, soft button nose.", AvatarPartCategory.NOSE_STYLE, 0, "•"),
        AvatarPart("nose-soft", "Soft curve", "A gentle curved line.", AvatarPartCategory.NOSE_STYLE, 55, "⌁"),
        AvatarPart("nose-bridge", "Defined bridge", "A slightly longer nose shape.", AvatarPartCategory.NOSE_STYLE, 70, "⌇"),
        AvatarPart("nose-freckles", "Freckles", "A tiny nose with freckles.", AvatarPartCategory.NOSE_STYLE, 85, "⁙"),
        AvatarPart("nose-upturned", "Upturned", "A softly lifted tip.", AvatarPartCategory.NOSE_STYLE, 65, "⌃"),
        AvatarPart("hair-dark-brown", "Dark brown", "A deep natural brown.", AvatarPartCategory.HAIR_COLOR, 0, "●", 0xFF3A241C),
        AvatarPart("hair-black", "Soft black", "A natural near-black.", AvatarPartCategory.HAIR_COLOR, 25, "●", 0xFF211E24),
        AvatarPart("hair-chestnut", "Chestnut", "A warm chestnut brown.", AvatarPartCategory.HAIR_COLOR, 35, "●", 0xFF75432D),
        AvatarPart("hair-blonde", "Golden blonde", "A warm golden blonde.", AvatarPartCategory.HAIR_COLOR, 45, "●", 0xFFD5A849),
        AvatarPart("hair-auburn", "Auburn", "A rich red-brown.", AvatarPartCategory.HAIR_COLOR, 50, "●", 0xFFA64735),
        AvatarPart("hair-silver", "Silver", "A cool silver-grey.", AvatarPartCategory.HAIR_COLOR, 65, "●", 0xFFB6B8C1),
        AvatarPart("hair-teal", "Teal", "A vivid fantasy teal.", AvatarPartCategory.HAIR_COLOR, 75, "●", 0xFF168F91),
        AvatarPart("hair-blue", "Electric blue", "A bright blue fantasy colour.", AvatarPartCategory.HAIR_COLOR, 75, "●", 0xFF4B77E8),
        AvatarPart("hair-rose", "Rose pink", "A bright rosy fantasy colour.", AvatarPartCategory.HAIR_COLOR, 80, "●", 0xFFC64F86),
        AvatarPart("eyes-brown", "Brown", "Warm brown eyes.", AvatarPartCategory.EYE_COLOR, 0, "●", 0xFF593622),
        AvatarPart("eyes-blue", "Blue", "Bright ocean blue.", AvatarPartCategory.EYE_COLOR, 30, "●", 0xFF3476C5),
        AvatarPart("eyes-green", "Green", "Fresh leaf green.", AvatarPartCategory.EYE_COLOR, 35, "●", 0xFF31845B),
        AvatarPart("eyes-hazel", "Hazel", "A mix of green and amber.", AvatarPartCategory.EYE_COLOR, 35, "●", 0xFF8D7134),
        AvatarPart("eyes-grey", "Storm grey", "A cool grey-blue shade.", AvatarPartCategory.EYE_COLOR, 40, "●", 0xFF75889B),
        AvatarPart("eyes-amber", "Amber", "A glowing golden brown.", AvatarPartCategory.EYE_COLOR, 45, "●", 0xFFC27B27),
        AvatarPart("eyes-violet", "Violet", "A vivid fantasy violet.", AvatarPartCategory.EYE_COLOR, 60, "●", 0xFF8054C8),
    ) + skinTones

    val naturalSkinToneIds: List<String> = skinTones.take(8).map { it.id }
    val startingOwnedIds: List<String> = listOf(
        DEFAULT_HAIR_STYLE,
        "hair-waves",
        DEFAULT_EYE_STYLE,
        DEFAULT_NOSE_STYLE,
        DEFAULT_HAIR_COLOR,
        DEFAULT_EYE_COLOR,
    ) + naturalSkinToneIds

    fun find(id: String): AvatarPart? = all.firstOrNull { it.id == id }

    fun inCategory(category: AvatarPartCategory): List<AvatarPart> = all.filter { it.category == category }
}

/** Serializable, editable selections plus free shape controls for an avatar. */
@Serializable
data class CharacterAppearance(
    val hairStyleId: String = AvatarPartCatalog.DEFAULT_HAIR_STYLE,
    val eyeStyleId: String = AvatarPartCatalog.DEFAULT_EYE_STYLE,
    val noseStyleId: String = AvatarPartCatalog.DEFAULT_NOSE_STYLE,
    val skinToneId: String = AvatarPartCatalog.DEFAULT_SKIN_TONE,
    val hairColorId: String = AvatarPartCatalog.DEFAULT_HAIR_COLOR,
    val eyeColorId: String = AvatarPartCatalog.DEFAULT_EYE_COLOR,
    /** 0 = narrow, 1 = wide. */
    val faceWidth: Float = 0.5f,
    /** 0 = short, 1 = tall. */
    val faceHeight: Float = 0.5f,
    /** 0 = tapered jaw, 1 = rounded jaw. */
    val faceRoundness: Float = 0.5f,
    /** 0 = close set, 1 = wide set. */
    val eyeSpacing: Float = 0.46f,
    /** 0 = smaller, 1 = larger. */
    val eyeSize: Float = 0.5f,
    /** 0 = higher, 1 = lower on the face. */
    val eyeHeight: Float = 0.48f,
    val noseSize: Float = 0.45f,
    /** 0 = higher, 1 = lower on the face. */
    val noseHeight: Float = 0.52f,
    /** 0 = smaller, 1 = larger. */
    val hairSize: Float = 0.5f,
    /** 0 = higher, 1 = lower on the head. */
    val hairHeight: Float = 0.48f,
    val hairVolume: Float = 0.52f,
)
