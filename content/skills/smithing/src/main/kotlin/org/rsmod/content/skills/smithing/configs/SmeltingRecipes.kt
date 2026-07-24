package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.config.refs.objs
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.obj.ObjType

/**
 * Smelting recipes. Coal-per-bar amounts and level/xp values follow well-known long-standing OSRS
 * figures; the "50% smelt-fail chance without a ring of forging" mechanic for iron is intentionally
 * omitted for simplicity.
 */
internal data class SmeltingRecipe(
    val bar: ObjType,
    val levelReq: Int,
    val xp: Double,
    val primaryOre: ObjType,
    val secondaryOre: ObjType? = null,
    val coalCount: Int = 0,
)

internal object SmeltingRecipes {
    val all =
        listOf(
            SmeltingRecipe(objs.bronze_bar, 1, 6.25, objs.copper_ore, objs.tin_ore),
            SmeltingRecipe(objs.iron_bar, 15, 12.5, objs.iron_ore),
            SmeltingRecipe(objs.silver_bar, 20, 13.7, objs.silver_ore),
            SmeltingRecipe(objs.steel_bar, 30, 17.5, objs.iron_ore, coalCount = 2),
            SmeltingRecipe(objs.gold_bar, 40, 22.5, objs.gold_ore),
            SmeltingRecipe(objs.mithril_bar, 50, 30.0, objs.mithril_ore, coalCount = 4),
            SmeltingRecipe(objs.adamantite_bar, 70, 37.5, objs.adamantite_ore, coalCount = 6),
            SmeltingRecipe(objs.runite_bar, 85, 50.0, objs.runite_ore, coalCount = 8),
        )

    fun fineXp(recipe: SmeltingRecipe): Double = PlayerStatMap.toFineXP(recipe.xp)
}
