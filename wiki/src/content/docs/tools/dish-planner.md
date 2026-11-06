---
title: dish planner
description: a dish, down to raw materials.
sidebar:
  order: 2
---

pick a dish to see every ingredient down to what you mine, forage, hunt or get from vanilla.

<div class="tool">
<div class="row">
<div><label for="dish">dish</label><select id="dish"></select></div>
<div><label for="servings">servings</label><input id="servings" type="number" min="1" value="1"></div>
</div>
</div>

<div id="tree" class="out tree"></div>

<div id="raw" class="out"></div>

<script src="/js/wiki.js"></script>
<script>
	(async () => {
		const data = await dw.load();
		const el = (id) => document.getElementById(id);
		const dishes = [...data.dishes].sort((a, b) => a.name.localeCompare(b.name));
		dw.fill(el('dish'), dishes.map((dish) => [dish.id, dish.name]));
		const start = dw.param('dish');
		if (start && dishes.some((dish) => dish.id === start)) el('dish').value = start;
		const round = (value) => (Math.round(value * 100) / 100).toString();
		const where = (id) => {
			if (id === '#meat') return `beef, porkchop, mutton, chicken, rabbit or hoglin chop`;
			const list = data.sources[id];
			if (list?.length) return list.map(dw.esc).join('; ');
			return id.startsWith('minecraft:') ? 'vanilla' : 'unknown';
		};
		const pick = (id, path) => {
			const options = (data.recipes[id] ?? []).filter((recipe) => recipe.inputs.every(([input]) => !path.has(dw.dish_ref(input))));
			return options;
		};
		const draw = () => {
			const dish = data.dishes.find((entry) => entry.id === el('dish').value);
			if (!dish) return;
			const servings = Math.max(1, Number(el('servings').value) || 1);
			const totals = {};
			const node = (id, amount, path, depth) => {
				const real = dw.dish_ref(id);
				const options = depth > 7 ? [] : pick(real, path);
				const label = `${round(amount)} × ${dw.link_of(data, id)}`;
				if (!options.length) {
					totals[id] = (totals[id] ?? 0) + amount;
					return `<li>${label} <span class="from">${where(real)}</span></li>`;
				}
				const recipe = options[0];
				const scale = amount / recipe.count;
				const next = new Set(path).add(real);
				const others = options.length > 1 ? ` <span class="from">or ${options.slice(1).map((other) => `${dw.esc(other.via)}: ${other.inputs.map(([input, n]) => `${n} ${dw.esc(dw.name_of(data, input))}`).join(' + ')}`).join('; ')}</span>` : '';
				const children = recipe.inputs.map(([input, n]) => node(input, n * scale, next, depth + 1)).join('');
				return `<li>${label} <span class="from">${dw.esc(recipe.via)}, makes ${recipe.count}</span>${others}<ul>${children}</ul></li>`;
			};
			const own = (data.recipes[dish.result ?? dish.id] ?? []).find((recipe) => recipe.dish === dish.id.split(':')[1]);
			const scale = servings / (own?.count ?? 1);
			const inputs = own ? own.inputs : dish.ingredients.map((id) => [id, 1]);
			const children = inputs.map(([input, n]) => node(input, n * scale, new Set([dish.result ?? dish.id]), 1)).join('');
			el('tree').innerHTML = `<ul><li><strong>${dw.link_of(data, dish.id)}</strong> <span class="from">${dw.esc(dish.station.replaceAll('_', ' '))}, ${dw.esc(dish.heat)} heat${dish.kind === 'extract' ? `, makes ${dish.count}` : ''}</span><ul>${children}</ul></li></ul>`;
			const rows = Object.entries(totals).sort((a, b) => dw.name_of(data, a[0]).localeCompare(dw.name_of(data, b[0]))).map(([id, amount]) => `<tr><td>${round(amount)}</td><td>${dw.link_of(data, id)}</td><td>${where(dw.dish_ref(id))}</td></tr>`).join('');
			el('raw').innerHTML = `<h3>raw materials</h3><table><thead><tr><th>amount</th><th>item</th><th>where</th></tr></thead><tbody>${rows}</tbody></table>`;
		};
		el('dish').addEventListener('input', draw);
		el('servings').addEventListener('input', draw);
		draw();
	})();
</script>

amounts are fractions where a step makes more than one, like dough coming out two at a time.
