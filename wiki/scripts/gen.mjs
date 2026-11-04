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
