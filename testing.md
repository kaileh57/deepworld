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
