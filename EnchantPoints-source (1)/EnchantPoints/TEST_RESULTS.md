# Test results

## What I could and could not do
My sandbox has a Java 21 compiler but **no internet**, so Gradle/Loom could not download Minecraft 1.21.11, Yarn mappings or
Fabric API. Therefore: **no jar was built, and nothing was run inside Minecraft.** I will not claim otherwise.

## Actually executed here (core logic, `CoreSelfTest`): 48 passed, 0 failed
Covers the cost rules and the disable-selection rule that all acceptance tests depend on.

| Spec test | Verified how | Result |
|---|---|---|
| A  Normal inventory 7+4+3 = 14/20 | cost math | PASS |
| B  Exactly at limit 10+10 = 20/20, nothing disabled | cost math | PASS |
| C  19 + new 8-pt item = 27/20 -> only the new item selected | selector | PASS |
| F  Drop to 19/20 -> nothing disabled | selector | PASS |
| G  Lower max 30->20 at 25 pts -> newest item(s) selected, deterministic | selector | PASS |
| H  Raise max back -> nothing disabled | selector | PASS |
| J  Sharpness X=20, Protection X=15, Lunge X=20, Breach X=40, Unbreaking X=10, Mending fixed 2, Sharpness 255 unclipped | cost math | PASS |
| Sample sword (Sharp X + Unb V + KB II + Mending) = 29 | cost math | PASS |
| Config validation (overlap, duplicates, bad ids, negative max) reports clear errors | validate() | PASS |

## NOT verified (needs a real build + game; use this as your manual checklist)
| Test | What to do in game |
|---|---|
| D  Disabled weapon | go over limit, hit a mob with the disabled sword / mace / shoot a disabled bow: no damage |
| E  Disabled armor | wear over-limit armor: it moves to inventory (or stops giving armor value if inventory is full) |
| C/F in game | pick up / drop items, watch the chat message and the item working again without relog |
| I  Anvil bypass | at 18/20 do an anvil combine worth +6: result slot empties + message |
| K  Item transfer | drop a disabled item, other player picks it up: judged against their budget |
| L  Persistence | `/setenchantpoint 30`, restart, `/setenchantpoint get` |
| GUI | open inventory: counter above the panel, green/yellow/red, "N OVER" when over |
| Syntax | all files were parsed by javac; the only errors are unresolved Minecraft/Fabric symbols (expected offline) |

## Raw self-test output
```
== Defaults validate ==
PASS  default config errors = []
== Spec cost tables (Test J) ==
PASS  Mending I = 2
PASS  Mending V (fixed, not per level) = 2
PASS  Lunge 1 = 2
PASS  Lunge 2 = 4
PASS  Lunge 5 = 10
PASS  Lunge 10 = 20
PASS  Breach 1 = 4
PASS  Breach 2 = 8
PASS  Breach 5 = 20
PASS  Breach 10 = 40
PASS  Protection 1 = 1
PASS  Protection 4 = 4
PASS  Protection 5 = 5
PASS  Protection 6 = 7
PASS  Protection 7 = 9
PASS  Protection 8 = 11
PASS  Protection 10 = 15
PASS  Sharpness 1 = 1
PASS  Sharpness 3 = 3
PASS  Sharpness 4 = 5
PASS  Sharpness 5 = 7
PASS  Sharpness 7 = 11
PASS  Sharpness 8 = 14
PASS  Sharpness 9 = 17
PASS  Sharpness 10 = 20
PASS  Unbreaking III = 3
PASS  Knockback II = 2
PASS  Feather Falling IV = 4
PASS  Efficiency V = 5
PASS  Unbreaking X = 10
PASS  Efficiency X = 10
PASS  Sharpness 255 not clipped (3+8+248*3) = 755
PASS  Sample sword (Sharp X + Unb V + KB II + Mending) = 29
== Test A / B totals ==
PASS  Test A 7+4+3 = 14
PASS  Test B 10+10 = 20
PASS  Sec.5 Sharp X + Unb X over by 10 = 30
== Test C: 19 pts + new 8-pt item => 27/20, new item disabled ==
PASS  total = 27
PASS  disabled = only new item = [5]
== Test F: drop items, back to 19 => nothing disabled ==
PASS  total = 19
PASS  disabled = []
== Test G: lower max 30 -> 20 while holding 25 ==
PASS  max 30 nothing disabled = []
PASS  max 20 disables newest only (25-7=18<=20) = [3]
PASS  needs 2 items: 25 -> drop 5 (20) ok = [3]
PASS  24 -> 22 still >20, also disable next newest = [3, 2]
== Test H: raise max 20 -> 30 with total 25 ==
PASS  re-enabled = []
== Determinism ==
PASS  same input same output = [3, 2]
== Config validation reports errors ==
      error: maxPoints must be >= 0 (was -1)
      error: 'minecraft:lunge' is defined in more than one cost section; it would be ambiguous
      error: perLevelCosts: 'NotAnId' is not a valid identifier (expected namespace:path)
      error: tieredCosts.minecraft:sharpness: tiers overlap (4-7 and 5-9)
      error: tieredCosts.minecraft:sharpness: tiers overlap (5-9 and 8-999)
PASS  4+ errors detected = true

48 passed, 0 failed
```
