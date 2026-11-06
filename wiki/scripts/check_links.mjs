import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const wiki_dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dist_dir = path.join(wiki_dir, 'dist');

if (!fs.existsSync(dist_dir)) {
	console.error('no dist, run npm run build first');
	process.exit(1);
}

const walk = (dir) => fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
	const full = path.join(dir, entry.name);
	return entry.isDirectory() ? walk(full) : [full];
});

const resolves = (target) => {
	const clean = decodeURIComponent(target.split('#')[0].split('?')[0]);
	if (clean === '' || clean === '/') return fs.existsSync(path.join(dist_dir, 'index.html'));
	const file = path.join(dist_dir, clean);
	if (!file.startsWith(dist_dir)) return false;
	if (fs.existsSync(file) && fs.statSync(file).isFile()) return true;
	if (fs.existsSync(path.join(file, 'index.html'))) return true;
	return fs.existsSync(`${file}.html`);
};

const pages = walk(dist_dir).filter((file) => file.endsWith('.html'));
const broken = [];
let checked = 0;
for (const page of pages) {
	const html = fs.readFileSync(page, 'utf8').replace(/(<script\b[^>]*>)[\s\S]*?(<\/script>)/gi, '$1$2');
	const base = '/' + path.relative(dist_dir, path.dirname(page)).split(path.sep).filter(Boolean).join('/');
	for (const match of html.matchAll(/\s(?:href|src)="([^"]*)"/g)) {
		const raw = match[1].replaceAll('&amp;', '&');
		if (/^(?:[a-z]+:|\/\/|#|data:)/i.test(raw) || raw === '') continue;
		const target = raw.startsWith('/') ? raw : path.posix.join(base.endsWith('/') ? base : `${base}/`, raw);
		checked++;
		if (!resolves(target)) broken.push(`${path.relative(dist_dir, page)}: ${raw}`);
	}
}

console.log(`${pages.length} pages, ${checked} links`);
if (broken.length) {
	console.error(`${broken.length} broken:`);
	for (const line of broken) console.error(`  ${line}`);
	process.exit(1);
}
console.log('no broken links');
