import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
	integrations: [
		starlight({
			title: 'deepworld',
			customCss: ['./src/styles/flat.css'],
			sidebar: [
				{ label: 'start', items: ['getting-started', 'server'] },
				{ label: 'guides', autogenerate: { directory: 'guides' } },
				{ label: 'mechanics', autogenerate: { directory: 'mechanics' } },
				{ label: 'tools', autogenerate: { directory: 'tools' } },
				{ label: 'layers', autogenerate: { directory: 'ref/layers' } },
				{ label: 'landmarks', autogenerate: { directory: 'ref/landmarks' }, collapsed: true },
				{ label: 'disciplines', autogenerate: { directory: 'ref/disciplines' }, collapsed: true },
				{ label: 'story', autogenerate: { directory: 'ref/story' } },
				{ label: 'dishes', autogenerate: { directory: 'ref/dishes' }, collapsed: true },
				{ label: 'items', autogenerate: { directory: 'ref/items' }, collapsed: true },
				{ label: 'blocks', autogenerate: { directory: 'ref/blocks' }, collapsed: true },
			],
		}),
	],
});
