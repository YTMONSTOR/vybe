/* VYBE — Watch Together: synced playback, chat and live reactions over WebSocket */
(function () {
    'use strict';
    const { esc, toast, icons } = window.VYBE;
    const $ = s => document.querySelector(s);
    const code = window.VYBE_ROOM;
    const video = $('#tVideo'), log = $('#chatLog'), player = $('#tPlayer');
    let ws = null, applying = false, retry = 0;

    function connect() {
        const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
        ws = new WebSocket(proto + location.host + '/ws/together?room=' + encodeURIComponent(code));
        ws.onopen = () => { retry = 0; };
        ws.onmessage = e => handle(JSON.parse(e.data));
        ws.onclose = e => {
            if (e.code === 4404) { system('Комната закрыта'); return; }
            system('Соединение потеряно, переподключение…');
            setTimeout(connect, Math.min(10000, 1000 * Math.pow(2, retry++)));
        };
    }

    function send(obj) {
        if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
    }

    function apply(fn) {
        applying = true;
        try { fn(); } finally { setTimeout(() => { applying = false; }, 250); }
    }

    function sync(playing, time) {
        apply(() => {
            const drift = Math.abs(video.currentTime - time);
            if (drift > 0.8) video.currentTime = time;
            if (playing && video.paused) video.play().catch(() => system('Нажмите ▶, чтобы присоединиться к воспроизведению'));
            if (!playing && !video.paused) video.pause();
        });
    }

    function handle(m) {
        switch (m.type) {
            case 'welcome':
                setVideo(m.video, false);
                (m.chat || []).forEach(line => chat(line));
                const start = () => sync(m.playing, m.time);
                if (video.readyState >= 1) start(); else video.addEventListener('loadedmetadata', start, { once: true });
                system('Вы вошли как ' + m.you);
                break;
            case 'sync':
                sync(m.playing, m.time);
                if (m.by && m.action) system(m.by + ' — ' + ({ play: 'воспроизведение', pause: 'пауза', seek: 'перемотка' }[m.action] || m.action));
                break;
            case 'members':
                renderMembers(m.members);
                break;
            case 'chat':
                chat(m.line);
                break;
            case 'system':
                system(m.text);
                break;
            case 'reaction':
                floatEmoji(m.emoji);
                break;
            case 'video':
                setVideo(m.video, true);
                system(m.by + ' включил(а) «' + m.video.title + '»');
                break;
            case 'error':
                toast(m.text, 'error');
                break;
        }
    }

    function setVideo(v, reset) {
        if (!v) return;
        const abs = new URL(v.url, location.origin).href;
        if (video.src !== abs) {
            apply(() => { video.src = v.url; video.poster = v.thumbnail; });
        }
        $('#tTitle').textContent = v.title;
        $('#tAuthor').textContent = v.author;
        if (reset) apply(() => { video.currentTime = 0; video.pause(); });
    }

    function renderMembers(list) {
        $('#memberCount').textContent = list.length;
        $('#members').innerHTML = list.slice(0, 6).map(m =>
            '<span class="avatar" title="' + esc(m.name) + '" style="--h:' + m.hue + '">' + esc((m.name || '?').slice(0, 2).toUpperCase()) + '</span>').join('') +
            (list.length > 6 ? '<span class="badge" style="margin-left:6px">+' + (list.length - 6) + '</span>' : '');
    }

    function chat(line) {
        const d = document.createElement('div');
        d.className = 'chat-msg';
        d.innerHTML = '<span class="avatar" style="--h:' + line.hue + '">' + esc(line.name.slice(0, 2).toUpperCase()) + '</span>' +
            '<div style="--h:' + line.hue + '"><b>' + esc(line.name) + '</b><p>' + esc(line.text) + '</p></div>';
        log.appendChild(d);
        log.scrollTop = log.scrollHeight;
    }

    function system(text) {
        const d = document.createElement('div');
        d.className = 'chat-sys';
        d.textContent = text;
        log.appendChild(d);
        log.scrollTop = log.scrollHeight;
    }

    function floatEmoji(emoji) {
        const s = document.createElement('span');
        s.className = 'float-emoji';
        s.textContent = emoji;
        s.style.left = (10 + Math.random() * 80) + '%';
        s.style.setProperty('--r', (Math.random() * 40 - 20) + 'deg');
        player.appendChild(s);
        setTimeout(() => s.remove(), 2700);
    }

    video.addEventListener('play', () => { if (!applying) send({ type: 'state', playing: true, time: video.currentTime, action: 'play' }); });
    video.addEventListener('pause', () => { if (!applying && !video.ended) send({ type: 'state', playing: false, time: video.currentTime, action: 'pause' }); });
    video.addEventListener('seeked', () => { if (!applying) send({ type: 'state', playing: !video.paused, time: video.currentTime, action: 'seek' }); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) send({ type: 'sync-request' }); });

    $('#chatForm').addEventListener('submit', e => {
        e.preventDefault();
        const input = e.target.text;
        const text = input.value.trim();
        if (!text) return;
        send({ type: 'chat', text });
        input.value = '';
    });
    $('#reactions').addEventListener('click', e => {
        const b = e.target.closest('button');
        if (b) send({ type: 'reaction', emoji: b.textContent.trim() });
    });
    document.querySelectorAll('.pick-video').forEach(b => b.addEventListener('click', () => {
        send({ type: 'video', videoId: +b.dataset.id });
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }));
    $('#inviteBtn').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(location.href); toast('Ссылка-приглашение скопирована'); }
        catch (e) { toast(location.href); }
    });

    icons();
    connect();
})();
