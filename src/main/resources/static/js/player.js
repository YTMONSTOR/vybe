/* =====================================================================
   VYBE — watch page: custom HTML5 player, chapters, focus mode,
   floating/mini player, reactions, subscriptions, comments, reports
   ===================================================================== */
(function () {
    'use strict';
    const V = window.VYBE_VIDEO;
    const { api, toast, esc, icons, modal, openSaveModal, requireLogin, Mini, formatTime } = window.VYBE;
    const $ = (s, r = document) => r.querySelector(s);
    const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

    const player = $('#player'), video = $('#video'), wrap = $('#playerWrap');
    const progress = $('#progress'), fill = $('#fill'), buffer = $('#buffer'), hoverBar = $('#hoverBar');
    const thumb = $('#thumb'), tip = $('#tip'), track = $('#track');
    const btnPlay = $('#btnPlay'), btnMute = $('#btnMute'), volume = $('#volume');
    const curEl = $('#cur'), durEl = $('#dur'), chapName = $('#chapName');

    const chapters = $$('#chapters .chapter').map(b => ({ start: +b.dataset.start, title: $('b', b).textContent, el: b }));
    const nextEl = $('#nextData');
    const next = nextEl ? { id: nextEl.dataset.id, title: nextEl.dataset.title, thumb: nextEl.dataset.thumb, url: nextEl.dataset.url } : null;

    const store = {
        get(k, d) { try { const v = localStorage.getItem('vybe.' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
        set(k, v) { try { localStorage.setItem('vybe.' + k, JSON.stringify(v)); } catch (e) { /* ignore */ } }
    };

    function setIcon(el, name) {
        if (!el) return;
        const badge = el.querySelector('.cbadge');
        el.innerHTML = '<i data-lucide="' + name + '"></i>';
        if (badge) el.appendChild(badge);
        icons(el);
    }

    // ------------------------------------------------------------ playback basics
    video.volume = store.get('volume', 1);
    video.muted = store.get('muted', false);
    volume.value = video.muted ? 0 : video.volume;
    let autoplayNext = store.get('autoplay', true);
    $('#btnAutoplay') && $('#btnAutoplay').classList.toggle('on', autoplayNext);

    function togglePlay() {
        if (video.paused || video.ended) video.play().catch(() => {});
        else video.pause();
    }

    function flash(iconName) {
        const f = $('#flash');
        f.innerHTML = '<i data-lucide="' + iconName + '"></i>';
        icons(f);
        f.classList.remove('show');
        void f.offsetWidth;
        f.classList.add('show');
    }

    video.addEventListener('play', () => { player.classList.add('playing'); setIcon(btnPlay, 'pause'); });
    video.addEventListener('pause', () => { player.classList.remove('playing'); setIcon(btnPlay, 'play'); saveProgress(); showControls(); });
    video.addEventListener('waiting', () => player.classList.add('waiting'));
    video.addEventListener('canplay', () => player.classList.remove('waiting'));
    video.addEventListener('playing', () => player.classList.remove('waiting'));

    video.addEventListener('loadedmetadata', () => {
        durEl.textContent = formatTime(video.duration);
        renderChapterMarks();
        if (V.startAt > 0 && V.startAt < video.duration - 3) video.currentTime = V.startAt;
    });

    video.addEventListener('timeupdate', () => {
        const d = video.duration || 0;
        const t = video.currentTime;
        if (!dragging) {
            const pct = d ? (t / d) * 100 : 0;
            fill.style.width = pct + '%';
            thumb.style.left = pct + '%';
        }
        curEl.textContent = formatTime(t);
        updateChapter(t);
        trackView();
    });

    video.addEventListener('progress', () => {
        if (!video.duration || !video.buffered.length) return;
        const end = video.buffered.end(video.buffered.length - 1);
        buffer.style.width = (end / video.duration) * 100 + '%';
    });

    video.addEventListener('ended', () => {
        saveProgress(true);
        if (next && autoplayNext) showNextOverlay();
    });

    $('#bigPlay').addEventListener('click', togglePlay);
    btnPlay.addEventListener('click', togglePlay);

    let clickTimer = null;
    video.addEventListener('click', () => {
        clearTimeout(clickTimer);
        clickTimer = setTimeout(() => { togglePlay(); flash(video.paused ? 'pause' : 'play'); }, 180);
    });
    video.addEventListener('dblclick', e => {
        clearTimeout(clickTimer);
        const rect = video.getBoundingClientRect();
        const x = e.clientX - rect.left;
        if (x < rect.width * 0.3) seekBy(-10);
        else if (x > rect.width * 0.7) seekBy(10);
        else toggleFullscreen();
    });

    function seekBy(sec) {
        video.currentTime = Math.max(0, Math.min((video.duration || 0), video.currentTime + sec));
        const hint = sec < 0 ? $('#hintLeft') : $('#hintRight');
        hint.textContent = (sec < 0 ? '−' : '+') + Math.abs(sec) + ' с';
        hint.classList.add('show');
        clearTimeout(hint._t);
        hint._t = setTimeout(() => hint.classList.remove('show'), 600);
    }

    // ------------------------------------------------------------ volume
    function renderVolume() {
        const v = video.muted ? 0 : video.volume;
        setIcon(btnMute, v === 0 ? 'volume-x' : v < 0.5 ? 'volume-1' : 'volume-2');
        volume.value = v;
        volume.style.background = 'linear-gradient(90deg,#fff ' + (v * 100) + '%, rgba(255,255,255,.25) ' + (v * 100) + '%)';
    }
    btnMute.addEventListener('click', () => { video.muted = !video.muted; if (!video.muted && video.volume === 0) video.volume = 0.5; });
    volume.addEventListener('input', () => { video.volume = +volume.value; video.muted = +volume.value === 0; });
    video.addEventListener('volumechange', () => { store.set('volume', video.volume); store.set('muted', video.muted); renderVolume(); });
    renderVolume();

    // ------------------------------------------------------------ progress bar & chapters
    let dragging = false;

    function pctFromEvent(e) {
        const rect = track.getBoundingClientRect();
        const x = (e.touches ? e.touches[0].clientX : e.clientX) - rect.left;
        return Math.max(0, Math.min(1, x / rect.width));
    }

    function chapterAt(t) {
        let c = null;
        for (const ch of chapters) if (t >= ch.start) c = ch;
        return c;
    }

    function showTip(p) {
        const d = video.duration || V.duration || 0;
        const t = p * d;
        const ch = chapterAt(t);
        tip.innerHTML = (ch ? '<small>' + esc(ch.title) + '</small>' : '') + formatTime(t);
        const rect = track.getBoundingClientRect();
        const half = tip.offsetWidth / 2;
        tip.style.left = Math.max(half, Math.min(rect.width - half, p * rect.width)) + 'px';
        hoverBar.style.width = p * 100 + '%';
    }

    progress.addEventListener('mousemove', e => showTip(pctFromEvent(e)));
    progress.addEventListener('pointerdown', e => {
        dragging = true;
        progress.classList.add('dragging');
        progress.setPointerCapture(e.pointerId);
        const p = pctFromEvent(e);
        fill.style.width = p * 100 + '%';
        thumb.style.left = p * 100 + '%';
        showTip(p);
    });
    progress.addEventListener('pointermove', e => {
        if (!dragging) return;
        const p = pctFromEvent(e);
        fill.style.width = p * 100 + '%';
        thumb.style.left = p * 100 + '%';
        showTip(p);
    });
    progress.addEventListener('pointerup', e => {
        if (!dragging) return;
        dragging = false;
        progress.classList.remove('dragging');
        const p = pctFromEvent(e);
        if (video.duration) video.currentTime = p * video.duration;
    });

    function renderChapterMarks() {
        $$('.chapter-mark', track).forEach(m => m.remove());
        const d = video.duration;
        if (!d) return;
        chapters.forEach(c => {
            if (c.start <= 0 || c.start >= d) return;
            const m = document.createElement('div');
            m.className = 'chapter-mark';
            m.style.left = (c.start / d) * 100 + '%';
            track.appendChild(m);
        });
    }

    let currentChapter = null;
    function updateChapter(t) {
        const c = chapterAt(t);
        if (c === currentChapter) return;
        currentChapter = c;
        chapName.textContent = c ? '• ' + c.title : '';
        chapters.forEach(ch => ch.el.classList.toggle('active', ch === c));
        if (c && c.el.parentElement) {
            const list = c.el.parentElement;
            list.scrollTo({ left: c.el.offsetLeft - list.clientWidth / 2 + c.el.clientWidth / 2, behavior: 'smooth' });
        }
    }

    function jumpTo(sec) {
        video.currentTime = sec;
        video.play().catch(() => {});
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    chapters.forEach(c => c.el.addEventListener('click', () => jumpTo(c.start)));

    // ------------------------------------------------------------ speed
    const speedMenu = $('#speedMenu'), speedBadge = $('#speedBadge');
    function setSpeed(r) {
        video.playbackRate = r;
        $$('button', speedMenu).forEach(b => b.classList.toggle('active', +b.dataset.speed === r));
        speedBadge.hidden = r === 1;
        speedBadge.textContent = r + 'x';
        store.set('speed', r);
    }
    setSpeed(store.get('speed', 1));
    $('#btnSpeed').addEventListener('click', e => { e.stopPropagation(); speedMenu.classList.toggle('open'); });
    speedMenu.addEventListener('click', e => {
        const b = e.target.closest('button[data-speed]');
        if (b) { setSpeed(+b.dataset.speed); speedMenu.classList.remove('open'); }
    });
    document.addEventListener('click', e => { if (!speedMenu.contains(e.target)) speedMenu.classList.remove('open'); });

    // ------------------------------------------------------------ autoplay & next overlay
    const btnAuto = $('#btnAutoplay');
    if (btnAuto) btnAuto.addEventListener('click', () => {
        autoplayNext = !autoplayNext;
        store.set('autoplay', autoplayNext);
        btnAuto.classList.toggle('on', autoplayNext);
        toast(autoplayNext ? 'Автовоспроизведение включено' : 'Автовоспроизведение выключено');
    });

    function showNextOverlay() {
        if ($('.next-overlay', player)) return;
        const o = document.createElement('div');
        o.className = 'next-overlay';
        const C = 2 * Math.PI * 23;
        o.innerHTML = '<div class="next-card"><svg class="next-ring" viewBox="0 0 54 54">' +
            '<circle cx="27" cy="27" r="23" stroke="rgba(255,255,255,.15)"></circle>' +
            '<circle id="ring" cx="27" cy="27" r="23" stroke="#a78bfa" stroke-dasharray="' + C + '" stroke-dashoffset="0" transform="rotate(-90 27 27)" style="transition:stroke-dashoffset 1s linear"></circle></svg>' +
            '<small>Далее через <span id="cd">6</span> с</small><img src="' + esc(next.thumb) + '" alt=""><b>' + esc(next.title) + '</b>' +
            '<div style="display:flex;gap:10px;justify-content:center"><button class="btn btn-glass" data-x>Отмена</button>' +
            '<a class="btn btn-primary" href="' + esc(next.url) + '"><i data-lucide="play"></i>Смотреть</a></div></div>';
        player.appendChild(o);
        icons(o);
        let left = 6;
        const ring = $('#ring', o);
        const timer = setInterval(() => {
            left--;
            $('#cd', o).textContent = left;
            ring.style.strokeDashoffset = (C * (6 - left) / 6);
            if (left <= 0) { clearInterval(timer); location.href = next.url; }
        }, 1000);
        $('[data-x]', o).addEventListener('click', () => { clearInterval(timer); o.remove(); });
    }

    // ------------------------------------------------------------ fullscreen, idle UI
    function toggleFullscreen() {
        if (document.fullscreenElement) document.exitFullscreen();
        else if (player.requestFullscreen) player.requestFullscreen().catch(() => {});
        else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen();
    }
    $('#btnFull').addEventListener('click', toggleFullscreen);
    document.addEventListener('fullscreenchange', () => {
        const fs = document.fullscreenElement === player;
        player.classList.toggle('fullscreen', fs);
        setIcon($('#btnFull'), fs ? 'minimize' : 'maximize');
    });

    let idleTimer = null;
    function showControls() {
        player.classList.remove('idle');
        clearTimeout(idleTimer);
        idleTimer = setTimeout(() => { if (!video.paused && !speedMenu.classList.contains('open')) player.classList.add('idle'); }, 2600);
    }
    player.addEventListener('mousemove', showControls);
    player.addEventListener('touchstart', showControls, { passive: true });
    player.addEventListener('mouseleave', () => { if (!video.paused) player.classList.add('idle'); });

    // ------------------------------------------------------------ Focus Mode
    function toggleFocus(force) {
        const on = typeof force === 'boolean' ? force : !document.body.classList.contains('focus-mode');
        document.body.classList.toggle('focus-mode', on);
        player.classList.toggle('focus-ui', on);
        $('#btnFocus').classList.toggle('on', on);
        if (on) { window.scrollTo({ top: 0, behavior: 'smooth' }); toast('Focus Mode: только вы и видео. Нажмите T, чтобы выйти'); }
    }
    $('#btnFocus').addEventListener('click', () => toggleFocus());
    $('#focusExit').addEventListener('click', () => toggleFocus(false));

    // ------------------------------------------------------------ floating player on scroll + Mini Player
    let floatingDismissed = false;
    const placeholder = $('#playerPlaceholder');
    if ('IntersectionObserver' in window) {
        const io = new IntersectionObserver(entries => {
            const e = entries[0];
            const shouldFloat = !e.isIntersecting && !video.paused && !floatingDismissed && !document.fullscreenElement
                && !document.body.classList.contains('focus-mode') && e.boundingClientRect.top < 0;
            wrap.classList.toggle('floating', shouldFloat);
        }, { threshold: 0.15 });
        io.observe(wrap);
    }
    placeholder.addEventListener('click', () => { wrap.classList.remove('floating'); window.scrollTo({ top: 0, behavior: 'smooth' }); });

    function toMiniPlayer() {
        const t = video.currentTime || 0;
        const wasPlaying = !video.paused;
        video.pause();
        floatingDismissed = true;
        wrap.classList.remove('floating');
        Mini.open({ id: V.id, src: V.src, title: V.title, author: V.author, time: t, playing: wasPlaying || true });
        toast('Мини-плеер включён — можно продолжить просмотр на других страницах');
    }
    $('#btnMini').addEventListener('click', toMiniPlayer);
    video.addEventListener('play', () => { if (Mini.state && String(Mini.state.id) === String(V.id)) Mini.close(); });

    // ------------------------------------------------------------ ambient glow
    const canvas = $('#ambient');
    const ctx = canvas && canvas.getContext ? canvas.getContext('2d') : null;
    function paintAmbient() {
        if (!ctx || video.paused || document.hidden) return;
        try { ctx.drawImage(video, 0, 0, canvas.width, canvas.height); } catch (e) { /* not ready */ }
    }
    setInterval(paintAmbient, 400);
    video.addEventListener('loadeddata', paintAmbient);

    // ------------------------------------------------------------ keyboard shortcuts
    document.addEventListener('keydown', e => {
        const tag = (e.target.tagName || '').toLowerCase();
        if (tag === 'input' || tag === 'textarea' || tag === 'select' || e.target.isContentEditable) return;
        if (e.ctrlKey || e.metaKey || e.altKey) return;
        const k = e.key.toLowerCase();
        if (k === ' ' || k === 'k') { e.preventDefault(); togglePlay(); flash(video.paused ? 'pause' : 'play'); }
        else if (k === 'arrowleft' || k === 'j') { e.preventDefault(); seekBy(k === 'j' ? -10 : -5); }
        else if (k === 'arrowright' || k === 'l') { e.preventDefault(); seekBy(k === 'l' ? 10 : 5); }
        else if (k === 'arrowup') { e.preventDefault(); video.muted = false; video.volume = Math.min(1, video.volume + 0.1); }
        else if (k === 'arrowdown') { e.preventDefault(); video.volume = Math.max(0, video.volume - 0.1); }
        else if (k === 'f') toggleFullscreen();
        else if (k === 'm') video.muted = !video.muted;
        else if (k === 't') toggleFocus();
        else if (k === 'i') toMiniPlayer();
        else if (k === 'n' && e.shiftKey && next) location.href = next.url;
        else if (k === 'escape' && document.body.classList.contains('focus-mode')) toggleFocus(false);
        else if (/^[0-9]$/.test(k) && video.duration) video.currentTime = video.duration * (+k / 10);
        else if (e.key === '>' ) setSpeed(Math.min(2, video.playbackRate + 0.25));
        else if (e.key === '<') setSpeed(Math.max(0.25, video.playbackRate - 0.25));
        else return;
        showControls();
    });

    // ------------------------------------------------------------ views & progress
    let watchedSeconds = 0, lastTick = null, viewSent = false;
    function trackView() {
        const now = performance.now();
        if (!video.paused && lastTick !== null) watchedSeconds += Math.min(1, (now - lastTick) / 1000);
        lastTick = video.paused ? null : now;
        const need = Math.min(5, (video.duration || 30) * 0.3);
        if (!viewSent && watchedSeconds >= need) {
            viewSent = true;
            api('/api/videos/' + V.id + '/view', { method: 'POST' })
                .then(r => { if (r && r.label) $('#viewsLabel').textContent = r.label; })
                .catch(() => { viewSent = false; });
        }
    }
    video.addEventListener('pause', () => { lastTick = null; });

    let lastSaved = 0;
    function saveProgress(finished) {
        if (!V.loggedIn || !video.duration) return;
        const pos = finished ? Math.floor(video.duration) : Math.floor(video.currentTime);
        if (!finished && Math.abs(pos - lastSaved) < 3) return;
        lastSaved = pos;
        const token = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
        const header = (document.querySelector('meta[name="_csrf_header"]') || {}).content || 'X-CSRF-TOKEN';
        const headers = { 'Content-Type': 'application/json' };
        headers[header] = token;
        fetch('/api/videos/' + V.id + '/progress', {
            method: 'POST', headers, body: JSON.stringify({ position: pos }), keepalive: true, credentials: 'same-origin'
        }).catch(() => {});
    }
    setInterval(() => { if (!video.paused) saveProgress(); }, 10000);
    window.addEventListener('pagehide', () => saveProgress());

    // ------------------------------------------------------------ description
    const desc = $('#descBox'), descText = $('#descText'), descToggle = $('#descToggle');
    // linkify timestamps like 1:23 or 01:02:03 in the description
    descText.innerHTML = esc(descText.textContent).replace(/(^|[\s(\[])((?:\d{1,2}:)?\d{1,2}:\d{2})(?=$|[\s)\].,])/gm, (m, pre, ts) => {
        const sec = ts.split(':').reduce((a, b) => a * 60 + (+b), 0);
        return pre + '<a class="ts" data-t="' + sec + '">' + ts + '</a>';
    }).replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener nofollow" style="color:var(--violet-2)">$1</a>');
    descText.addEventListener('click', e => {
        const a = e.target.closest('a.ts');
        if (a) { e.stopPropagation(); jumpTo(+a.dataset.t); }
    });
    function setDesc(open) {
        desc.classList.toggle('expanded', open);
        descToggle.textContent = open ? 'Свернуть' : 'Ещё';
    }
    if (descText.scrollHeight <= descText.clientHeight + 4) descToggle.hidden = true;
    desc.addEventListener('click', e => { if (!desc.classList.contains('expanded') && !e.target.closest('a')) setDesc(true); });
    descToggle.addEventListener('click', e => { e.stopPropagation(); setDesc(!desc.classList.contains('expanded')); });

    // ------------------------------------------------------------ reactions, subscribe, favorite, share, save, report
    const btnLike = $('#btnLike'), btnDislike = $('#btnDislike');
    async function react(type) {
        if (!requireLogin()) return;
        try {
            const r = await api('/api/videos/' + V.id + '/react', { method: 'POST', body: { type } });
            btnLike.classList.toggle('active', r.reaction === 'LIKE');
            btnDislike.classList.toggle('active', r.reaction === 'DISLIKE');
            $('#likeCount').textContent = compact(r.likes);
            $('#dislikeCount').textContent = compact(r.dislikes);
            const b = type === 'LIKE' ? btnLike : btnDislike;
            b.classList.remove('pulse'); void b.offsetWidth; b.classList.add('pulse');
        } catch (e) { toast(e.message, 'error'); }
    }
    function compact(n) {
        if (n < 1000) return String(n);
        if (n < 1e6) return (Math.floor(n / 100) / 10).toString().replace('.', ',') + ' тыс.';
        return (Math.floor(n / 1e5) / 10).toString().replace('.', ',') + ' млн';
    }
    btnLike.addEventListener('click', () => react('LIKE'));
    btnDislike.addEventListener('click', () => react('DISLIKE'));

    const btnSub = $('#btnSubscribe');
    if (btnSub) btnSub.addEventListener('click', async () => {
        if (!requireLogin()) return;
        try {
            const r = await api('/api/channels/' + btnSub.dataset.channel + '/subscribe', { method: 'POST' });
            btnSub.classList.toggle('subscribed', r.active);
            btnSub.innerHTML = '<i data-lucide="' + (r.active ? 'bell-ring' : 'bell-plus') + '"></i><span>' + (r.active ? 'Вы подписаны' : 'Подписаться') + '</span>';
            icons(btnSub);
            $('#subsLabel').textContent = r.label;
            toast(r.active ? 'Вы подписались на канал' : 'Подписка отменена');
        } catch (e) { toast(e.message, 'error'); }
    });

    const btnFav = $('#btnFav');
    btnFav.addEventListener('click', async () => {
        if (!requireLogin()) return;
        try {
            const r = await api('/api/videos/' + V.id + '/favorite', { method: 'POST' });
            btnFav.classList.toggle('active', r.active);
            const label = btnFav.querySelector('span');
            if (label) label.textContent = r.active ? 'В избранном' : 'В избранное';
            toast(r.active ? 'Добавлено в избранное' : 'Удалено из избранного');
        } catch (e) { toast(e.message, 'error'); }
    });

    $('#btnSave').addEventListener('click', () => openSaveModal(V.id));

    $('#btnShare').addEventListener('click', async () => {
        const t = Math.floor(video.currentTime || 0);
        const url = location.origin + '/watch/' + V.id + (t > 5 ? '?t=' + t : '');
        const m = modal('<div class="modal-head"><h3>Поделиться</h3><button class="btn btn-ghost btn-icon btn-sm" data-close><i data-lucide="x"></i></button></div>' +
            '<div class="field"><label class="label">Ссылка на видео</label><div style="display:flex;gap:8px"><input class="input" id="shareUrl" readonly value="' + esc(url) + '">' +
            '<button class="btn btn-primary" id="copyBtn"><i data-lucide="copy"></i></button></div>' +
            (t > 5 ? '<label class="check" style="margin-top:10px"><input type="checkbox" id="withTime" checked>Начать с ' + formatTime(t) + '</label>' : '') + '</div>');
        const input = $('#shareUrl', m.root);
        const wt = $('#withTime', m.root);
        if (wt) wt.addEventListener('change', () => { input.value = location.origin + '/watch/' + V.id + (wt.checked ? '?t=' + t : ''); });
        $('#copyBtn', m.root).addEventListener('click', async () => {
            try { await navigator.clipboard.writeText(input.value); } catch (e) { input.select(); document.execCommand('copy'); }
            toast('Ссылка скопирована');
            m.close();
        });
    });

    const btnReport = $('#btnReport');
    if (btnReport) btnReport.addEventListener('click', () => {
        if (!requireLogin()) return;
        const m = modal($('#reportTpl').innerHTML);
        $('#reportForm', m.root).addEventListener('submit', async e => {
            e.preventDefault();
            const f = e.target;
            try {
                const r = await api('/api/videos/' + V.id + '/report', {
                    method: 'POST', body: { reason: f.reason.value, details: f.details.value }
                });
                toast(r.message);
                m.close();
            } catch (err) { toast(err.message, 'error'); }
        });
    });

    // ------------------------------------------------------------ comments
    const list = $('#commentList'), more = $('#moreComments'), skeleton = $('#commentSkeleton');
    let page = 0;

    function avatarHtml(a, size) {
        return '<a href="/channel/' + esc(a.username) + '" class="avatar avatar-' + size + '" style="--h:' + a.hue + '">' +
            (a.avatarUrl ? '<img src="' + esc(a.avatarUrl) + '" alt="">' : esc(a.initials)) + '</a>';
    }

    function commentHtml(c, isReply) {
        return '<div class="comment" data-id="' + c.id + '">' + avatarHtml(c.author, isReply ? 'sm' : 'md') +
            '<div class="comment-body">' +
            (c.pinned ? '<div class="comment-pinned"><i data-lucide="pin"></i>Закреплено автором</div>' : '') +
            '<div class="comment-top"><a href="/channel/' + esc(c.author.username) + '"' + (c.byCreator ? ' class="creator"' : '') + '>@' + esc(c.author.username) + '</a>' +
            '<time>' + esc(c.ago) + '</time>' +
            (c.hearted ? '<span class="heart-badge"><i data-lucide="heart"></i>от автора</span>' : '') + '</div>' +
            '<div class="comment-text">' + esc(c.text) + '</div>' +
            '<div class="comment-tools">' +
            (V.loggedIn ? '<button class="btn btn-ghost" data-act="reply" data-root="' + (c.parentId || c.id) + '" data-user="' + esc(c.author.username) + '"><i data-lucide="reply"></i>Ответить</button>' : '') +
            (c.canModerate && !isReply ? '<button class="btn btn-ghost" data-act="pin"><i data-lucide="pin"></i>' + (c.pinned ? 'Открепить' : 'Закрепить') + '</button>' : '') +
            (c.canModerate ? '<button class="btn btn-ghost" data-act="heart"><i data-lucide="heart"></i>' + (c.hearted ? 'Убрать' : 'Сердце') + '</button>' : '') +
            (c.canDelete ? '<button class="btn btn-ghost" data-act="delete"><i data-lucide="trash-2"></i>Удалить</button>' : '') +
            '</div>' +
            (!isReply ? '<div class="replies">' + (c.replyCount > 0 ? '<button class="replies-toggle" data-act="replies"><i data-lucide="chevron-down"></i>' + c.replyCount + ' ' + plural(c.replyCount, 'ответ', 'ответа', 'ответов') + '</button>' : '') +
                '<div class="reply-list" hidden></div><div class="reply-slot"></div></div>' : '') +
            '</div></div>';
    }

    function plural(n, one, few, many) {
        const a = Math.abs(n) % 100, b = a % 10;
        if (a > 10 && a < 20) return many;
        if (b > 1 && b < 5) return few;
        if (b === 1) return one;
        return many;
    }

    async function loadComments() {
        try {
            const r = await api('/api/videos/' + V.id + '/comments?page=' + page);
            skeleton.hidden = true;
            if (page === 0 && !r.items.length) {
                list.innerHTML = r.enabled ? '<p class="dim" style="padding:10px 0 20px">Комментариев пока нет — будьте первым!</p>' : '';
            } else {
                list.insertAdjacentHTML('beforeend', r.items.map(c => commentHtml(c, false)).join(''));
            }
            more.hidden = !r.hasMore;
            icons(list);
        } catch (e) {
            skeleton.hidden = true;
            list.innerHTML = '<p class="field-error">' + esc(e.message) + '</p>';
        }
    }
    more.addEventListener('click', () => { page++; loadComments(); });

    function bindCommentForm(form, parentId, onDone) {
        const ta = form.querySelector('textarea');
        const actions = form.querySelector('.comment-form-actions');
        ta.addEventListener('focus', () => { if (actions) actions.hidden = false; });
        ta.addEventListener('input', () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; });
        const cancel = form.querySelector('[data-cancel]');
        if (cancel) cancel.addEventListener('click', () => {
            ta.value = '';
            ta.style.height = '';
            if (parentId) form.remove(); else if (actions) actions.hidden = true;
        });
        form.addEventListener('submit', async e => {
            e.preventDefault();
            const text = ta.value.trim();
            if (!text) return;
            const btn = form.querySelector('[type=submit]');
            btn.disabled = true;
            try {
                const c = await api('/api/videos/' + V.id + '/comments', { method: 'POST', body: { text, parentId } });
                ta.value = '';
                ta.style.height = '';
                onDone(c);
                const total = $('#commentTotal');
                total.textContent = (+total.textContent || 0) + 1;
            } catch (err) { toast(err.message, 'error'); }
            finally { btn.disabled = false; }
        });
    }

    const mainForm = $('#commentForm');
    if (mainForm) bindCommentForm(mainForm, null, c => {
        const empty = list.querySelector('p.dim');
        if (empty) empty.remove();
        list.insertAdjacentHTML('afterbegin', commentHtml(c, false));
        icons(list);
        mainForm.querySelector('.comment-form-actions').hidden = true;
    });

    list.addEventListener('click', async e => {
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        const el = btn.closest('.comment');
        const id = el.dataset.id;
        const act = btn.dataset.act;
        try {
            if (act === 'replies') {
                const rl = el.querySelector('.reply-list');
                const open = !btn.classList.contains('open');
                btn.classList.toggle('open', open);
                rl.hidden = !open;
                if (open && !rl.dataset.loaded) {
                    rl.innerHTML = '<div class="skeleton sk-line" style="width:60%;margin:10px 0"></div>';
                    const replies = await api('/api/comments/' + id + '/replies');
                    rl.innerHTML = replies.map(r => commentHtml(r, true)).join('');
                    rl.dataset.loaded = '1';
                    icons(rl);
                }
            } else if (act === 'reply') {
                const rootEl = list.querySelector('.comment[data-id="' + btn.dataset.root + '"]');
                const slot = rootEl.querySelector('.reply-slot');
                if (slot.querySelector('form')) { slot.querySelector('textarea').focus(); return; }
                slot.innerHTML = '<form class="comment-form reply-form"><div class="grow">' +
                    '<textarea class="comment-input" rows="1" maxlength="2000" placeholder="Ваш ответ…"></textarea>' +
                    '<div class="comment-form-actions"><button class="btn btn-ghost btn-sm" type="button" data-cancel>Отмена</button>' +
                    '<button class="btn btn-primary btn-sm" type="submit">Ответить</button></div></div></form>';
                const f = slot.querySelector('form');
                const ta = f.querySelector('textarea');
                if (btn.dataset.root !== id) ta.value = '@' + btn.dataset.user + ' ';
                bindCommentForm(f, +btn.dataset.root, c => {
                    const rl = rootEl.querySelector('.reply-list');
                    rl.hidden = false;
                    rl.insertAdjacentHTML('beforeend', commentHtml(c, true));
                    icons(rl);
                    f.remove();
                });
                ta.focus();
                ta.setSelectionRange(ta.value.length, ta.value.length);
            } else if (act === 'delete') {
                if (!confirm('Удалить комментарий?')) return;
                await api('/api/comments/' + id, { method: 'DELETE' });
                el.style.transition = 'opacity .3s, transform .3s';
                el.style.opacity = '0';
                el.style.transform = 'translateX(-10px)';
                setTimeout(() => el.remove(), 300);
                const total = $('#commentTotal');
                total.textContent = Math.max(0, (+total.textContent || 0) - 1);
            } else if (act === 'pin' || act === 'heart') {
                await api('/api/comments/' + id + '/' + act, { method: 'POST' });
                list.innerHTML = '';
                page = 0;
                loadComments();
                toast('Готово');
            }
        } catch (err) { toast(err.message, 'error'); }
    });

    loadComments();
    showControls();
})();
