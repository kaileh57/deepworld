---
title: server
description: live from the column.
---

<div id="live">loading…</div>

<script>
	const url = new URLSearchParams(location.search).get('live') ?? `http://${location.hostname}:8125/live.json`;
	const el = document.getElementById('live');
	const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
	const list = (title, rows) => `<h3>${title}</h3>` + (rows.length ? `<ul>${rows.map((r) => `<li>${r}</li>`).join('')}</ul>` : '<p>none</p>');
	const draw = async () => {
		try {
			const data = await (await fetch(url)).json();
			el.innerHTML = [
				data.goal ? `<p><strong>weekly goal</strong> ${esc(data.goal)}${data.goal_met ? ' (met)' : ''}</p>` : '',
				data.breakthrough ? `<p>first through to the nether stratum: ${esc(data.breakthrough)}</p>` : '',
				list('online', data.players.map((p) => `${esc(p.name)}, ${esc(p.layer)}`)),
				list('records', data.records.map((r) => `${esc(r.record)}: ${esc(r.name)} (${esc(r.value)})`)),
				list('commissions', data.commissions.map((c) => `${esc(c.count)} ${esc(c.wanted.split(':')[1])} for ${esc(c.poster)}${c.taken ? ', taken' : ''}`)),
				list('recent', data.events.map(esc)),
				`<p>${esc(data.landmarks)} landmarks generated so far.</p>`,
			].join('');
		} catch {
			el.textContent = 'the server is offline.';
		}
	};
	draw();
	setInterval(draw, 30000);
</script>
