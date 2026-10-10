package com.revisionapp.ui.screens

import com.revisionapp.domain.progression.AvatarPartCategory

/** Shared feature-first categories keep hair colours under Hair and eye colours under Eyes. */
internal enum class AvatarFeatureArea(val title: String, val icon: String) {
    HAIR("Hair", "✂️"),
    EYES("Eyes", "👁️"),
    NOSE("Nose", "◉"),
    FACE("Face", "☺️"),
}

internal enum class AvatarFeatureControl(val title: String) {
    STYLE("Style"),
    COLOUR("Colour"),
    SKIN("Skin"),
    SHAPE("Shape"),
}

internal fun controlsForArea(area: AvatarFeatureArea): List<AvatarFeatureControl> = when (area) {
    AvatarFeatureArea.HAIR, AvatarFeatureArea.EYES -> listOf(
        AvatarFeatureControl.STYLE,
        AvatarFeatureControl.COLOUR,
        AvatarFeatureControl.SHAPE,
    )

    AvatarFeatureArea.NOSE -> listOf(AvatarFeatureControl.STYLE, AvatarFeatureControl.SHAPE)
    AvatarFeatureArea.FACE -> listOf(AvatarFeatureControl.SKIN, AvatarFeatureControl.SHAPE)
}

internal fun defaultControlFor(area: AvatarFeatureArea): AvatarFeatureControl = when (area) {
    AvatarFeatureArea.HAIR, AvatarFeatureArea.EYES, AvatarFeatureArea.NOSE -> AvatarFeatureControl.STYLE
    AvatarFeatureArea.FACE -> AvatarFeatureControl.SKIN
}

internal fun partCategoryFor(
    area: AvatarFeatureArea,
    control: AvatarFeatureControl,
): AvatarPartCategory? = when (area to control) {
    AvatarFeatureArea.HAIR to AvatarFeatureControl.STYLE -> AvatarPartCategory.HAIR_STYLE
    AvatarFeatureArea.HAIR to AvatarFeatureControl.COLOUR -> AvatarPartCategory.HAIR_COLOR
    AvatarFeatureArea.EYES to AvatarFeatureControl.STYLE -> AvatarPartCategory.EYE_STYLE
    AvatarFeatureArea.EYES to AvatarFeatureControl.COLOUR -> AvatarPartCategory.EYE_COLOR
    AvatarFeatureArea.NOSE to AvatarFeatureControl.STYLE -> AvatarPartCategory.NOSE_STYLE
    AvatarFeatureArea.FACE to AvatarFeatureControl.SKIN -> AvatarPartCategory.SKIN_TONE
    else -> null
}

internal fun areaForCategory(category: AvatarPartCategory): AvatarFeatureArea = when (category) {
    AvatarPartCategory.HAIR_STYLE, AvatarPartCategory.HAIR_COLOR -> AvatarFeatureArea.HAIR
    AvatarPartCategory.EYE_STYLE, AvatarPartCategory.EYE_COLOR -> AvatarFeatureArea.EYES
    AvatarPartCategory.NOSE_STYLE -> AvatarFeatureArea.NOSE
    AvatarPartCategory.SKIN_TONE -> AvatarFeatureArea.FACE
}

internal fun controlForCategory(category: AvatarPartCategory): AvatarFeatureControl = when (category) {
    AvatarPartCategory.HAIR_STYLE, AvatarPartCategory.EYE_STYLE, AvatarPartCategory.NOSE_STYLE -> AvatarFeatureControl.STYLE
    AvatarPartCategory.HAIR_COLOR, AvatarPartCategory.EYE_COLOR -> AvatarFeatureControl.COLOUR
    AvatarPartCategory.SKIN_TONE -> AvatarFeatureControl.SKIN
}

internal fun shapeCategoryFor(area: AvatarFeatureArea): AvatarPartCategory = when (area) {
    AvatarFeatureArea.HAIR -> AvatarPartCategory.HAIR_STYLE
    AvatarFeatureArea.EYES -> AvatarPartCategory.EYE_STYLE
    AvatarFeatureArea.NOSE -> AvatarPartCategory.NOSE_STYLE
    AvatarFeatureArea.FACE -> AvatarPartCategory.SKIN_TONE
}
