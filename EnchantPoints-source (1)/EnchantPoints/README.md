# EnchantPoints (Fabric 1.21.11)

One shared enchantment-point budget for everything a player carries. Go over the limit and the newest items are
**temporarily disabled** (never deleted, never de-enchanted) until you are back under it.

> **Status: source complete, NOT yet compiled against Minecraft.** See `TEST_RESULTS.md` for exactly what was
> verified and what was not. Build it first (below) and fix any mapping drift listed under "First-build checklist".

## Build
Needs JDK 21 and internet access (Gradle downloads Minecraft, Yarn, Loom, Fabric API).

1. Check https://fabricmc.net/develop/ for 1.21.11 and put the real numbers in `gradle.properties`
   (`yarn_mappings`, `loader_version`, `loom_version`, `fabric_version`).
2. `gradle wrapper` (once, if you have Gradle installed) then `./gradlew build`  — or push to GitHub: `.github/workflows/build.yml` builds it for you.
3. Jar: `build/libs/enchantpoints-1.0.0.jar`

## Install
Put the jar in `mods/` on the **server** and on **every client** that should see the inventory counter.
Fabric API is required. Enforcement is 100 % server-side, so a client without the mod can still join and is still
restricted; it just won't see the counter.

## Counting
All items directly carried: main inventory + hotbar (once), 4 armor slots, offhand, enchanted books, and the stack on your
cursor. Shulker boxes, ender chests and other containers are not counted. Enchantments are read by registry id and real stored
level (no clipping at vanilla maximums), from both `enchantments` and `stored_enchantments`.

Default costs: Mending 2 (fixed, once) - Lunge 2/lvl - Breach 4/lvl - Protection 1/lvl up to V then 2/lvl -
Sharpness 1/lvl to III, 2/lvl IV-VII, 3/lvl VIII+ - everything else 1/lvl.

## Which items get disabled
If total <= max: nothing is disabled. If total > max: items are disabled **newest-acquired first** (ties: higher inventory
index first) until the points of the remaining enabled items fit. Disabled items still count toward the total.
On a fresh login, "age" is the item's creation order. Items arriving later (pickup, trade, command, other mods) are newest.
Disabled state is recomputed from the current holder every time, so a transferred item is judged by its new owner.

## What "disabled" does
- **Weapons / any held disabled item**: all damage dealt by it is cancelled (melee, sweep, crit, mace smash, arrows, bolts, tridents).
- **Tools**: cannot start/finish breaking blocks, cannot strip/till/path.
- **Bows/crossbows/tridents**: cannot start being used.
- **Armor**: moved into your inventory if there's room. If your inventory is completely full it stays worn but is hidden from
  armor/enchantment logic. It cannot be equipped by right-click while disabled.
- **Enchanting/anvil**: anvil results that would take you over the limit (and raise your points) are blocked with a message.
  Enchanting table: the guaranteed "clue" enchantment is priced and refused if it doesn't fit.

## Commands
| Command | Who | Does |
|---|---|---|
| `/setenchantpoint <n>` | op | set server-wide maximum (saved, applied to everyone online instantly) |
| `/setenchantpoint get` / `reload` | op | show max / reload `config/enchantpoints.json` (bad config is rejected, old one stays active) |
| `/setenchantpoint cost <id> <n>` | op | set linear per-level cost (not for fixed/tiered enchantments: edit the file) |
| `/enchantpoints` | everyone | your total / max / remaining or over-limit |
| `/enchantpoints <player>` | op | inspect someone else |

Admin = op level 2, or the node `enchantpoints.admin` if `fabric-permissions-api` (LuckPerms) is installed.

## Config (`config/enchantpoints.json`)
`maxPoints`, `defaultCostPerLevel`, `safetyCheckIntervalTicks`, `fixedCosts`, `perLevelCosts`, `tieredCosts`,
`disabling{armor,weapons,tools,rangedWeapons}`, `display{inventoryHud,actionBar,yellowAtPercent}`, `messages{...}`.
An enchantment may appear in only one of fixed/perLevel/tiered. Invalid values are reported, never silently fixed.

## Known limitations
- Enchanting table: random *extra* enchantments are rolled inside vanilla code and can't be priced beforehand; if they push
  you over, the item is simply disabled afterwards.
- Mace smash: the damage is cancelled, but vanilla's area-knockback/fall-reset side effects may still occur.
- Each enchanted stack carries a hidden `enchantpoints_id` in `minecraft:custom_data` (visible only with advanced tooltips / NBT tools).
- Inventory changes are detected by slot-click events + a poll every `safetyCheckIntervalTicks` (default 5), so pickups and
  other-mod changes can take up to ~0.25 s to register.
- If the inventory is 100 % full and disabled armor is still worn, dispenser-equipping armor onto that player could displace it.

## First-build checklist (things I could not verify offline; all are one-line fixes)
1. `gradle.properties` version numbers.
2. `PointsCommands.VANILLA_OP`: the vanilla permission API changed in 1.21.9+; adjust to the current "gamemaster" check if it doesn't compile.
3. `LivingEntityMixin`: confirm `getEquippedStack` is the method armor logic uses for players in 1.21.11 (if `PlayerEntity` overrides it, point the mixin there).
4. Armor inventory indices 36-39 (`PointsManager.ARMOR_BASE`) and `PlayerInventory.getEmptySlot()`.
5. Mixin field names in `ForgingScreenHandlerAccessor`, `AnvilScreenHandlerMixin`, `EnchantmentScreenHandlerMixin`, `HandledScreenAccessor`.
6. `DataComponentTypes.WEAPON` / `TOOL` / `EQUIPPABLE`, `NbtCompound.getLong(key, default)`.

## Running the core self-test (no Minecraft needed)
```
javac -d /tmp/out src/main/java/dev/enchantpoints/core/*.java src/test/java/dev/enchantpoints/core/CoreSelfTest.java
java -cp /tmp/out dev.enchantpoints.core.CoreSelfTest
```
