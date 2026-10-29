# Academy Manufactor implementation

Academy Manufactor replaces each qualifying prospective token with a Clue, Food and Treasure.
A pending token event holds homogeneous batches and replacement identities; each chosen replacement
applies once across the event. Replacements are selected before native token-entry processing.

| Layer | Normal path | Edge path |
| --- | --- | --- |
| SDK | Serializable ReplaceTokenCreationWithTokens with registry keys and EventPattern | Empty outputs rejected; existing filter/controller data round trips |
| Executor | Registry intercepts native token instructions; materializes final coalesced batches | Added Food can make an initially unrelated event eligible; zero counts skip replacements |
| Triggers | Native ZoneChangeEvents feed existing Settler/TriggerDetector | Investigation emits one event only after its Clue event completes, including pauses; replacement Clues add no investigations |
| Projection | Prospective characteristics use base copiable values; replacement controller uses projected state | Face-down/ability-removed sources excluded; grants survive their anchor leaving |
| Continuations | Option/yes-no answers save the whole pending event and applied identities | Native entry choices retain remaining batches; CREATED_TOKENS reaches the enclosing pipeline |
| Cleanup | No new duration state | Existing once-per-turn attached-copy marker and granted-effect cleanup remain authoritative |
| Server/masking | Existing decisions and token events | No new hidden information or DTO fields |
| Legal actions/client | Existing resolution option, yes/no and card-selection interfaces | No new actions or UI required |

The legacy token path remains active without a multi-kind replacement. With one active, native
custom/predefined, target/source/equipped/selected copies, Roles and random-definition tokens participate.
Selection happens first for chosen/random factories; entry choices happen after replacements.

Two Treasures with Took before Manufactor become three of each. Manufactor before Took gives two
Clues, three Foods, two Treasures. Two Manufactors produce three of each per original qualifying token.
Eighteen produce 129140163 of each while retaining only three batch records before materialization;
native engine token-count limits still govern concrete allocation.

Manual scenario: manual-scenarios/cards/a/academy-manufactor.json. Cast Brandywine Farmer to create Food
with both replacements present, or cast the second Manufactor first to exercise multiple instances.
The six Forests pay for both spells. The card has one replacement ability; all necessary pieces start
on the battlefield or are castable from hand.

New Assay vocabulary: ReplaceTokenCreationWithTokens. Grammar work remains a separate measured band.

Rules references: Wizards' [Modern Horizons 2 release notes](https://magic.wizards.com/en/news/feature/modern-horizons-2-release-notes-2021-06-04)
confirm the one/two/eighteen Manufactor counts and investigation distinction. The
[Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260619.pdf),
701.7b, distinguish creation-time characteristics from battlefield continuous effects; 614.5 and
616.1 govern replacement identity and choice. Wizards' [Bloomburrow release notes](https://magic.wizards.com/en/news/feature/bloomburrow-release-notes)
for token substitutions retain entry and delayed instructions while replacing token characteristics.
The generic emitter recognizes per-token replacement IR with exact supported artifact subtype filters
and Clue/Food/Treasure outputs; other shapes still decline to scaffolds.

Attached-copy order and yes/no choices name the attached permanent. Creation instructions are supplied to native creature factories before entry choices, so tapped/attacking status is already present during a pending entry decision. The replacement scan skips objects without replacement sources before computing text changes.
