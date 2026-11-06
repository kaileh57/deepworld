---
title: recipe browser
description: find a dish by station, ingredient or buff.
sidebar:
  order: 1
---

<div class="tool">
<div class="row">
<div><label for="q">name</label><input id="q" type="search" placeholder="search"></div>
<div><label for="station">station</label><select id="station"></select></div>
<div><label for="kind">kind</label><select id="kind"></select></div>
</div>
<div class="row">
<div><label for="ingredient">ingredient</label><select id="ingredient"></select></div>
<div><label for="property">ingredient property</label><select id="property"></select></div>
<div><label for="buff">buff</label><select id="buff"></select></div>
</div>
<label class="check"><input id="preserved" type="checkbox">preserved only</label>
</div>

<div id="count" class="out"></div>
<div id="list"></div>

## what can i make

type what you have, one per line or separated by commas. names as in game, like `flour, beef, potato`.

<div class="tool">
<label for="have">ingredients</label>
<textarea id="have" rows="4" placeholder="dough&#10;beef&#10;potato&#10;rock salt"></textarea>
<label class="check"><input id="near" type="checkbox" checked>also show dishes missing one</label>
</div>

<div id="make" class="out"></div>

<script src="/js/wiki.js"></script>
<script>
	(async () => {
		const data = await dw.load();
		const el = (id) => document.getElementById(id);
		const dishes = [...data.dishes].sort((a, b) => a.name.localeCompare(b.name));
		const used = [...new Set(dishes.flatMap((dish) => dish.ingredients))].sort((a, b) => dw.name_of(data, a).localeCompare(dw.name_of(data, b)));
		const props_of = (id) => {
			if (id === '#meat') return Object.assign({}, ...data.meats.map((meat) => data.ingredients[meat]?.properties ?? {}));
			return data.ingredients[id]?.properties ?? {};
		};
		const all_props = [...new Set(Object.values(data.ingredients).flatMap((entry) => Object.keys(entry.properties ?? {})))].sort();
		const all_buffs = [...new Set(dishes.flatMap((dish) => Object.keys(dish.buffs)))].sort();
		dw.fill(el('station'), [...new Set(dishes.map((dish) => dish.station))].sort().map((s) => [s, s.replaceAll('_', ' ')]), 'any');
		dw.fill(el('kind'), [...new Set(dishes.map((dish) => dish.kind))].sort().map((k) => [k, k]), 'any');
		dw.fill(el('ingredient'), used.map((id) => [id, dw.name_of(data, id)]), 'any');
		dw.fill(el('property'), all_props.map((p) => [p, p]), 'any');
		dw.fill(el('buff'), all_buffs.map((b) => [b, b.replaceAll('_', ' ')]), 'any');
		const start = dw.param('ingredient');
		if (start) el('ingredient').value = start;
		const row = (dish, missing) => `<tr><td>${dw.link_of(data, dish.id)}</td><td>${dw.esc(dish.station.replaceAll('_', ' '))}</td><td>${dish.ingredients.map((id) => dw.link_of(data, id)).join(', ')}</td><td>${dw.esc(dish.kind === 'extract' ? `makes ${dish.count}` : dw.buff_text(dish.buffs))}</td><td>${dish.duration ? dw.minutes(dish.duration) : ''}</td>${missing === undefined ? '' : `<td>${missing.map((id) => dw.esc(dw.name_of(data, id))).join(', ')}</td>`}</tr>`;
		const table = (list, missing) => list.length ? `<table><thead><tr><th>dish</th><th>station</th><th>ingredients</th><th>buffs</th><th>lasts</th>${missing ? '<th>missing</th>' : ''}</tr></thead><tbody>${list.map((entry) => missing ? row(entry.dish, entry.missing) : row(entry)).join('')}</tbody></table>` : '<p>nothing matches.</p>';
		const draw = () => {
			const q = el('q').value.trim().toLowerCase();
			const station = el('station').value;
			const kind = el('kind').value;
			const ingredient = el('ingredient').value;
			const property = el('property').value;
			const buff = el('buff').value;
			const preserved = el('preserved').checked;
			const list = dishes.filter((dish) =>
				(!q || dish.name.includes(q)) &&
				(!station || dish.station === station) &&
				(!kind || dish.kind === kind) &&
				(!ingredient || dish.ingredients.includes(ingredient)) &&
				(!property || dish.ingredients.some((id) => props_of(id)[property])) &&
				(!buff || dish.buffs[buff]) &&
				(!preserved || dish.preserved));
			el('count').textContent = `${list.length} of ${dishes.length}`;
			el('list').innerHTML = table(list);
		};
		const norm = (text) => text.toLowerCase().replace(/['’]/g, '').replace(/[_\s]+/g, ' ').trim();
		const make = () => {
			const have = new Set(el('have').value.split(/[\n,]+/).map(norm).filter(Boolean));
			if (!have.size) {
				el('make').innerHTML = '';
				return;
			}
			const has = (id) => {
				if (id === '#meat') return data.meats.some((meat) => have.has(norm(dw.name_of(data, meat))));
				return have.has(norm(dw.name_of(data, id))) || have.has(norm(dw.plain(id)));
			};
			const results = dishes.map((dish) => ({ dish, missing: [...new Set(dish.ingredients.filter((id) => !has(id)))] }));
			const ready = results.filter((entry) => entry.missing.length === 0);
			const near = el('near').checked ? results.filter((entry) => entry.missing.length === 1) : [];
			el('make').innerHTML = `<h3>you can make ${ready.length}</h3>${table(ready.map((entry) => entry.dish))}` + (el('near').checked ? `<h3>one short: ${near.length}</h3>${table(near, true)}` : '');
		};
		for (const id of ['q', 'station', 'kind', 'ingredient', 'property', 'buff', 'preserved']) el(id).addEventListener('input', draw);
		el('have').addEventListener('input', make);
		el('near').addEventListener('input', make);
		draw();
	})();
</script>

anything that isn't a known recipe still cooks into a generic dish. see [quality and stirring](/mechanics/quality/).
