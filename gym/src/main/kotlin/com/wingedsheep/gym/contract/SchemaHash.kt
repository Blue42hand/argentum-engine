package com.wingedsheep.gym.contract

/**
 * Schema/version identity for the Gym observation contract.
 *
 * Clients treat this as a fail-closed compatibility boundary. Changes to the
 * model-facing schema or to provenance semantics that affect durable hashes
 * must advance the value so artifacts from incompatible contracts cannot be
 * mistaken for equivalent observations/actions.
 */
object SchemaHash {
    const val CURRENT: String = "argentum-gym-contract@v1.7-semantic-state-provenance"
}
