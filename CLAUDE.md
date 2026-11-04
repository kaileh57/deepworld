# rules

- only commit and push as kaileh57. check `gh auth status` and `git config user.name` before pushing. no co-author trailers.
- keep public text short: readme, commit messages, repo description, pr text, in-game text, wiki copy.
- mostly lowercase in public text, item names included.
- no code comments, in any language or build file.
- snake_case for variables, fields, params and own methods. overrides, interface methods and mixin targets keep their upstream names. class names stay CamelCase.
- the design plan lives in `temp/plan.md`, which is gitignored. never commit it.

# layout

- minecraft 26.3, fabric, mojang names. polymer and sgui are bundled into the jar.
- content is data, behavior is java.
- textures are drawn from `tools/textures/sprites/**.json` with `palettes.json`. run `python3 tools/textures/gen.py --preview` and look at `build/texture_preview/` before committing art.
- `scripts/verify.py` boots a test server in `run/` and checks the gates. it must pass before pushing.
- `scripts/export.sh` dumps registries to `export/`. `wiki/` builds pages from it.
- `scripts/deploy.sh` installs the server to `~/deepworld-server` on this mac.
- the wiki uses astro 5 because node here is 20. astro 7 needs node 22.
- worldgen is generated: edit `tools/worldgen/layers.json` or `gen.py`, run `python3 tools/worldgen/gen.py`. output in `src/main/generated` is committed.
- `python3 scripts/verify.py --worldgen` runs the worldgen gates on a fresh seeded world. `tools/worldgen/calibrate.py` prints per-layer stats.
- `/dw survey`, `/dw fortress`, `/dw slice <z> <x> <width>` are debug commands. slice writes `run/deepworld_slice.png`.
- `python3 scripts/devserver.py start|stop` runs the dev server for rcon checks.
