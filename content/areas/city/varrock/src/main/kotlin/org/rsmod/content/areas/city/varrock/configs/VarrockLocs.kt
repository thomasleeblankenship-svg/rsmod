package org.rsmod.content.areas.city.varrock.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences

typealias varrock_locs = VarrockLocs

object VarrockLocs : LocReferences() {
    val bankbooth = find("fai_varrock_bankbooth")
}

internal object VarrockLocEditor : LocEditor() {
    init {
        edit(varrock_locs.bankbooth) { contentGroup = content.bank_booth }
    }
}
