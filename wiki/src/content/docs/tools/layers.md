---
title: layer explorer
description: the whole column, top to bottom.
sidebar:
  order: 6
---

the column from y 1535 down to y -256. click a layer.

<div id="explorer" class="explorer"></div>

<script src="/js/wiki.js"></script>
<script>
	(async () => {
		const data = await dw.load();
		const top = 1535;
		const bottom = -256;
		const scale = 0.5;
		const width = 420;
		const left = 56;
		const y_of = (y) => (top - y) * scale + 10;
		const colors = ['#4f6b3a', '#3c4650', '#7a4a38', '#5b4a80', '#2c2533', '#8a3a14', '#16303d', '#5a2414', '#7a1e1e', '#4a0f12'];
		const bands = [];
		bands.push(`<rect x="${left}" y="${y_of(top)}" width="${width - left}" height="${(top - 1200) * scale}" fill="#5e86ad"></rect><text x="${left + 10}" y="${y_of(1200) - 10}" fill="#f4efe8">sky and surface, y 1200</text>`);
		bands.push(`<rect x="${left}" y="${y_of(1200)}" width="${width - left}" height="${100 * scale}" fill="#6b5a44"></rect><text x="${left + 10}" y="${y_of(1150) + 5}" fill="#f4efe8">upper rock</text>`);
		data.layers.forEach((layer, index) => {
			const y1 = y_of(layer.y[1]);
			const h = (layer.y[1] - layer.y[0]) * scale;
			const heat = layer.heat ? ` · heat ${layer.heat}` : '';
			bands.push(`<a href="/ref/layers/${layer.slug}/"><title>${dw.esc(layer.name)}</title><rect x="${left}" y="${y1}" width="${width - left}" height="${h}" fill="${colors[index % colors.length]}"></rect><text x="${left + 10}" y="${y1 + 20}" fill="#f4efe8">${dw.esc(layer.name)}</text><text x="${left + 10}" y="${y1 + 38}" fill="#d8cfc4" font-size="12">y ${layer.y[0]} to ${layer.y[1]}${heat}</text></a>`);
		});
		const ticks = [1500, 1200, 1000, 800, 600, 400, 200, 0, -256].map((y) => `<line x1="${left - 6}" x2="${left}" y1="${y_of(y)}" y2="${y_of(y)}" stroke="currentColor"></line><text class="scale" x="${left - 10}" y="${y_of(y) + 4}" text-anchor="end">${y}</text>`);
		const heat_line = `<line x1="${left}" x2="${width}" y1="${y_of(600)}" y2="${y_of(600)}" stroke="#f4c58f" stroke-width="2" stroke-dasharray="6 4"></line><text x="${width - 6}" y="${y_of(600) - 6}" text-anchor="end" fill="#f4c58f" font-size="12">heat starts</text>`;
		const height = (top - bottom) * scale + 20;
		document.getElementById('explorer').innerHTML = `<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="layers of the column">${bands.join('')}${heat_line}${ticks.join('')}</svg>`;
	})();
</script>

## layers

- [shallow caverns](/ref/layers/shallow-caverns/)
- [deepslate boundary](/ref/layers/deepslate-boundary/)
- [granite trenches](/ref/layers/granite-trenches/)
- [crystal caverns](/ref/layers/crystal-caverns/)
- [obsidian roots](/ref/layers/obsidian-roots/)
- [molten echoes](/ref/layers/molten-echoes/)
- [gloomstone trenches](/ref/layers/gloomstone-trenches/)
- [mantle crust](/ref/layers/mantle-crust/)
- [the hellmouth](/ref/layers/the-hellmouth/)
- [nether stratum](/ref/layers/nether-stratum/)
