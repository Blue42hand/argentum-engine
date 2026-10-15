package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Replaces an Aura's enchant ability; an optional source-record key limits it to those exact objects. */
@Serializable
@SerialName("EnchantRestrictionOverride")
data class EnchantRestrictionOverride(
    val requirement: TargetRequirement,
    val recordedHostKey: String? = null,
) : StaticAbility {
    override val description: String = "Enchant " + requirement.description +
        if (recordedHostKey != null) " recorded by this Aura" else ""
}
