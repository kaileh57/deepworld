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
