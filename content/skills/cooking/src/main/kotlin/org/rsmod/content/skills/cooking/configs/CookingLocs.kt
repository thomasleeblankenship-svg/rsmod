package org.rsmod.content.skills.cooking.configs

import org.rsmod.api.type.refs.loc.LocReferences

internal typealias cooking_locs = CookingLocs

internal object CookingLocs : LocReferences() {
    val fire = find("fire")
    val range = find("range")
}
