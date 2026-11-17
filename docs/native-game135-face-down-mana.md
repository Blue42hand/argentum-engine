# Face-down mana source consistency

Closed evaluation Game134 callback6050 exposed a payment inconsistency. The acting
seat's manifested Talisman was a face-down creature and its printed mana abilities
were absent from the manual menu. The automatic inventory nevertheless advertised
its printed white mana. AutoPay tapped it and a colorless land to cast Seal Away,
charged the hidden Talisman's damage, and exiled an opposing Haughty Djinn.

The repair applies the existing face-down component to printed ability lookup in
both ordinary and bonus mana inventories. A printed land hidden face down also
cannot use the old printed-land fallback. Static/runtime grants and intrinsic mana
from later projected basic land types are considered independently. The payment
side-effect lookup excludes the same unavailable printed abilities, so a legitimate
white mana grant on the hidden Talisman does not inherit its printed pain effect.

The boundary is printed ability availability, not a blanket prohibition on mana
from face-down permanents. The engine tests cover ordinary artifact/creature/land
sources, face-up recovery, a rejected unfunded Seal Away, static and runtime grants,
Ashaya's projected Forest type, summoning sickness, sacrifice/tap-permanent bonus
inventories, and a later explicit-only sacrifice grant.

The official Comprehensive Rules effective November6,2026 were checked from
[the published text](https://media.wizards.com/2026/downloads/MagicCompRules%2020261106.txt).
Rules708.2 and708.2a describe the face-down characteristics. Later continuous
changes can still add abilities or land types; the tests preserve those cases.

No SDK/card-definition/serialization changes are needed. Projection and the manual
ability menu already suppress printed face-down abilities. The server and client
consume the corrected affordability/source inventories through existing DTOs; no
new UI is introduced. The repair adds constant-time component checks and reuses
cached projection, without another battlefield walk.

Native unit/card-scenario results and the original captured-state comparison are
external evaluation receipts. They prove source/payment consistency on their
stated cases, not a natural completed game. Frozen broad library facts and runtime
policy source remain separate; a new matched evaluation must pin this native
revision and regenerate public facts from its actual registry before launch.
