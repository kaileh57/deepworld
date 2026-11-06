---
title: rail calculator
description: travel time and bed cost.
sidebar:
  order: 5
---

<div class="tool">
<div class="row">
<div><label for="distance">track length, in blocks</label><input id="distance" type="number" min="1" value="1000"></div>
<div><label for="bed">bed under the rail</label><select id="bed"><option value="8">plain (8 m/s)</option><option value="16">copper block (16 m/s)</option><option value="32" selected>gold block (32 m/s)</option><option value="48">cobalt steel block (48 m/s)</option></select></div>
</div>
</div>

<div id="rail" class="out"></div>

## lift

<div class="tool">
<div class="row">
<div><label for="height">shaft height, in blocks</label><input id="height" type="number" min="1" value="200"></div>
<div><label for="floors">floors</label><input id="floors" type="number" min="2" value="2"></div>
</div>
<label class="check"><input id="engineer" type="checkbox">engineer focus (12 m/s)</label>
</div>

<div id="lift" class="out"></div>

<script src="/js/wiki.js"></script>
<script>
	(() => {
		const el = (id) => document.getElementById(id);
		const beds = [
			{ speed: 8, name: 'plain', cost: null },
			{ speed: 16, name: 'copper block', cost: 'copper ingots' },
			{ speed: 32, name: 'gold block', cost: 'gold ingots' },
			{ speed: 48, name: 'cobalt steel block', cost: 'cobalt steel ingots' },
		];
		const draw = () => {
			const distance = Math.max(1, Number(el('distance').value) || 1);
			const speed = Number(el('bed').value);
			const bed = beds.find((entry) => entry.speed === speed);
			const rail_crafts = Math.ceil(distance / 16);
			const rows = beds.map((entry) => `<tr${entry === bed ? ' class="stage-0"' : ''}><td>${entry.name}</td><td>${entry.speed} m/s</td><td>${dw.minutes(distance / entry.speed)}</td><td>${entry.cost ? `${(distance * 9).toLocaleString()} ${entry.cost}` : 'none'}</td></tr>`).join('');
			el('rail').innerHTML = `<p class="big">${dw.minutes(distance / speed)}</p><p>${distance.toLocaleString()} blocks at ${speed} m/s on straight track.${bed.cost ? ` ${distance.toLocaleString()} ${bed.name}s, ${(distance * 9).toLocaleString()} ${bed.cost}.` : ''} rails: ${rail_crafts * 6} iron ingots and ${rail_crafts} sticks.</p><table><thead><tr><th>bed</th><th>speed</th><th>time</th><th>bed cost</th></tr></thead><tbody>${rows}</tbody></table>`;
		};
		const lift = () => {
			const height = Math.max(1, Number(el('height').value) || 1);
			const floors = Math.max(2, Number(el('floors').value) || 2);
			const speed = el('engineer').checked ? 12 : 8;
			const cable = Math.ceil(height / 64);
			el('lift').innerHTML = `<p class="big">${dw.minutes(height / speed)}</p><p>${height} blocks at ${speed} m/s. ${floors} lift stations: ${floors * 4} gold blocks, ${floors * 4} iron ingots, ${floors} redstone. cable: ${cable} gold block${cable === 1 ? '' : 's'}, one per 64 blocks.</p>`;
		};
		for (const id of ['distance', 'bed']) el(id).addEventListener('input', draw);
		for (const id of ['height', 'floors', 'engineer']) el(id).addEventListener('input', lift);
		draw();
		lift();
	})();
</script>

times assume top speed the whole way. carts speed up on their own over beds, only straight runs hold top speed, and curves can derail. see [rail and lifts](/mechanics/rail/).
