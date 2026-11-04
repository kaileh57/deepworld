import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
	integrations: [
		starlight({
			title: 'deepworld',
			customCss: ['./src/styles/flat.css'],
			sidebar: [
				{ label: 'start', items: ['getting-started'] },
				{ label: 'dishes', autogenerate: { directory: 'ref/dishes' } },
				{ label: 'items', autogenerate: { directory: 'ref/items' } },
				{ label: 'blocks', autogenerate: { directory: 'ref/blocks' } },
			],
		}),
	],
});
