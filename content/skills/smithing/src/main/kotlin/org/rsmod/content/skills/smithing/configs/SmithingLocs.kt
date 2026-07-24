package org.rsmod.content.skills.smithing.configs

import org.rsmod.api.type.refs.loc.LocReferences

internal typealias smithing_locs = SmithingLocs

internal object SmithingLocs : LocReferences() {
    val furnace = find("furnace")
    val anvil = find("anvil")
}
