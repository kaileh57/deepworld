import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const wiki_dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const export_dir = process.env.DEEPWORLD_EXPORT ?? path.resolve(wiki_dir, '..', 'export');
const ref_dir = path.join(wiki_dir, 'src', 'content', 'docs', 'ref');
const icon_dir = path.join(wiki_dir, 'public', 'icons');

const read_json = (name) => {
	const file = path.join(export_dir, name);
	return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : [];
};

const short_id = (id) => id.split(':')[1];

const infobox = (entry, kind) => {
	const rows = Object.entries(entry.info ?? {})
		.map(([key, value]) => `<tr><th>${key}</th><td>${value}</td></tr>`)
		.join('');
	const icon = entry.icon ? `<img src="/icons/${kind}/${short_id(entry.id)}.png" alt="">` : '';
	return `<div class="infobox">${icon}<table><tr><th>id</th><td><code>${entry.id}</code></td></tr>${rows}</table></div>`;
};

const write_pages = (kind, entries) => {
	const dir = path.join(ref_dir, kind);
	fs.mkdirSync(dir, { recursive: true });
	if (entries.length === 0) {
		fs.writeFileSync(path.join(dir, 'index.md'), `---\ntitle: ${kind}\n---\n\nnothing yet.\n`);
		return;
	}
	for (const entry of entries) {
		const body = [
			'---',
			`title: ${JSON.stringify(entry.name)}`,
			'---',
			'',
			infobox(entry, kind),
			'',
			entry.description ?? '',
			'',
		].join('\n');
		fs.writeFileSync(path.join(dir, `${short_id(entry.id)}.md`), body);
	}
};

const copy_icons = (kind, entries) => {
	const dir = path.join(icon_dir, kind);
	fs.mkdirSync(dir, { recursive: true });
	for (const entry of entries) {
		if (!entry.icon) continue;
		const source = path.join(export_dir, entry.icon);
		if (fs.existsSync(source)) fs.copyFileSync(source, path.join(dir, `${short_id(entry.id)}.png`));
	}
};

fs.rmSync(ref_dir, { recursive: true, force: true });
fs.rmSync(icon_dir, { recursive: true, force: true });
for (const kind of ['items', 'blocks', 'dishes']) {
	const entries = read_json(`${kind}.json`);
	write_pages(kind, entries);
	copy_icons(kind, entries);
	console.log(`${kind}: ${entries.length}`);
}

const content_file = path.resolve(wiki_dir, '..', 'src', 'main', 'generated_content', 'deepworld_content.json');
const content = fs.existsSync(content_file) ? JSON.parse(fs.readFileSync(content_file, 'utf8')) : {};
const pages = [...(content.journal_pages ?? [])].sort((a, b) => a.page - b.page);
const story = [
	'---',
	'title: the journal',
	'description: pages found in landmarks. spoilers.',
	'---',
	'',
	'pages turn up in landmark chests. each one is hidden until you open it.',
	'',
	...pages.map((entry) => `<details><summary>page ${entry.page}, ${entry.landmark.replaceAll('_', ' ')}</summary>\n\n${entry.text}\n\n</details>\n`),
].join('\n');
fs.mkdirSync(path.join(ref_dir, 'story'), { recursive: true });
fs.writeFileSync(path.join(ref_dir, 'story', 'journal.md'), story);
console.log(`journal: ${pages.length}`);

const docs_dir = path.join(wiki_dir, 'src', 'content', 'docs');
const data_dir = path.join(wiki_dir, 'public', 'data');
const read_root = (rel) => {
	const file = path.resolve(wiki_dir, '..', rel);
	return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : [];
};
const layer_list = read_root('src/main/generated/deepworld_layers.json');
const layer_spec = read_root('tools/worldgen/layers.json');

const exported = {};
for (const kind of ['items', 'blocks', 'dishes']) {
	for (const entry of read_json(`${kind}.json`)) exported[entry.id] = { name: entry.name, kind, icon: entry.icon ? `/icons/${kind}/${short_id(entry.id)}.png` : null };
}
const meats = ['minecraft:beef', 'minecraft:porkchop', 'minecraft:mutton', 'minecraft:chicken', 'minecraft:rabbit', 'deepworld:hoglin_chop'];
const plain = (id) => (id.includes(':') ? id.split(':')[1] : id).replace(/^dish\//, '').replaceAll('_', ' ');
const dish_by_id = Object.fromEntries((content.dishes ?? []).map((dish) => [dish.id, dish]));
const resolve = (id) => {
	if (id.startsWith('deepworld:dish/')) return `deepworld:${id.slice('deepworld:dish/'.length)}`;
	if (id.startsWith('drink:')) return `deepworld:${id.slice('drink:'.length)}`;
	return id;
};
const name_of = (id) => {
	if (id === '#meat') return 'any meat';
	const real = resolve(id);
	return exported[real]?.name ?? plain(real);
};
const link_of = (id) => {
	const real = resolve(id);
	const hit = exported[real];
	return hit ? `/ref/${hit.kind}/${short_id(real)}/` : null;
};
const link = (id) => {
	const url = link_of(id);
	return url ? `<a href="${url}">${name_of(id)}</a>` : name_of(id);
};
const slug = (name) => name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const rock_names = { 'minecraft:stone': 'stone', 'minecraft:deepslate': 'deepslate', 'minecraft:granite': 'granite', 'minecraft:calcite': 'calcite', 'minecraft:blackstone': 'blackstone', 'minecraft:basalt': 'basalt', 'minecraft:netherrack': 'netherrack' };
const rock_needs = { ...(content.vanilla_needs ?? {}) };
for (const stone of content.stones ?? []) rock_needs[`deepworld:${stone.id}`] = stone.needs;
const tier_name = (tier) => (tier ?? 'wood').replaceAll('_', ' ');

const hazard_defs = [
	{ id: 'cave_ins', name: 'cave-ins', y: [600, 1000], page: '/mechanics/shoring/' },
	{ id: 'cold_water', name: 'cold water', y: [900, 1000], page: '/mechanics/hazards/#cold-water' },
	{ id: 'firedamp', name: 'firedamp', y: [800, 900], where: 'coal seams', page: '/mechanics/hazards/#firedamp' },
	{ id: 'echoes', name: 'echoes', y: [700, 800], page: '/mechanics/hazards/#echoes' },
	{ id: 'fumes', name: 'fumes', y: [600, 700], where: 'sulfur springs', page: '/mechanics/hazards/#fumes' },
	{ id: 'magma_tides', name: 'magma tides', y: [450, 600], where: 'lava sea', page: '/mechanics/hazards/#magma-tides' },
	{ id: 'darkness', name: 'darkness', y: [300, 450], page: '/mechanics/hazards/#darkness' },
	{ id: 'sound', name: 'sound', y: [300, 450], where: 'sculk', page: '/mechanics/hazards/#sound' },
];
const mob_drops = {
	'deepworld:blind_eel': ['glow squid and drowned drop it', 'bonus catch fishing at y 900-1000'],
	'deepworld:lava_fish': ['striders drop it'],
	'deepworld:hoglin_chop': ['hoglins drop it'],
	'deepworld:fossil_marrow': ['breaking bone blocks'],
	'deepworld:geode_nectar': ['breaking amethyst clusters'],
	'deepworld:crystal_minnow': ['bonus catch fishing at y 700-800'],
	'deepworld:saltpeter': ['breaking tuff'],
};

const layers = layer_list.map((layer, index) => {
	const spec = layer_spec.find((entry) => entry.name === layer.name) ?? {};
	const number = 9 - index;
	const [min_y, max_y] = layer.y;
	const biomes = layer.biomes;
	const mobs = {};
	for (const biome of spec.biomes ?? []) {
		for (const group of Object.values(biome.spawns ?? {})) {
			for (const spawn of group) {
				mobs[spawn.type] ??= [];
				mobs[spawn.type].push(plain(biome.id));
			}
		}
	}
	const landmark = (content.landmarks ?? []).find((entry) => entry.biomes.some((biome) => biomes.includes(biome)));
	return {
		slug: slug(layer.name),
		name: layer.name,
		number,
		y: layer.y,
		heat: Math.max(0, Math.min(5, 5 - number)),
		rock: spec.stone ?? null,
		rock_name: spec.stone ? (rock_names[spec.stone] ?? name_of(spec.stone)) : null,
		needs: spec.stone ? (rock_needs[spec.stone] ?? 'wood') : 'wood',
		biomes,
		ores: layer.ores,
		signature: (content.tasting_layers ?? []).find((entry) => entry.layer === layer.name)?.keys ?? [],
		plants: (content.plants ?? []).filter((plant) => (plant.deep ?? []).some((biome) => biomes.includes(biome))).map((plant) => ({ id: `deepworld:${plant.id}`, drop: plant.drop })),
		hazards: hazard_defs.filter((hazard) => hazard.y[0] < max_y && hazard.y[1] > min_y).map((hazard) => hazard.id),
		landmark: landmark ? landmark.kind : null,
		mobs: Object.entries(mobs).map(([type, where]) => ({ id: type, name: plain(type), where: [...new Set(where)] })),
		fog: spec.attributes?.['minecraft:visual/fog_color'] ?? null,
	};
});
const layer_of_y = (y) => layers.find((layer) => y >= layer.y[0] && y < layer.y[1]);

const sources = {};
const add_source = (id, text) => {
	sources[id] ??= [];
	if (!sources[id].includes(text)) sources[id].push(text);
};
for (const layer of layers) {
	for (const ore of layer.ores) add_source(ore, `${layer.name} (y ${layer.y[0]}-${layer.y[1]})`);
}
for (const ore of content.ores ?? []) {
	const where = layers.filter((layer) => layer.ores.includes(`deepworld:${ore.id}`)).map((layer) => layer.name);
	add_source(ore.drop, `mine ${ore.name}${where.length ? ` in ${where.join(', ')}` : ''}`);
}
for (const plant of content.plants ?? []) {
	if (plant.biomes) add_source(plant.drop, `forage ${plant.name} on the surface: ${plant.biomes.map(plain).join(', ')}`);
	else add_source(plant.drop, `forage ${plant.name} at y ${plant.y[0]}-${plant.y[1]}: ${plant.deep.map(plain).join(', ')}`);
}
for (const [id, list] of Object.entries(mob_drops)) for (const text of list) add_source(id, text);

const recipes = {};
const add_recipe = (id, recipe) => {
	recipes[id] ??= [];
	recipes[id].push(recipe);
};
const count_inputs = (list) => {
	const counts = {};
	for (const id of list) counts[id] = (counts[id] ?? 0) + 1;
	return Object.entries(counts);
};
for (const entry of content.processing ?? []) add_recipe(entry.output, { via: entry.station.replaceAll('_', ' '), inputs: [[entry.input, 1]], count: entry.count });
for (const entry of content.forging ?? []) add_recipe(entry.result[0], { via: entry.station, inputs: entry.inputs, count: entry.result[1] });
for (const entry of content.smelting ?? []) add_recipe(entry.result, { via: 'furnace', inputs: [[entry.input, 1]], count: 1 });
for (const entry of content.shapeless ?? []) if (!entry.components) add_recipe(entry.result, { via: 'crafting', inputs: count_inputs(entry.ingredients), count: entry.count ?? 1 });
for (const entry of content.shaped ?? []) {
	if (entry.name) continue;
	const cells = entry.pattern.join('').split('').filter((cell) => cell !== ' ').map((cell) => entry.key[cell]);
	add_recipe(entry.result, { via: 'crafting', inputs: count_inputs(cells), count: entry.count ?? 1 });
}
for (const dish of content.dishes ?? []) {
	const target = dish.result ?? `deepworld:${dish.id}`;
	add_recipe(target, { via: dish.station.replaceAll('_', ' '), inputs: count_inputs(dish.ingredients), count: dish.count ?? 1, dish: dish.id });
}

const station_blocks = {};
for (const station of content.cook_stations ?? []) {
	station_blocks[station.station] ??= [];
	station_blocks[station.station].push({ id: `deepworld:${station.id}`, name: station.name, max_heat: station.max_heat ?? null, slots: station.slots ?? null });
}

const write_doc = (rel, front, body) => {
	const file = path.join(ref_dir, rel);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	const head = Object.entries(front).map(([key, value]) => `${key}: ${JSON.stringify(value)}`).join('\n');
	fs.writeFileSync(file, `---\n${head}\n---\n\n${body}\n`);
};
const box = (rows, image) => {
	const cells = rows.filter(([, value]) => value !== null && value !== undefined && value !== '').map(([key, value]) => `<tr><th>${key}</th><td>${value}</td></tr>`).join('');
	return `<div class="infobox">${image ?? ''}<table>${cells}</table></div>`;
};
const icon_img = (id, size = 'x2') => {
	const hit = exported[resolve(id)];
	return hit?.icon ? `<img class="icon ${size}" src="${hit.icon}" alt="">` : '';
};
const chips = (ids) => ids.map((id) => `${icon_img(id)}${link(id)}`).join(', ');
const landmark_name = (kind) => (content.landmarks ?? []).find((entry) => entry.kind === kind)?.name ?? plain(kind);
const hazard_link = (id) => {
	const hazard = hazard_defs.find((entry) => entry.id === id);
	return `<a href="${hazard.page}">${hazard.name}</a>${hazard.where ? ` (${hazard.where})` : ''}`;
};

const layer_pages = layers.map((layer, index) => {
	const above = layers[index - 1];
	const below = layers[index + 1];
	const hazards = [...(layer.heat > 0 ? [`<a href="/mechanics/heat/">heat ${layer.heat}</a>`] : []), ...layer.hazards.map(hazard_link)];
	const rows = [
		['layer', layer.number],
		['y', `${layer.y[0]} to ${layer.y[1]}`],
		['rock', layer.rock_name],
		['pickaxe', tier_name(layer.needs)],
		['heat', layer.heat],
		['biomes', layer.biomes.map(plain).join(', ')],
		['ores', layer.ores.length ? chips(layer.ores) : 'none'],
		['signature', chips(layer.signature)],
		['forage', layer.plants.length ? chips(layer.plants.map((plant) => plant.id)) : null],
		['hazards', hazards.length ? hazards.join(', ') : 'none'],
		['landmark', layer.landmark ? `<a href="/ref/landmarks/${layer.landmark}/">${landmark_name(layer.landmark)}</a>` : null],
	];
	const mob_lines = layer.mobs.length
		? layer.mobs.map((mob) => `- ${mob.name}: ${mob.where.join(', ')}`).join('\n')
		: layer.number === 0 ? '- vanilla nether spawns' : '- vanilla cave spawns';
	const nav = [above ? `up: [${above.name}](/ref/layers/${above.slug}/)` : 'up: the surface', below ? `down: [${below.name}](/ref/layers/${below.slug}/)` : 'down: nothing'].join(' · ');
	const body = [
		box(rows),
		`y ${layer.y[0]} to ${layer.y[1]}. ${layer.rock_name ?? 'rock'} needs a ${tier_name(layer.needs)} pickaxe or better.`,
		layer.heat > 0 ? `heat ${layer.heat} of 5. see the [heat calculator](/tools/heat/).` : 'no heat here.',
		'## mobs',
		mob_lines,
		'## signature ingredients',
		'one of these counts for this layer on a [tasting menu](/mechanics/meal-slots/#tasting-menu).',
		layer.signature.map((id) => `- ${link(id)}${sources[id] ? `: ${sources[id].join('; ')}` : ''}`).join('\n'),
		'---',
		nav,
		'',
		`[plan a trip here](/tools/expedition/?layer=${layer.slug})`,
	];
	write_doc(`layers/${layer.slug}.md`, { title: layer.name, description: `y ${layer.y[0]} to ${layer.y[1]}`, sidebar: { order: index + 1 } }, body.join('\n\n'));
	return layer;
});

const landmark_notes = {
	drowned_pump_station: 'right-click the dispenser with 4 copper ingots to drain the gallery around it.',
	fossil_dig: 'brush the suspicious gravel.',
	crystal_cathedral: 'tune the four note blocks to the right notes and the vault wall shatters. the notes differ per cathedral and there is no hint yet, so try them.',
};
(content.landmarks ?? []).forEach((landmark, index) => {
	const layer = layers.find((entry) => entry.landmark === landmark.kind);
	const pages = (content.journal_pages ?? []).filter((entry) => entry.landmark === landmark.kind).length;
	const rows = [
		['layer', layer ? `<a href="/ref/layers/${layer.slug}/">${layer.name}</a>` : null],
		['y', `${landmark.y[0]} to ${landmark.y[1]}`],
		['biomes', landmark.biomes.map(plain).join(', ')],
		['rarity', `${landmark.rarity}, higher is rarer`],
		['journal pages', pages],
	];
	const body = [
		box(rows),
		`a small build in ${landmark.biomes.map(plain).join(' or ')}. it has a chest${pages ? ` that can hold ${pages === 1 ? 'a journal page' : `${pages} journal pages`}` : ''}.`,
		landmark_notes[landmark.kind] ?? '',
		'walk within 24 blocks to count it as found, for the [archivist](/ref/disciplines/archivist/) and the logbook.',
		pages ? 'the pages themselves are on [the journal](/ref/story/journal/), behind spoilers.' : '',
	];
	write_doc(`landmarks/${landmark.kind}.md`, { title: landmark.name, description: layer ? layer.name : '', sidebar: { order: index + 1 } }, body.filter(Boolean).join('\n\n'));
});

const guide_of = { delver: 'delver', cook: 'cook', grower: 'grower', smith: 'smith', alchemist: 'alchemist', engineer: 'engineer', archivist: 'archivist', hunter: 'hunter' };
(content.disciplines ?? []).forEach((discipline, index) => {
	const milestones = (content.milestones ?? []).filter((entry) => entry.discipline === discipline.id);
	const rows = [
		['focus perk', discipline.perk],
		['milestones', milestones.length],
	];
	const table = ['| milestone | goal | reward |', '| --- | --- | --- |', ...milestones.map((entry) => {
		const [goal, reward] = entry.text.split(' · ');
		return `| ${entry.name} | ${goal} | ${reward ?? 'title'} |`;
	})].join('\n');
	const body = [
		box(rows),
		`focus on ${discipline.name} in the guild journal to get: ${discipline.perk}. you can focus on two disciplines and change one per in-game day.`,
		'## milestones',
		'milestones count up whether or not you focus. finishing one puts its name on your screen.',
		table,
		`[${discipline.name} guide](/guides/${guide_of[discipline.id]}/)`,
	];
	write_doc(`disciplines/${discipline.id}.md`, { title: discipline.name, description: discipline.perk, sidebar: { order: index + 1 } }, body.join('\n\n'));
});

const names = {};
const links = {};
const icons = {};
const remember = (id) => {
	if (!id || names[id]) return;
	names[id] = name_of(id);
	const url = link_of(id);
	if (url) links[id] = url;
	const icon = exported[resolve(id)]?.icon;
	if (icon) icons[id] = icon;
};
for (const id of Object.keys(exported)) remember(id);
for (const id of Object.keys(content.ingredients ?? {})) remember(id);
for (const list of Object.values(recipes)) for (const recipe of list) for (const [id] of recipe.inputs) remember(id);
for (const id of Object.keys(recipes)) remember(id);
for (const id of Object.keys(sources)) remember(id);
for (const dish of content.dishes ?? []) for (const id of dish.ingredients) remember(id);
remember('#meat');

const data = {
	names,
	links,
	icons,
	meats,
	layers,
	hazards: hazard_defs,
	tiers: content.tiers ?? [],
	armor: (content.armor ?? []).map((set) => ({ set: set.set, name: set.name, heat: set.heat })),
	tools: (content.tools ?? []).map((tool) => ({ id: `deepworld:${tool.id}`, name: tool.name, tier: tool.tier })),
	ingredients: content.ingredients ?? {},
	stations: station_blocks,
	dishes: (content.dishes ?? []).map((dish) => ({
		id: `deepworld:${dish.id}`,
		name: dish.name,
		station: dish.station,
		ingredients: dish.ingredients,
		buffs: dish.buffs,
		tier: dish.tier,
		duration: dish.duration,
		heat: dish.heat,
		kind: dish.kind,
		preserved: !!dish.preserved,
		special: dish.special ?? null,
		result: dish.result ?? null,
		count: dish.count ?? 1,
		nutrition: dish.nutrition,
	})),
	recipes,
	sources,
	landmarks: (content.landmarks ?? []).map((landmark) => ({ kind: landmark.kind, name: landmark.name, y: landmark.y })),
};
fs.rmSync(data_dir, { recursive: true, force: true });
fs.mkdirSync(data_dir, { recursive: true });
fs.writeFileSync(path.join(data_dir, 'deepworld.json'), JSON.stringify(data));
console.log(`layers: ${layers.length}, landmarks: ${(content.landmarks ?? []).length}, disciplines: ${(content.disciplines ?? []).length}`);
