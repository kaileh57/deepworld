window.dw = (() => {
	let cache = null;
	const load = () => (cache ??= fetch('/data/deepworld.json').then((response) => response.json()));
	const esc = (text) => String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
	const plain = (id) => id.split(':').pop().replace(/^dish\//, '').replaceAll('_', ' ');
	const name_of = (data, id) => data.names[id] ?? plain(id);
	const icon_of = (data, id) => (data.icons[id] ? `<img class="icon x2" src="${data.icons[id]}" alt="">` : '');
	const link_of = (data, id) => {
		const url = data.links[id];
		const label = esc(name_of(data, id));
		return url ? `${icon_of(data, id)}<a href="${url}">${label}</a>` : label;
	};
	const roman = (level) => ['', 'i', 'ii', 'iii', 'iv', 'v'][level] ?? String(level);
	const buff_text = (buffs) => Object.entries(buffs).map(([key, level]) => `${key.replaceAll('_', ' ')} ${roman(level)}`).join(', ') || 'none';
	const param = (key) => new URLSearchParams(location.search).get(key);
	const minutes = (seconds) => {
		const whole = Math.round(seconds);
		const m = Math.floor(whole / 60);
		const s = String(whole % 60).padStart(2, '0');
		return `${m}:${s}`;
	};
	const dish_ref = (id) => {
		if (id.startsWith('deepworld:dish/')) return `deepworld:${id.slice(15)}`;
		if (id.startsWith('drink:')) return `deepworld:${id.slice(6)}`;
		return id;
	};
	const fill = (select, options, first) => {
		select.innerHTML = (first ? `<option value="">${esc(first)}</option>` : '') + options.map(([value, label]) => `<option value="${esc(value)}">${esc(label)}</option>`).join('');
	};
	return { load, esc, plain, name_of, icon_of, link_of, roman, buff_text, param, minutes, dish_ref, fill };
})();
