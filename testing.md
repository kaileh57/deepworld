# testing

what's in, what's simplified, how to check it. op yourself first.

## setup

```
scripts/deploy.sh
~/deepworld-server/start.sh
```

join `localhost`, accept the pack. `/dw selftest` runs the built-in checks.

## 2 the column

- surface at y=1200. ten layers down to y=0, nether biomes below.
- try: `/tp @s 0 1050 0`, then 950, 850, 750, 650, 520, 380, 220, 80, -60. fog, music and rock change per layer.
- lava sea at y≈480-540. fortress: `/dw fortress` prints one below y=0.
- `/dw slice 0 -300 600` writes a cross-section png to the server folder.

## 3 mining

- pickaxe tiers gate rock: granite needs copper, calcite iron, blackstone/basalt steel, gloomstone diamond, mantle rock mantle alloy, scorched rock cobalt steel.
- new ores: tin (l8), rock salt (l7), resonant amethyst (l6), mantle iron + natron (l4), cobalt (l3).
- forge: bricks around a blast furnace. inputs left, fuel bottom, output right. steel = 2 iron + coal + flux. flux = rock salt + bone meal.
- heat starts below y=600. meter on the action bar. four heat armor sets, cobalt set = 4 protection, fire resistance = 2.
- simplified: forge menu is a plain chest layout for now.

## 4 cooking

- stations: clay/iron/mantle pot, iron skillet, brick oven, smokehouse, crock, drying rack, still, ice box, spit, cutting board, mill, churn, mortar, cheese press. all craftable.
- heated stations read the block below: campfire low, soul campfire soul, lit furnace or coal block medium, magma/lava high, ancient debris or lit hellforge extreme.
- put 2-6 ingredients in, press cook. when the stick glows, click stir. hits raise quality, misses lower it. take the dish out within 30s or it burns.
- known recipes make named dishes; anything else makes a generic one (hearty stew etc.). 43 dishes and drinks, see the wiki.
- three meal slots plus one drink slot, on the action bar. same family refreshes. natural regen is off; fresh food heals.
- food in your inventory spoils while you're online, 10x faster below y=600. preserved food doesn't. spoiled food stays edible but makes you sick.
- forage: wild rice/chili/mint/tea/coffee/beans/oats/herbs on the surface, one plant per cave layer (truffle, glowcap, ash pepper, ember root, hush moss, forge fig).
- mob drops: glow squid → blind eel, strider → lava fish, hoglin → hoglin chop, bone blocks → fossil marrow, amethyst clusters → geode nectar.
- simplified: menus are chest layouts with item buttons. chests also stop spoilage, not just the ice box. feasts and the tasting menu come later.

## 5 transit

- rail speed comes from the block under the rail: copper 16 m/s, gold 32, cobalt steel block 48. carts speed up on their own over those beds. straight runs only at top speed, curves can derail.
- lift: place lift stations at the same x/z on different floors. click one, pick a floor. the shaft between them must be clear. 8 m/s.
- paved blocks (gravel, cobble, packed mud, paths, stone bricks...) give speed i on foot, speed ii to horses. grass you walk over a lot becomes a path.
- the deepline terminal sits at spawn (0, 1212, 0). lines run east 250 (depot), west 450 (worker camp), south 1000 and north 2000 (stations). tunnels, bridges, gaps every 500, collapses, signal cabins, derelict chest carts.
- `/dw map 0 0 320` writes a top-down png.
- simplified: no junction routing, signals or timetables yet. lift has no cable cost and no express car. shoring blocks are vanilla logs/fences/walls/bars (used by cave-ins in phase 8).

## 6 disciplines and crafts

- guild journal (book + 4 gold nuggets): eight disciplines, milestones with progress, focus up to two (one change per in-game day).
- focus perks: delver +1 heat protection, cook +1 dish quality, grower double harvest, smith 2x forge speed, alchemist more charge drops and still output, engineer 12 m/s lifts, archivist doubled dowsing range, hunter extra meat from deep kills.
- gourmet milestone (10 masterwork dishes) gives everyone a fourth meal slot.
- farming: plant any forage ingredient on a block (sprout → mature). surface crops only grow in their biomes in daylight, cave crops only at their depth. right-click a mature plant to harvest. compost +1 stage, sulfur fertilizer +2 on cave crops, growth tonic +3. bone meal does nothing to them.
- fishing: bonus blind eel at y 900-1000, crystal minnow at y 700-800 (bring water).
- husbandry: bred animals inherit plump, rich milk, fast, hardy, calm with small mutations. check with the guild journal on an animal. rich-milk cows give rich milk.
- smithing bench (cauldron on smithing table): quench with water, rendered fat (tough), brine (keen), lava (tempered), ghast tear cordial (pristine). socket resonant shard (ore hum), lapis (xp), diamond (durability), quartz (mining speed), echo shard.
- alchemy: brewing stands take sulfur as fuel. still makes mint oil, ember extract, glowcap essence, tonics. rendered fat and brine from the pot. saltpeter drops from tuff.
- charges: black powder (3x3x3, half drops), shaped (12-deep bore), mining (ore only), demolition (5x5x3, no drops). right-click a block, run.
- simplified: no truffle pig, pack mule, cave goat. hardy/calm traits are minor. lava fishing isn't in (lava fish come from striders). echo shard socket has no effect yet.
