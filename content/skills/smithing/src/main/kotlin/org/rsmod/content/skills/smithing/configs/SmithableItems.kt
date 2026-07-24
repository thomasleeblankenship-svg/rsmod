package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.obj.ObjType

/**
 * Smithing (bar -> equipment) recipes. Only daggers are implemented as a representative item per
 * bar tier; the full smithing item catalog (hundreds of items per bar) is not covered.
 */
internal data class SmithingRecipe(
    val bar: ObjType,
    val product: ObjType,
    val levelReq: Int,
    val xp: Double,
    val barCount: Int = 1,
)

internal object SmithableItems {
    val all =
        listOf(
            SmithingRecipe(objs.bronze_bar, objs.bronze_dagger, 1, 12.5),
            SmithingRecipe(objs.iron_bar, objs.iron_dagger, 15, 12.5),
            SmithingRecipe(objs.steel_bar, objs.steel_dagger, 30, 37.5),
            SmithingRecipe(objs.mithril_bar, objs.mithril_dagger, 50, 62.5),
            SmithingRecipe(objs.adamantite_bar, objs.adamant_dagger, 70, 87.5),
            SmithingRecipe(objs.runite_bar, objs.rune_dagger, 85, 112.5),
        )

    fun fineXp(recipe: SmithingRecipe): Double = PlayerStatMap.toFineXP(recipe.xp)
}
