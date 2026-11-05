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

## 7 exploration

- landmarks, one kind per layer: miner's camp, drowned pump station, fossil dig, crystal cathedral, sulfur works, bridge pylons, sculk shrine, mantle forge, the gate. each has a chest; most hold deepline journal pages (12 total, the whole story).
- walking within 24 blocks of a landmark counts it as found (archivist milestones, logbook).
- pump station: right-click the dispenser with 4 copper ingots to drain the gallery around it.
- crystal cathedral: tune the four note blocks to the right notes and the vault wall shatters. the right notes differ per cathedral; there's no in-world hint yet besides trial and error.
- fossil dig: brush the suspicious gravel.
- survey kit (compass + copper + paper): layer, biome and known landmarks within 500 blocks.
- dowsing rod (resonant shard + sticks): right-click an ore to tune it, then hold it. it chimes louder and higher as you face the nearest one.
- expedition logbook (leather + book and quill): writes itself. layers, landmarks, pages, deaths.
- `/dw landmarks` lists generated landmarks.
- simplified: landmarks are small builds, not jigsaw structures. no explorer maps yet (phase 9 trades). cathedral hint isn't written.

## 8 hazards

each one can be turned off in `config/deepworld.json` (cave_ins, firedamp, cold_water, echoes, fumes, heat, magma_tides, darkness, sound, spoilage, tremors).

- cave-ins (y 600-1000): breaking blocks in a room 5+ wide with no logs, fences, walls, bars, chains or stairs within 4 blocks can drop the ceiling after a rumble. charges make it likely.
- firedamp (coal seams, y 800-900): some pockets hold gas. placing a torch, lantern, campfire or using flint and steel there explodes. hold a safety lamp to check the air. a ventilation fan within 12 blocks clears it.
- cold water (y 900-1000): swimming freezes you and drains hunger unless you wear any leather armor or ate something warming.
- echoes (y 700-800): mining calls monsters from 40 blocks unless you're hushed (hushed food or wool-wrapped boots) or using an echo-shard tool.
- fumes (sulfur springs, y 600-700): nausea and poison unless a fan is near or you ate something bitter.
- magma tides (lava sea): the sea rises and falls 5 blocks over two in-game days. tide shows on the action bar.
- darkness (y 300-450): torches burn out after 5 minutes. amethyst lamps (craftable) don't.
- sound: hushed ii food or an echo-shard tool keeps sculk sensors and wardens from hearing you.
- simplified: tides only move lava near players. firedamp pockets are invisible noise cells, not real gas blocks.

## 9 economy

- commissions board (craftable, put one at spawn): post a request with a reward (put the reward in the top row, one of the wanted item in the slot, set the count, post). others accept, then deliver; you collect what they brought from "collect deliveries". the deepline co-op posts up to 3 npc requests every half day.
- serving counter: the first person to open it owns it. owner view: 18 stock slots, the till, and 4 trades (example stack to sell on top, price below and right). everyone else sees a shop and buys straight from your stock, even while you're offline.
- villagers: trades are fixed per villager (rerolling the job block gives the same list). farmers no longer buy crops for emeralds; diamond gear is gone from smiths. new trades: regional seeds, spices, cookware, rails, lift stations, survey kits, journals, safety lamps, amethyst lamps.
- settlements: places where people spend 90+ minutes attract a wandering trader every half day and slowly gain lamp posts, benches and barrels. deep outposts get a trader that buys torches, ice and food for deep goods.
- simplified: no chef villager profession (the butcher sells spices instead). no player-minted currency.

## 10 living server

- feast table (dark oak slabs over fences): put up to 10 dishes in, hit serve. everyone within 8 blocks gets one "feast" meal with every buff merged, lasting 25% longer, plus 1 absorption heart per 4 dishes.
- tasting menu: serve 10 dishes that between them use a signature ingredient from every layer (list in `tools/content/parts/economy.json`, `tasting_layers`). the first time, everyone gets a permanent extra meal slot.
- 72 more dishes, 125 total.
- record board (lectern recipe): server bests for depth, dishes, feasts, landmarks, pages, commissions, forging and more. nothing is announced.
- breakthrough: the first player below y=0 puts a title on everyone's screen. only fires once per world.
- tremors: every 30-60 minutes a random layer with players in it shakes. your camera shakes for 2-3 seconds. wide unsupported rooms may cave in, and a fresh ore seam appears inside nearby rock. tremors only touch blocks 12+ below the surface and out of the sky. off with `tremors` in the config.
- weekly goal: one shared counter per real week (shown on the record board). meeting it gives everyone an extra meal slot for a week.
- `/wiki` links the held item's page. `/wiki <name>` links any item, block or dish. set `wiki_url` in `config/deepworld.json`.
- live data: the server serves `http://<host>:8125/live.json` (online players and their layer, records, commissions, goal, recent events) and writes `deepworld_live.json`. the wiki's server page reads it. `live_port: 0` turns it off.
- wiki: `cd wiki && npm run build && npm run preview`, then open `localhost:4321`. the journal page hides each page behind a spoiler.
- messages: the mod never posts to chat on its own. notices go on the action bar for 4 seconds, and big moments (milestones, breakthrough, tasting menu) are titles. only things you ask for (survey kit, trait check) answer in chat.
- simplified: shops aren't listed on the live page. the weekly goal resets on real-time weeks, even if the server is off.

## not tested

- nothing here was played with a real vanilla client. the checks are headless self-tests and rcon.
- multiplayer: feasts, records and commissions were only tested with fake players.
- the resource pack is generated and auto-hosted by polymer. check that it downloads on join.
- worldgen checks ran on the seed `deepworld`. the deployed server uses a random seed, so its caves and landmarks weren't checked.
- the live port is open to anyone who can reach it and shows player names. fine for a private server.
- tremor seams only replace stone with no air or water next to it, so walls you can see never change.
- the tremor camera shake was never seen on a real client. it nudges your view each tick and puts it back, so mouse aim survives.
