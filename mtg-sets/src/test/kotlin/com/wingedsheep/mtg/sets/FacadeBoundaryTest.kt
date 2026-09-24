package com.wingedsheep.mtg.sets

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.readText

/**
 * Enforces the facade boundary (SDK architecture review §2.3).
 *
 * Card definitions are an anti-corruption layer: they must construct effects and costs through
 * the curated `Effects.*` / `Costs.*` facades, never through the foundational data classes
 * directly. That contract is what lets the SDK refactor those underlying types without touching
 * the ~3,500 card files. This test scans every card definition and fails on any direct
 * construction of the foundational effect/cost types, pointing at the facade to use instead.
 *
 * Comments and `import` lines are ignored — only real code is checked.
 */
class FacadeBoundaryTest : FunSpec({

    /** Forbidden construction → human-readable facade hint. */
    val forbidden = listOf(
        Regex("""\bCompositeEffect\s*\(""") to "Effects.Composite(...)",
        Regex("""\bMoveToZoneEffect\s*\(""") to "Effects.Move(...) (or Effects.Destroy/Exile/ReturnToHand/…)",
        Regex("""\bForEachInGroupEffect\s*\(""") to "Effects.ForEachInGroup(...)",
        Regex("""\bAdditionalCost\.[A-Z]""") to "Costs.additional.*",
        Regex("""\bPayCost\.[A-Z]""") to "Costs.pay.*",
        Regex("""(?<!Conditions\.)\bEntityMatches\s*\(""") to
            "Conditions.EntityMatches(...) (or Conditions.SourceMatches/TargetMatchesFilter/…)",
        // Every effect data class is named `…Effect`; a card never constructs one directly. The
        // lookbehind lets qualified calls through — `Effects.GrantReplacementEffect(…)`,
        // `ModalEffect.chooseOne(…)` — and objects (`SacrificeSelfEffect`) aren't constructions.
        Regex("""((?<![\w.])|\bscripting\.effects\.)[A-Z]\w*Effect\s*\(""") to "the Effects.* facade for that effect",
        Regex("""(?<![\w.])(GatedEffect\s*\(|Gate\.[A-Z])""") to
            "Effects.If / May / MayPay / MayPayX / IfYouDo",
    )

    /**
     * Raw pipeline steps thread their data through string keys, so a typo or a read of a
     * collection nobody wrote only surfaces at runtime (or in `CardLinter`). Cards write pipelines
     * with `Effects.Pipeline { }`, whose steps return typed handles; the raw steps stay
     * SDK-internal (the `Patterns.*` helpers, the engine, JSON-loaded cards).
     */
    val pipelineSteps = listOf(
        "GatherCardsEffect", "SelectFromCollectionEffect", "MoveCollectionEffect", "FilterCollectionEffect",
        "RevealCollectionEffect", "ConditionalOnCollectionEffect", "GatherUntilMatchEffect",
        "GatherSubtypesEffect", "ChoosePileEffect", "CaptureControllersEffect", "ForEachCapturedControllerEffect",
        "StoreCardNameEffect", "StoreNumberEffect", "SelectTargetEffect", "ChooseOptionEffect",
        "ChooseOnePerCategoryEffect", "NoteCreatureTypeEffect", "PairWithSourceEffect",
        "CopyCardIntoCollectionEffect", "CopyCollectionIntoCollectionEffect",
    ).map { Regex("""(?<![\w.])$it\s*\(""") to "Effects.Pipeline { … } (the typed step verbs)" } + listOf(
        Regex("""\b(storeAs|storeSelected|storeRemainder|storeMatching|storeNonMatching|storeMatch|storeRevealed|storeChosenAs|storeOtherAs|storeMovedAs|collectionName|storeDestroyedAs|storeExiledAs)\s*=\s*"""") to
            "a handle from Effects.Pipeline { } (or runStoringCollection { key -> … } for a non-step writer)",
        Regex("""VariableReference\(\s*"\w*_count"\s*\)""") to "CollectionSlot.count",
        Regex("""\.key\b(?!\s*=)""") to "the handle itself or a typed accessor (count, asSource, asTarget, controllerOf)",
    )

    /**
     * Cards that must still spell a pipeline key, each with the reason. Keep this short; a new
     * entry needs a reason a reviewer would accept.
     */
    val pipelineAllowlist: Map<String, String> = mapOf(
        "rav/cards/Flickerform.kt" to
            "CreateDelayedTriggerEffect.carryCollections names the collections the delayed trigger remembers",
        "dsk/cards/MonstrousEmergence.kt" to
            "the cost's ChooseEntity storeAs is read by the spell effect — a cost is not inside any pipeline",
        "eoe/cards/CloseEncounter.kt" to
            "the cost's ChooseEntity storeAs is read by the spell effect — a cost is not inside any pipeline",
    )

    test("card definitions write pipelines with Effects.Pipeline, not raw string-keyed steps") {
        val violations = mutableListOf<String>()

        SetSourceRoots.definitionFiles().forEach { path ->
            val rel = SetSourceRoots.relativize(path)
            if (pipelineAllowlist.keys.any { rel.toString().endsWith(it) }) return@forEach
            stripCommentsAndImports(path.readText()).forEachIndexed { idx, line ->
                for ((regex, hint) in pipelineSteps) {
                    if (regex.containsMatchIn(line)) {
                        violations += "$rel:${idx + 1}  →  use $hint instead of `${regex.find(line)!!.value}`"
                    }
                }
            }
        }

        withClue(
            "Card definitions must write pipelines through Effects.Pipeline { } (typed handles, no string keys).\n" +
                violations.joinToString("\n")
        ) {
            violations shouldBe emptyList()
        }
    }

    test("card definitions construct effects/costs via the Effects/Costs facades, not raw types") {
        val violations = mutableListOf<String>()

        SetSourceRoots.definitionFiles().forEach { path ->
            stripCommentsAndImports(path.readText()).forEachIndexed { idx, line ->
                // The specific hints come first; one report per line is enough.
                forbidden.firstOrNull { (regex, _) -> regex.containsMatchIn(line) }?.let { (regex, hint) ->
                    val rel = SetSourceRoots.relativize(path)
                    violations += "$rel:${idx + 1}  →  use $hint instead of `${regex.find(line)!!.value}`"
                }
            }
        }

        withClue(
            "Card definitions must go through the Effects.*/Costs.* facades (SDK review §2.3).\n" +
                violations.joinToString("\n")
        ) {
            violations shouldBe emptyList()
        }
    }
})

/**
 * Returns the file's lines with block comments, line comments, and `import` declarations blanked
 * out (line numbers preserved), so the scan only sees executable code.
 */
internal fun stripCommentsAndImports(source: String): List<String> {
    val out = ArrayList<String>()
    var inBlock = false
    for (raw in source.lines()) {
        if (raw.trimStart().startsWith("import ")) {
            out += ""
            continue
        }
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (inBlock) {
                if (i + 1 < raw.length && raw[i] == '*' && raw[i + 1] == '/') {
                    inBlock = false; i += 2
                } else i++
            } else {
                if (i + 1 < raw.length && raw[i] == '/' && raw[i + 1] == '*') {
                    inBlock = true; i += 2
                } else if (i + 1 < raw.length && raw[i] == '/' && raw[i + 1] == '/') {
                    break
                } else {
                    sb.append(raw[i]); i++
                }
            }
        }
        out += sb.toString()
    }
    return out
}
