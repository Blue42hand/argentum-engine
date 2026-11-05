package com.wingedsheep.ai.arena

import com.wingedsheep.ai.engine.buildSeededSealedDeck
import com.wingedsheep.ai.engine.safeFallbackAction
import com.wingedsheep.ai.insight.GameLogWriter
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.AttackMode
import com.wingedsheep.sdk.model.Rarity
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Plays AI-vs-AI games with sealed decks from *random sets* (a different set per seat) and writes a
 * human-readable log per game, for reading where the AI misplays. Off unless `-DgameLog=true`.
 *
 * `-DgameLogSeats=N` (default 2) seats N players. With more than two it is a Free-for-All game in
 * [com.wingedsheep.sdk.core.Format.Standard].
 *
 * `-DgameLogAttackMode=MODE` picks the multiplayer attack rule, case-insensitive:
 * - `LEFT` (default) — attack left (CR 803.1a): each creature may attack only the opponent seated to
 *   its controller's left. This is what the FFA lobby starts a non-Commander game with
 *   (`TournamentLobby`; only Commander pods default to attacking multiple players).
 * - `RIGHT` — attack right (CR 803.1b).
 * - `MULTIPLE`, or its alias `ALL` — attack multiple players (CR 802): every opponent is a defending
 *   player and each attacker picks which one it attacks.
 * Two-player games ignore it — the sole opponent is the only defender either way.
 *
 * The agent defaults to `live` ([com.wingedsheep.ai.engine.AiProfile.LIVE]) — the profile real
 * players face, carrying every shipped misplay fix — so the logs don't resurface problems an older
 * candidate profile still has but the live AI no longer does.
 *
 * ```
 * just ai-game-logs 24                     # or a 4-player FFA: just ai-game-logs 1 20261013 live 4
 * scripts/gradle-locked :ai:test --tests "*.GameLogBenchmark" -DgameLog=true \
 *     -DgameLogGames=20 -DgameLogAgent=live -DgameLogSeats=2 -DgameLogDir=/tmp/logs
 * ```
 */
class GameLogBenchmark : FunSpec({
    val enabled = System.getProperty("gameLog") == "true"

    test("game logs across random sets").config(enabled = enabled) {
        val games = System.getProperty("gameLogGames")?.toIntOrNull() ?: 20
        val seed = System.getProperty("gameLogSeed")?.toLongOrNull() ?: 20261009L
        val agent = ArenaAgents.resolve(System.getProperty("gameLogAgent") ?: "live")
        val seats = (System.getProperty("gameLogSeats")?.toIntOrNull() ?: 2).also {
            require(it >= 2) { "gameLogSeats must be at least 2, was $it" }
        }
        val attackMode = parseAttackMode(System.getProperty("gameLogAttackMode"))
        val outDir = File(System.getProperty("gameLogDir") ?: "build/game-logs").apply { mkdirs() }

        val eligible = MtgSetCatalog.all.filter { set ->
            !set.incomplete && set.cards.count { !it.typeLine.isBasicLand && it.metadata.rarity == Rarity.COMMON } >= 40 &&
                set.cards.any { it.metadata.rarity == Rarity.RARE }
        }
        println("Eligible sets: ${eligible.size}")
        val registry = harnessRegistry(MtgSetCatalog.all)

        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtMost(8))
        val futures = (1..games).map { g ->
            pool.submit<String> {
                val rng = Random(seed * 1000 + g)
                val sets = List(seats) { eligible[rng.nextInt(eligible.size)] }
                val decks = sets.map { buildSeededSealedDeck(it.cards, rng) }
                val log = StringBuilder()
                log.appendLine("GAME $g  seed=${seed * 1000 + g}  agent=${agent.name}  seats=$seats" +
                    if (seats > 2) "  (free-for-all, attack $attackMode)" else "")
                sets.forEachIndexed { i, s ->
                    log.appendLine("Seat$i deck (${s.code} ${s.displayName}): " +
                        decks[i].cards.groupingBy { it }.eachCount().entries.joinToString(", ") { "${it.value}x ${it.key}" })
                }
                val summary = try {
                    playLogged(registry, agent, decks, seed * 1000 + g, log, attackMode)
                } catch (e: Throwable) {
                    "EXCEPTION ${e::class.simpleName}: ${e.message}".also { log.appendLine(it) }
                }
                File(outDir, "game-%02d.log".format(g)).writeText(log.toString())
                "game $g ${sets.joinToString(" vs ") { it.code }}: $summary"
            }
        }
        futures.forEach { println(it.get()) }
        pool.shutdown()
        println("Logs in ${outDir.absolutePath}")
    }
})

/**
 * `-DgameLogAttackMode`: `LEFT` when unset (the FFA lobby's non-Commander default), `ALL` as an
 * alias for [AttackMode.MULTIPLE], case-insensitive. Anything else fails loudly.
 */
internal fun parseAttackMode(raw: String?): AttackMode {
    val value = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return AttackMode.LEFT
    if (value == "ALL") return AttackMode.MULTIPLE
    return AttackMode.entries.firstOrNull { it.name == value }
        ?: error("gameLogAttackMode must be one of LEFT, RIGHT, MULTIPLE, ALL; was $raw")
}

private fun playLogged(
    registry: CardRegistry,
    agent: ArenaAgent,
    decks: List<com.wingedsheep.sdk.model.Deck>,
    seed: Long,
    log: StringBuilder,
    attackMode: AttackMode,
): String {
    val processor = ActionProcessor(registry)
    val enumerator = LegalActionEnumerator.create(registry)
    val init = GameInitializer(registry).initializeGame(
        GameConfig(
            players = decks.mapIndexed { seat, deck -> PlayerConfig("Seat$seat", deck) },
            skipMulligans = true,
            startingPlayerIndex = 0,
            seed = seed,
            // CR 802 / 803 — which opponents an attacker may pick; see `-DgameLogAttackMode`.
            attackMode = attackMode,
        )
    )
    val seatIds = init.state.turnOrder
    val decklists = seatIds.mapIndexed { i, id -> id to decks[i].cards.groupingBy { it }.eachCount() }.toMap()
    val players = seatIds.map { agent.createPlayer(registry, it, decklists) }
    val seatOf = seatIds.withIndex().associate { (i, id) -> id to i }
    val writer = GameLogWriter(seatIds, enumerator, out = log)

    var state: GameState = processor.beginGame(init.state, init.events).state
    var actions = 0
    var illegal = 0
    // The caps are per seat: a turn number counts every player's turn, so a pod needs proportionally
    // more of them to reach the same round (30 rounds; 60 turns heads-up, as before).
    val maxTurns = 30 * decks.size
    val maxActions = 3000 * decks.size
    while (!state.gameOver && state.turnNumber < maxTurns && actions < maxActions) {
        writer.turnIfNew(state)
        val decision = state.pendingDecision
        if (decision != null) {
            actions++
            val response = players[seatOf[decision.playerId]!!].respondToDecision(state, decision)
            writer.decision(state, decision, response)
            val r = processor.process(state, SubmitDecision(decision.playerId, response)).result
            if (r.error != null) { log.appendLine("  DECISION ERROR ${r.error}"); return "decisionError" }
            state = r.state
            continue
        }
        val pp = state.priorityPlayerId ?: return "noPriority"
        actions++
        val action = players[seatOf[pp]!!].chooseAction(state)
        if (action is PassPriority) {
            // A pass in our own main phase with an empty stack while holding playable cards is the
            // most common "did nothing" misplay — record what was on offer.
            if (pp == state.activePlayerId && state.stack.isEmpty() && state.step.name.contains("MAIN")) {
                writer.passWithOffers(state, pp)
            }
        } else {
            writer.action(state, pp, action)
        }
        val r = processor.process(state, action).result
        state = if (r.error != null) {
            illegal++
            log.appendLine("  ILLEGAL: ${r.error}")
            processor.process(state, safeFallbackAction(state, pp, enumerator)).result.state
        } else r.state
    }
    val winner = state.winnerId?.let { seatOf[it] }
    log.appendLine("\n=== END turn=${state.turnNumber} winner=${winner?.let { "Seat$it" } ?: "none"} life=${seatIds.map { state.lifeTotal(it) }} illegal=$illegal ===")
    writer.board(state)
    return "winner=${winner ?: "none"} turns=${state.turnNumber} illegal=$illegal"
}
