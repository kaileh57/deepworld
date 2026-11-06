---
title: expedition planner
description: what to bring for a layer.
sidebar:
  order: 3
---

pick a layer and how long you'll stay. the planner reads [heat](/mechanics/heat/), [hazards](/mechanics/hazards/) and [spoilage](/mechanics/spoilage/).

<div class="tool">
<div class="row">
<div><label for="layer">layer</label><select id="layer"></select></div>
<div><label for="stay">stay, in minutes of play</label><input id="stay" type="number" min="1" value="30"></div>
</div>
<label class="check"><input id="delver" type="checkbox">delver focus</label>
<label class="check"><input id="leather" type="checkbox">wearing a leather armor piece</label>
<label class="check"><input id="boots" type="checkbox">wearing wool-wrapped boots</label>
</div>

<div id="plan" class="out"></div>

<script src="/js/wiki.js"></script>
<script>
	(async () => {
		const data = await dw.load();
		const el = (id) => document.getElementById(id);
		dw.fill(el('layer'), data.layers.map((layer) => [layer.slug, `${layer.name} (y ${layer.y[0]} to ${layer.y[1]})`]));
		const start = dw.param('layer');
		if (start) el('layer').value = start;
		const sets = data.armor.map((set) => ({ ...set, full: Math.floor(set.heat * 4 + 0.001) }));
		const best = (property, kind, deep) => data.dishes
			.filter((dish) => dish.buffs[property] && (kind === 'drink' ? dish.kind === 'drink' : dish.kind === 'dish'))
			.sort((a, b) => (deep ? (b.preserved - a.preserved) : 0) || b.buffs[property] - a.buffs[property] || a.tier - b.tier || b.duration - a.duration);
		const draw = () => {
			const layer = data.layers.find((entry) => entry.slug === el('layer').value);
			if (!layer) return;
			const stay = Math.max(1, Number(el('stay').value) || 1);
			const deep = layer.y[1] <= 600;
			const delver = el('delver').checked ? 1 : 0;
			const out = [];
			const tool = data.tools.find((entry) => entry.tier === layer.needs);
			out.push(layer.needs === 'wood' ? `<h3>pickaxe</h3><p>any pickaxe breaks ${dw.esc(layer.rock_name ?? 'the rock')}.</p>` : `<h3>pickaxe</h3><p>${dw.esc(layer.needs.replaceAll('_', ' '))} or better for ${dw.esc(layer.rock_name ?? 'the rock')}${tool ? `: ${dw.link_of(data, tool.id)}` : ''}.</p>`);
			const needs = [];
			const notes = [];
			const heat = layer.heat;
			let cooling_need = null;
			if (heat > 0) {
				const need = Math.max(0, heat - delver);
				const alone = sets.find((set) => set.full >= need);
				const cooled = sets.find((set) => set.full + 2 >= need);
				const lines = [];
				if (alone) lines.push(`a full ${dw.esc(alone.name)} set (${alone.full}) covers heat ${heat} on its own.`);
				if (cooled && cooled !== alone) lines.push(`or ${dw.esc(cooled.name)} (${cooled.full}) with cooling iii food or fire resistance (+2).`);
				if (!alone && !cooled) lines.push('no single set is enough. stack armor, cooling food and fire resistance.');
				if (delver) lines.push('delver focus counts for 1.');
				out.push(`<h3>heat ${heat}</h3><p>${lines.join(' ')} <a href="/tools/heat/?y=${layer.y[0] + 1}">check in the heat calculator</a>.</p>`);
				cooling_need = { property: 'cooling', why: alone ? `heat ${heat}, spare with full armor` : `heat ${heat}` };
			} else {
				out.push('<h3>heat</h3><p>none at this depth. no heat armor needed.</p>');
			}
			for (const id of layer.hazards) {
				if (id === 'cold_water') {
					if (el('leather').checked) notes.push('cold water: your leather keeps it off.');
					else needs.push({ property: 'warming', why: 'cold water' });
				}
				if (id === 'echoes') {
					if (el('boots').checked) notes.push('echoes: wool-wrapped boots keep you hushed.');
					else needs.push({ property: 'hushed', why: 'echoes' });
				}
				if (id === 'sound') needs.push({ property: 'hushed', why: 'sculk and wardens, needs ii', level: 2 });
				if (id === 'darkness') {
					needs.push({ property: 'glowing', why: 'darkness' });
					notes.push('darkness: torches burn out after 5 minutes. bring amethyst lamps.');
				}
				if (id === 'fumes') {
					needs.push({ property: 'bitter', why: 'fumes in sulfur springs' });
					notes.push('fumes: a ventilation fan nearby also works.');
				}
				if (id === 'firedamp') notes.push('firedamp: carry a safety lamp, no torches in coal seams, place a ventilation fan.');
				if (id === 'cave_ins') notes.push('cave-ins: keep logs, fences or walls within 4 blocks of wide rooms.');
				if (id === 'magma_tides') notes.push('magma tides: the lava sea moves 5 blocks. watch the tide on the action bar.');
			}
			if (cooling_need) needs.push(cooling_need);
			const seen = new Set();
			const wanted = needs.filter((need) => {
				const key = need.property;
				const prior = needs.find((other) => other.property === key && (other.level ?? 1) > (need.level ?? 1));
				if (seen.has(key) || prior) return false;
				seen.add(key);
				return true;
			});
			const slots = [];
			const drink_need = wanted.find((need) => best(need.property, 'drink', deep).some((dish) => dish.buffs[need.property] >= (need.level ?? 1) && (!deep || dish.preserved)));
			for (const need of wanted) {
				if (need === drink_need) continue;
				const pick = best(need.property, 'dish', deep).find((dish) => dish.buffs[need.property] >= (need.level ?? 1)) ?? best(need.property, 'dish', deep)[0];
				if (pick && slots.length < 3 && !slots.some((slot) => slot.dish === pick)) slots.push({ dish: pick, why: need.why });
			}
			const filler = data.dishes.filter((dish) => dish.kind === 'dish' && dish.preserved && dish.buffs.hearty).sort((a, b) => b.buffs.hearty - a.buffs.hearty || b.duration - a.duration || a.tier - b.tier);
			for (const dish of filler) {
				if (slots.length >= 3) break;
				if (!slots.some((slot) => slot.dish === dish)) slots.push({ dish, why: 'hearty filler' });
			}
			const drink = drink_need
				? { dish: best(drink_need.property, 'drink', deep).find((dish) => dish.buffs[drink_need.property] >= (drink_need.level ?? 1) && (!deep || dish.preserved)), why: drink_need.why }
				: { dish: data.dishes.filter((dish) => dish.kind === 'drink' && dish.preserved).sort((a, b) => b.duration - a.duration)[0], why: 'any preserved drink' };
			const servings = (dish) => Math.ceil((stay * 60) / Math.max(1, dish.duration));
			const line = (slot, label) => `<tr><td>${label}</td><td>${dw.link_of(data, slot.dish.id)}</td><td>${dw.esc(dw.buff_text(slot.dish.buffs))}</td><td>${dw.esc(slot.why)}</td><td>${dw.minutes(slot.dish.duration)}</td><td>${slot.dish.preserved ? 'yes' : '<strong>no</strong>'}</td><td>${servings(slot.dish)}${slot.dish.preserved || !deep ? '' : ', jarred'}</td></tr>`;
			const rows = slots.map((slot, index) => line(slot, `meal ${index + 1}`)).concat(drink.dish ? [line(drink, 'drink')] : []);
			const total = slots.concat(drink.dish ? [drink] : []).reduce((sum, slot) => sum + servings(slot.dish), 0);
			out.push(`<h3>meals for ${stay} minutes</h3><table><thead><tr><th>slot</th><th>dish</th><th>buffs</th><th>for</th><th>lasts</th><th>preserved</th><th>bring</th></tr></thead><tbody>${rows.join('')}</tbody></table><p>about ${total} servings in all.${deep ? ' below y 600 anything not preserved spoils in about 6 minutes of play. seal the rest in jars or carry it in a lunchbox or hamper.' : ''}</p>`);
			const alts = wanted.map((need) => {
				const list = best(need.property, 'dish', deep).concat(best(need.property, 'drink', deep)).slice(0, 5);
				return `<li><strong>${dw.esc(need.property)}</strong> (${dw.esc(need.why)}): ${list.map((dish) => `${dw.link_of(data, dish.id)} ${dw.roman(dish.buffs[need.property])}${dish.preserved ? '' : '*'}`).join(', ')}</li>`;
			});
			if (alts.length) out.push(`<h3>other options</h3><ul>${alts.join('')}</ul><p>* not preserved.</p>`);
			if (notes.length) out.push(`<h3>also</h3><ul>${notes.map((note) => `<li>${dw.esc(note)}</li>`).join('')}</ul>`);
			out.push(`<p><a href="/ref/layers/${layer.slug}/">${dw.esc(layer.name)}</a></p>`);
			el('plan').innerHTML = out.join('');
		};
		for (const id of ['layer', 'stay', 'delver', 'leather', 'boots']) el(id).addEventListener('input', draw);
		draw();
	})();
</script>

three meal slots and one drink slot. a slot refreshes when you eat the same dish again, so bring one serving per slot for each time it runs out.
