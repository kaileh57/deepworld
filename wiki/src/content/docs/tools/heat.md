---
title: heat calculator
description: depth, armor and buffs to heat stage.
sidebar:
  order: 4
---

<div class="tool">
<div class="row">
<div><label for="y">y</label><input id="y" type="number" value="400" min="-256" max="1535"></div>
<div><label for="preset">full set</label><select id="preset"></select></div>
</div>
<div class="row">
<div><label for="head">helmet</label><select id="head" class="slot"></select></div>
<div><label for="chest">chestplate</label><select id="chest" class="slot"></select></div>
<div><label for="legs">leggings</label><select id="legs" class="slot"></select></div>
<div><label for="feet">boots</label><select id="feet" class="slot"></select></div>
</div>
<div class="row">
<div><label for="cooling">cooling meal</label><select id="cooling"><option value="0">none</option><option value="1">cooling i or ii (+1)</option><option value="2">cooling iii (+2)</option></select></div>
</div>
<label class="check"><input id="fire" type="checkbox">fire resistance (+2)</label>
<label class="check"><input id="delver" type="checkbox">delver focus (+1)</label>
</div>

<div id="result" class="out"></div>

<script src="/js/wiki.js"></script>
<script>
	(async () => {
		const data = await dw.load();
		const el = (id) => document.getElementById(id);
		const slots = ['head', 'chest', 'legs', 'feet'];
		const options = [['', 'none'], ...data.armor.map((set) => [set.set, `${set.name} (${set.heat})`])];
		for (const slot of slots) dw.fill(el(slot), options);
		dw.fill(el('preset'), data.armor.map((set) => [set.set, set.name]), 'pick one');
		const start = dw.param('y');
		if (start !== null) el('y').value = start;
		const effects = ['nothing', 'hunger drains faster', 'hunger and slowness', 'hunger, slowness, damage every two seconds', 'hunger, slowness, damage every second', 'hunger, slowness, heavy damage every second'];
		const draw = () => {
			const y = Number(el('y').value);
			const layer = data.layers.find((entry) => y >= entry.y[0] && y < entry.y[1]);
			const heat = layer ? layer.heat : 0;
			const armor = slots.reduce((sum, slot) => sum + (data.armor.find((set) => set.set === el(slot).value)?.heat ?? 0), 0);
			const armor_points = Math.floor(armor + 0.001);
			const protection = armor_points + (el('fire').checked ? 2 : 0) + Number(el('cooling').value) + (el('delver').checked ? 1 : 0);
			const stage = Math.max(0, heat - protection);
			const where = layer ? `<a href="/ref/layers/${layer.slug}/">${dw.esc(layer.name)}</a>` : 'outside the column';
			el('result').innerHTML = `<p class="big stage-${stage}">stage ${stage}</p><p>${effects[Math.min(stage, 5)]}.</p><table><tbody><tr><th>layer</th><td>${where}</td></tr><tr><th>heat</th><td>${heat} of 5</td></tr><tr><th>armor</th><td>${armor} → ${armor_points}</td></tr><tr><th>protection</th><td>${protection}</td></tr></tbody></table>${heat === 0 ? '<p>no heat above y 600.</p>' : ''}`;
		};
		el('preset').addEventListener('input', () => {
			for (const slot of slots) el(slot).value = el('preset').value;
			draw();
		});
		for (const id of ['y', 'cooling', 'fire', 'delver', ...slots]) el(id).addEventListener('input', draw);
		draw();
	})();
</script>

armor adds up across pieces and rounds down. stage is heat minus protection. see [heat](/mechanics/heat/).
