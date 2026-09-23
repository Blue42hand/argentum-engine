package com.wingedsheep.gameserver.persistence

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.lobby.LobbyGameMode
import com.wingedsheep.gameserver.lobby.LobbyPlayerState
import com.wingedsheep.gameserver.lobby.LobbyState
import com.wingedsheep.gameserver.lobby.TournamentFormat
import com.wingedsheep.gameserver.lobby.TournamentLobby
import com.wingedsheep.gameserver.cube.ResolvedCube
import com.wingedsheep.gameserver.persistence.dto.*
import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.gameserver.sealed.SealedPlayerState
import com.wingedsheep.gameserver.sealed.SealedSession
import com.wingedsheep.gameserver.sealed.SealedSessionState
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.gameserver.session.PlayerSession
import com.wingedsheep.gameserver.tournament.PlayerStanding
import com.wingedsheep.gameserver.tournament.TournamentManager
import com.wingedsheep.gameserver.tournament.TournamentMatch
import com.wingedsheep.gameserver.tournament.TournamentRound
import com.wingedsheep.sdk.model.EntityId

// ============================================================================
// TournamentLobby Conversion
// ============================================================================

/** Converts a TournamentLobby to its persistent representation. */
fun TournamentLobby.toPersistent(): PersistentTournamentLobby {
    return PersistentTournamentLobby(
        lobbyId = lobbyId,
        setCodes = setCodes,
        setNames = setNames,
        format = format.name,
        rules = rules.name,
        boosterCount = boosterCount,
        maxPlayers = maxPlayers,
        pickTimeSeconds = pickTimeSeconds,
        gamesPerMatch = gamesPerMatch,
        state = state.name,
        hostPlayerId = hostPlayerId?.value,
        players = players.mapKeys { it.key.value }.mapValues { (_, playerState) ->
            PersistentLobbyPlayer(
                playerId = playerState.identity.playerId.value,
                playerName = playerState.identity.playerName,
                token = playerState.identity.token,
                cardPoolNames = playerState.cardPool.map { it.name },
                currentPackNames = playerState.currentPack?.map { it.name },
                packQueueNames = playerState.packQueue.map { pack -> pack.map { it.name } },
                submittedDeck = playerState.submittedDeck,
                currentSpectatingGameId = playerState.identity.currentSpectatingGameId,
                isAi = playerState.identity.isAi,
                aiModelOverride = playerState.identity.aiModelOverride,
                aiControllerSpec = playerState.identity.aiControllerSpec,
                submittedSideboard = playerState.submittedSideboard
            )
        },
        cubeName = cube?.name,
        cubeCardNames = cube?.cards?.map { it.name }.orEmpty(),
        cubeBasicLandSetCode = cube?.basicLandSetCode,
        cubePackSize = cube?.packSize,
        cubeDealerRemainingCardNames = cubeDealerRemainingCards().map { it.name },
        cubePoolPlay = cubePoolPlay,
        bannedCardNames = bannedCardNames,
        includedSetProducts = includedSetProducts,
        currentPackNumber = currentPackNumber,
        currentPickNumber = currentPickNumber,
        playerOrder = getPlayerOrderForPersistence(),
        winstonMainDeckNames = winstonMainDeck.map { it.name },
        winstonPileNames = winstonPiles.map { pile -> pile.map { it.name } },
        winstonActivePlayerIndex = winstonActivePlayerIndex,
        winstonCurrentPileIndex = winstonCurrentPileIndex,
        winstonSeenCardNames = winstonSeenCards.mapKeys { it.key.value }.mapValues { it.value.toList() },
        completedAt = completedAt,
        isPublic = isPublic,
        aiAssistEnabled = aiAssistEnabled,
        gameMode = gameMode.name,
        attackMode = attackMode.name,
        randomTeams = randomTeams,
        teamAssignments = teamAssignments.mapKeys { it.key.value },
        ffaGameSessionId = ffaGameSessionId,
        ffaGamesPlayed = ffaGamesPlayed
    )
}

private fun TournamentLobby.getPlayerOrderForPersistence(): List<String> = getPlayerOrder().map { it.value }

/** Restores a TournamentLobby from its persistent representation. */
fun restoreTournamentLobby(
    persistent: PersistentTournamentLobby,
    cardRegistry: CardRegistry,
    boosterGenerator: BoosterGenerator
): Pair<TournamentLobby, List<PlayerIdentity>> {
    val format = TournamentFormat.valueOf(persistent.format)
    val lobby = TournamentLobby(
        lobbyId = persistent.lobbyId,
        setCodes = persistent.setCodes,
        setNames = persistent.setNames,
        boosterGenerator = boosterGenerator,
        format = format,
        rules = persistent.rules
            ?.let { runCatching { com.wingedsheep.sdk.core.GameRules.valueOf(it) }.getOrNull() }
            ?: com.wingedsheep.sdk.core.GameRules.inferred(
                commanderPackShape = format.isCommanderFormat,
                deckFormat = null,
            ),
        boosterCount = persistent.boosterCount,
        maxPlayers = persistent.maxPlayers,
        pickTimeSeconds = persistent.pickTimeSeconds,
        gamesPerMatch = persistent.gamesPerMatch,
        isPublic = persistent.isPublic,
        aiAssistEnabled = persistent.aiAssistEnabled,
        gameMode = runCatching { LobbyGameMode.valueOf(persistent.gameMode) }
            .getOrDefault(LobbyGameMode.TOURNAMENT),
        attackMode = runCatching { com.wingedsheep.sdk.core.AttackMode.valueOf(persistent.attackMode) }
            .getOrDefault(com.wingedsheep.sdk.core.AttackMode.MULTIPLE),
        randomTeams = persistent.randomTeams
    )
    lobby.bannedCardNames = persistent.bannedCardNames
    lobby.includedSetProducts = persistent.includedSetProducts
    if (persistent.cubeName != null && persistent.cubeBasicLandSetCode != null && persistent.cubePackSize != null) {
        val cubeCards = persistent.cubeCardNames.mapNotNull(cardRegistry::getCard)
        lobby.configureCube(
            ResolvedCube(
                name = persistent.cubeName,
                cards = cubeCards,
                basicLandSetCode = persistent.cubeBasicLandSetCode,
                packSize = persistent.cubePackSize,
            )
        )
        lobby.cubePoolPlay = persistent.cubePoolPlay
        if (persistent.cubeDealerRemainingCardNames.isNotEmpty()) {
            lobby.restoreCubeDealer(persistent.cubeDealerRemainingCardNames.mapNotNull(cardRegistry::getCard))
        }
    }
    lobby.ffaGameSessionId = persistent.ffaGameSessionId
    lobby.ffaGamesPlayed = persistent.ffaGamesPlayed

    val playerIdentities = mutableListOf<PlayerIdentity>()
    for ((playerIdStr, persistentPlayer) in persistent.players) {
        val playerId = EntityId(playerIdStr)
        val identity = PlayerIdentity(
            token = persistentPlayer.token,
            playerId = playerId,
            playerName = persistentPlayer.playerName,
            isAi = persistentPlayer.isAi,
            aiModelOverride = persistentPlayer.aiModelOverride,
            aiControllerSpec = persistentPlayer.aiControllerSpec,
        ).also {
            it.currentLobbyId = persistent.lobbyId
            it.currentSpectatingGameId = persistentPlayer.currentSpectatingGameId
        }
        playerIdentities.add(identity)

        val cardPool = persistentPlayer.cardPoolNames.mapNotNull(cardRegistry::getCard)
        val currentPack = persistentPlayer.currentPackNames?.mapNotNull(cardRegistry::getCard)
        val packQueue = persistentPlayer.packQueueNames.map { packNames ->
            packNames.mapNotNull(cardRegistry::getCard)
        }.toMutableList()

        lobby.players[playerId] = LobbyPlayerState(
            identity = identity,
            cardPool = cardPool,
            currentPack = currentPack,
            packQueue = packQueue,
            submittedDeck = persistentPlayer.submittedDeck,
            submittedSideboard = persistentPlayer.submittedSideboard
        )
    }

    lobby.setTeamAssignments(persistent.teamAssignments.mapKeys { EntityId(it.key) })
    lobby.restoreFromPersistence(
        state = LobbyState.valueOf(persistent.state),
        hostPlayerId = persistent.hostPlayerId?.let { EntityId(it) },
        completedAt = persistent.completedAt
    )
    lobby.restoreDraftState(
        currentPackNumber = persistent.currentPackNumber,
        currentPickNumber = persistent.currentPickNumber,
        playerOrder = persistent.playerOrder.map { EntityId(it) }
    )
    if (persistent.format == "WINSTON_DRAFT" && persistent.winstonMainDeckNames.isNotEmpty()) {
        val mainDeck = persistent.winstonMainDeckNames.mapNotNull(cardRegistry::getCard)
        val piles = persistent.winstonPileNames.map { pileNames -> pileNames.mapNotNull(cardRegistry::getCard) }
        lobby.restoreWinstonDraftState(
            mainDeck = mainDeck,
            piles = piles,
            activePlayerIndex = persistent.winstonActivePlayerIndex,
            currentPileIndex = persistent.winstonCurrentPileIndex,
            seenCards = persistent.winstonSeenCardNames.mapKeys { EntityId(it.key) }.mapValues { it.value.toMutableSet() }
        )
    }

    return lobby to playerIdentities
}

// ============================================================================
// SealedSession Conversion (Legacy 2-player format)
// ============================================================================

fun SealedSession.toPersistent(): PersistentSealedSession {
    return PersistentSealedSession(
        sessionId = sessionId,
        setCodes = setCodes,
        setNames = setNames,
        state = state.name,
        players = players.mapKeys { it.key.value }.mapValues { (_, playerState) ->
            PersistentSealedPlayer(
                playerId = playerState.session.playerId.value,
                playerName = playerState.session.playerName,
                cardPoolNames = playerState.cardPool.map { it.name },
                submittedDeck = playerState.submittedDeck,
                submittedSideboard = playerState.submittedSideboard
            )
        }
    )
}

// ============================================================================
// TournamentManager Conversion
// ============================================================================

fun TournamentManager.toPersistent(lobbyId: String): PersistentTournament {
    return PersistentTournament(
        lobbyId = lobbyId,
        standings = getStandingsForPersistence().mapKeys { it.key.value }.mapValues { (_, standing) ->
            PersistentStanding(
                playerId = standing.playerId.value,
                playerName = standing.playerName,
                wins = standing.wins,
                losses = standing.losses,
                draws = standing.draws,
                gamesWon = standing.gamesWon,
                gamesLost = standing.gamesLost,
                lifeDifferential = standing.lifeDifferential
            )
        },
        rounds = getRoundsForPersistence().map { round ->
            PersistentRound(
                roundNumber = round.roundNumber,
                matches = round.matches.map { match ->
                    PersistentMatch(
                        player1Id = match.player1Id.value,
                        player2Id = match.player2Id?.value,
                        gameSessionId = match.gameSessionId,
                        winnerId = match.winnerId?.value,
                        isDraw = match.isDraw,
                        isComplete = match.isComplete,
                        player1GameWins = match.player1GameWins,
                        player2GameWins = match.player2GameWins,
                        isSimulated = match.isSimulated
                    )
                }
            )
        },
        currentRoundIndex = getCurrentRoundIndexForPersistence(),
        totalRounds = totalRounds,
        gamesPerMatch = getGamesPerMatchForPersistence(),
        playerIds = playerIds.map { it.value }
    )
}

fun restoreTournamentManager(persistent: PersistentTournament): TournamentManager {
    val players = persistent.playerIds.map { playerIdStr ->
        val standing = persistent.standings[playerIdStr]
            ?: throw IllegalStateException("Standing not found for player $playerIdStr")
        EntityId(playerIdStr) to standing.playerName
    }

    val tournament = TournamentManager(
        lobbyId = persistent.lobbyId,
        players = players,
        gamesPerMatch = persistent.gamesPerMatch
    )

    val rounds = persistent.rounds.map { persistentRound ->
        TournamentRound(
            roundNumber = persistentRound.roundNumber,
            matches = persistentRound.matches.map { persistentMatch ->
                TournamentMatch(
                    player1Id = EntityId(persistentMatch.player1Id),
                    player2Id = persistentMatch.player2Id?.let { EntityId(it) },
                    gameSessionId = persistentMatch.gameSessionId,
                    winnerId = persistentMatch.winnerId?.let { EntityId(it) },
                    isDraw = persistentMatch.isDraw,
                    isComplete = persistentMatch.isComplete,
                    player1GameWins = persistentMatch.player1GameWins,
                    player2GameWins = persistentMatch.player2GameWins,
                    isSimulated = persistentMatch.isSimulated
                )
            }
        )
    }

    val standings = persistent.standings.mapKeys { EntityId(it.key) }.mapValues { (_, persistentStanding) ->
        PlayerStanding(
            playerId = EntityId(persistentStanding.playerId),
            playerName = persistentStanding.playerName,
            wins = persistentStanding.wins,
            losses = persistentStanding.losses,
            draws = persistentStanding.draws,
            gamesWon = persistentStanding.gamesWon,
            gamesLost = persistentStanding.gamesLost,
            lifeDifferential = persistentStanding.lifeDifferential
        )
    }

    tournament.restoreFromPersistence(
        rounds = rounds,
        standings = standings,
        currentRoundIndex = persistent.currentRoundIndex
    )
    return tournament
}
