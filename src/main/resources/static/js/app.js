/* =====================================================================
   VYBE — core client: API, layout, search, cards, modals, mini player
   ===================================================================== */
(function () {
    'use strict';

    const $ = (s, r = document) => r.querySelector(s);
    const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
    const csrfToken = () => (document.querySelector('meta[name="_csrf"]') || {}).content || '';
    const csrfHeader = () => (document.querySelector('meta[name="_csrf_header"]') || {}).content || 'X-CSRF-TOKEN';

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    function icons(root) {
        if (window.lucide) window.lucide.createIcons({ nameAttr: 'data-lucide', attrs: {}, root: root || document });
    }

    /** JSON fetch with CSRF, auth redirect and readable errors. */
    async function api(url, opts = {}) {
        const headers = Object.assign({ 'Accept': 'application/json' }, opts.headers || {});
        const method = (opts.method || 'GET').toUpperCase();
        let body = opts.body;
        if (method !== 'GET') headers[csrfHeader()] = csrfToken();
        if (body && !(body instanceof FormData) && typeof body !== 'string') {
            headers['Content-Type'] = 'application/json';
            body = JSON.stringify(body);
        }
        const res = await fetch(url, { method, headers, body, credentials: 'same-origin' });
        if (res.status === 401) {
            location.href = '/login';
            throw new Error('Требуется вход');
        }
        if (res.status === 204) return null;
        let data = null;
        const text = await res.text();
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
        if (!res.ok) {
            const msg = (data && (data.message || data.error)) || ('Ошибка ' + res.status);
            throw new Error(msg);
        }
        return data;
    }

    function requireLogin() {
        if (!window.VYBE_ME) {
            location.href = '/login';
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ toasts
    function toast(message, type) {
        const box = $('#toasts');
        if (!box || !message) return;
        const el = document.createElement('div');
        el.className = 'toast' + (type === 'error' ? ' error' : '');
        el.innerHTML = '<i data-lucide="' + (type === 'error' ? 'circle-alert' : 'circle-check') + '"></i><span>' + esc(message) + '</span>';
        box.appendChild(el);
        icons(el);
        setTimeout(() => { el.classList.add('hide'); setTimeout(() => el.remove(), 320); }, 3200);
    }

    // ------------------------------------------------------------ layout
    function initLayout() {
        const body = document.body;
        const forcedDrawer = body.classList.contains('no-sidebar');
        const apply = () => {
            if (forcedDrawer) return;
            const w = window.innerWidth;
            body.classList.toggle('no-sidebar', w < 720);
            if (w >= 720) {
                let stored = null;
                try { stored = localStorage.getItem('vybe.sidebar'); } catch (e) { /* ignore */ }
                body.classList.toggle('sidebar-collapsed', stored === 'collapsed' || (stored === null && w < 1280));
            }
        };
        apply();
        window.addEventListener('resize', apply);
        const toggle = $('#menuToggle');
        if (toggle) toggle.addEventListener('click', () => {
            if (body.classList.contains('no-sidebar')) {
                body.classList.toggle('sidebar-open');
            } else {
                const collapsed = body.classList.toggle('sidebar-collapsed');
                try { localStorage.setItem('vybe.sidebar', collapsed ? 'collapsed' : 'open'); } catch (e) { /* ignore */ }
            }
        });
        const scrim = $('#scrim');
        if (scrim) scrim.addEventListener('click', () => body.classList.remove('sidebar-open'));

        const btn = $('#userMenuBtn'), menu = $('#userMenu');
        if (btn && menu) {
            btn.addEventListener('click', e => { e.stopPropagation(); menu.classList.toggle('open'); });
            document.addEventListener('click', e => { if (!menu.contains(e.target)) menu.classList.remove('open'); });
            document.addEventListener('keydown', e => { if (e.key === 'Escape') menu.classList.remove('open'); });
        }
        const flash = $('#flashToast');
        if (flash) toast(flash.dataset.message);
    }

    // ------------------------------------------------------------ search suggestions
    function initSearch() {
        const input = $('#searchInput'), box = $('#suggest');
        if (!input || !box) return;
        let timer = null, items = [], active = -1, lastQ = '';
        const typeIcon = { video: 'search', tag: 'hash', channel: 'user-round' };
        const typeName = { video: 'видео', tag: 'тег', channel: 'канал' };

        function render(q) {
            if (!items.length) { box.hidden = true; return; }
            const ql = q.toLowerCase();
            box.innerHTML = items.map((s, i) => {
                const t = esc(s.text);
                const idx = s.text.toLowerCase().indexOf(ql.replace(/^#/, ''));
                let html = t;
                if (idx >= 0 && ql) {
                    const len = ql.replace(/^#/, '').length;
                    html = esc(s.text.slice(0, idx)) + '<b>' + esc(s.text.slice(idx, idx + len)) + '</b>' + esc(s.text.slice(idx + len));
                }
                return '<div class="suggest-item' + (i === active ? ' active' : '') + '" data-i="' + i + '">' +
                    '<i data-lucide="' + (typeIcon[s.type] || 'search') + '"></i><span>' + html + '</span>' +
                    '<span class="suggest-type">' + (typeName[s.type] || '') + '</span></div>';
            }).join('');
            box.hidden = false;
            icons(box);
        }

        function go(text) {
            input.value = text;
            input.form.submit();
        }

        input.addEventListener('input', () => {
            clearTimeout(timer);
            const q = input.value.trim();
            if (!q) { items = []; box.hidden = true; return; }
            timer = setTimeout(async () => {
                if (q === lastQ) return;
                lastQ = q;
                try {
                    items = await api('/api/search/suggest?q=' + encodeURIComponent(q)) || [];
                    active = -1;
                    render(q);
                } catch (e) { box.hidden = true; }
            }, 160);
        });
        input.addEventListener('keydown', e => {
            if (box.hidden || !items.length) return;
            if (e.key === 'ArrowDown') { e.preventDefault(); active = (active + 1) % items.length; render(input.value.trim()); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); active = (active - 1 + items.length) % items.length; render(input.value.trim()); }
            else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); go(items[active].text); }
            else if (e.key === 'Escape') { box.hidden = true; }
        });
        box.addEventListener('mousedown', e => {
            const it = e.target.closest('.suggest-item');
            if (it) { e.preventDefault(); go(items[+it.dataset.i].text); }
        });
        input.addEventListener('blur', () => setTimeout(() => { box.hidden = true; }, 120));
        input.addEventListener('focus', () => { if (items.length && input.value.trim()) box.hidden = false; });
        document.addEventListener('keydown', e => {
            const tag = (e.target.tagName || '').toLowerCase();
            if (e.key === '/' && tag !== 'input' && tag !== 'textarea' && !e.target.isContentEditable) {
                e.preventDefault();
                input.focus();
            }
        });
    }

    // ------------------------------------------------------------ images & cards
    function initLazyImages(root) {
        $$('img.lazy-img', root || document).forEach(img => {
            if (img.dataset.bound) return;
            img.dataset.bound = '1';
            const wrap = img.parentElement;
            const done = () => { img.classList.add('loaded'); wrap && wrap.classList.remove('loading'); };
            if (img.complete && img.naturalWidth) { done(); return; }
            wrap && wrap.classList.add('lazy-wrap', 'loading');
            img.addEventListener('load', done, { once: true });
            img.addEventListener('error', () => {
                img.src = '/img/placeholder.svg';
                done();
            }, { once: true });
        });
    }

    const canHover = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

    function initCardPreviews(root) {
        if (!canHover) return;
        $$('.vcard[data-preview]', root || document).forEach(card => {
            if (card.dataset.previewBound) return;
            card.dataset.previewBound = '1';
            const thumb = $('.vcard-thumb', card);
            let timer = null, video = null;
            card.addEventListener('mouseenter', () => {
                timer = setTimeout(() => {
                    video = document.createElement('video');
                    video.className = 'preview';
                    video.muted = true;
                    video.playsInline = true;
                    video.loop = true;
                    video.preload = 'auto';
                    video.src = card.dataset.preview;
                    video.addEventListener('loadedmetadata', () => {
                        if (video.duration && isFinite(video.duration) && video.duration > 20) {
                            video.currentTime = Math.min(video.duration * 0.15, 60);
                        }
                    }, { once: true });
                    video.addEventListener('playing', () => { video.classList.add('playing'); card.classList.add('previewing'); });
                    thumb.insertBefore(video, thumb.firstChild.nextSibling);
                    video.play().catch(() => { /* autoplay may be blocked */ });
                }, 650);
            });
            card.addEventListener('mouseleave', () => {
                clearTimeout(timer);
                card.classList.remove('previewing');
                if (video) {
                    video.pause();
                    video.removeAttribute('src');
                    video.load();
                    video.remove();
                    video = null;
                }
            });
        });
    }

    function initShelves() {
        $$('.shelf-wrap').forEach(wrap => {
            const shelf = $('.shelf', wrap), prev = $('.shelf-nav.prev', wrap), next = $('.shelf-nav.next', wrap);
            if (!shelf || !prev || !next) return;
            const update = () => {
                prev.disabled = shelf.scrollLeft < 10;
                next.disabled = shelf.scrollLeft + shelf.clientWidth >= shelf.scrollWidth - 10;
            };
            prev.addEventListener('click', () => shelf.scrollBy({ left: -shelf.clientWidth * 0.9, behavior: 'smooth' }));
            next.addEventListener('click', () => shelf.scrollBy({ left: shelf.clientWidth * 0.9, behavior: 'smooth' }));
            shelf.addEventListener('scroll', update, { passive: true });
            window.addEventListener('resize', update);
            update();
        });
    }

    function initCardActions() {
        document.addEventListener('click', e => {
            const btn = e.target.closest('.vcard-action');
            if (!btn) return;
            e.preventDefault();
            e.stopPropagation();
            if (btn.dataset.action === 'save') openSaveModal(btn.dataset.id);
            if (btn.dataset.action === 'mini') {
                Mini.open({ id: btn.dataset.id, src: btn.dataset.src, title: btn.dataset.title, author: btn.dataset.author, time: 0, playing: true });
            }
        });
    }

    // ------------------------------------------------------------ Smart Feed infinite scroll
    function cardHtml(c) {
        const a = c.author;
        const avatar = '<span class="avatar avatar-sm" style="--h:' + a.hue + '">' +
            (a.avatarUrl ? '<img src="' + esc(a.avatarUrl) + '" alt="" loading="lazy">' : esc(a.initials)) + '</span>';
        const actions = window.VYBE_ME ? '<div class="vcard-actions">' +
            '<button class="vcard-action" type="button" data-action="save" data-id="' + c.id + '" title="Сохранить в плейлист"><i data-lucide="list-plus"></i></button>' +
            '<button class="vcard-action" type="button" data-action="mini" data-id="' + c.id + '" data-src="' + esc(c.videoUrl) + '" data-title="' + esc(c.title) + '" data-author="' + esc(a.displayName) + '" title="Мини-плеер"><i data-lucide="picture-in-picture-2"></i></button></div>' : '';
        return '<article class="vcard" data-preview="' + esc(c.videoUrl) + '" data-video-id="' + c.id + '">' +
            '<a class="vcard-thumb" href="/watch/' + c.id + '" aria-label="' + esc(c.title) + '">' +
            '<img class="lazy-img" src="' + esc(c.thumbnailUrl) + '" alt="" loading="lazy" decoding="async">' +
            (c.reason ? '<span class="vcard-reason"><i data-lucide="sparkles"></i>' + esc(c.reason) + '</span>' : '') +
            '<span class="vcard-play"><i data-lucide="play"></i></span>' +
            (c.duration ? '<span class="vcard-duration">' + esc(c.duration) + '</span>' : '') +
            '</a>' + actions +
            '<div class="vcard-body"><a href="/channel/' + esc(a.username) + '">' + avatar + '</a><div class="vcard-meta">' +
            '<a href="/watch/' + c.id + '" class="vcard-title">' + esc(c.title) + '</a>' +
            '<a class="vcard-author" href="/channel/' + esc(a.username) + '">' + esc(a.displayName) + '</a>' +
            '<div class="vcard-stats"><span>' + esc(c.views) + '</span><span class="sep">•</span><span>' + esc(c.ago) + '</span></div>' +
            '</div></div></article>';
    }

    function initFeed() {
        const grid = $('#smartFeed'), sentinel = $('#feedSentinel'), skeletons = $('#feedSkeletons');
        if (!grid || !sentinel || !('IntersectionObserver' in window)) return;
        let offset = parseInt(grid.dataset.offset || '0', 10), loading = false, done = offset < 12;
        if (done) return;
        const seen = new Set($$('.vcard', grid).map(c => c.dataset.videoId));
        const io = new IntersectionObserver(async entries => {
            if (!entries[0].isIntersecting || loading || done) return;
            loading = true;
            skeletons.hidden = false;
            try {
                const items = await api('/api/feed?offset=' + offset + '&limit=12');
                offset += items.length;
                if (items.length < 12) done = true;
                const fresh = items.filter(c => !seen.has(String(c.id)));
                fresh.forEach(c => seen.add(String(c.id)));
                const tmp = document.createElement('div');
                tmp.innerHTML = fresh.map(cardHtml).join('');
                const nodes = Array.from(tmp.children);
                nodes.forEach((n, i) => { n.style.animationDelay = (i * 45) + 'ms'; grid.appendChild(n); });
                icons(grid);
                initLazyImages(grid);
                initCardPreviews(grid);
            } catch (e) {
                done = true;
            } finally {
                loading = false;
                skeletons.hidden = true;
                if (done) io.disconnect();
            }
        }, { rootMargin: '600px' });
        io.observe(sentinel);
    }

    // ------------------------------------------------------------ modal helpers
    function modal(html) {
        const root = document.createElement('div');
        root.className = 'modal-root';
        root.innerHTML = '<div class="modal" role="dialog" aria-modal="true">' + html + '</div>';
        document.body.appendChild(root);
        icons(root);
        const close = () => { root.remove(); document.removeEventListener('keydown', onKey); };
        const onKey = e => { if (e.key === 'Escape') close(); };
        document.addEventListener('keydown', onKey);
        root.addEventListener('mousedown', e => { if (e.target === root) close(); });
        $$('[data-close]', root).forEach(b => b.addEventListener('click', close));
        return { root, close };
    }

    async function openSaveModal(videoId) {
        if (!requireLogin()) return;
        const m = modal(
            '<div class="modal-head"><h3>Сохранить в…</h3><button class="btn btn-ghost btn-icon btn-sm" data-close aria-label="Закрыть"><i data-lucide="x"></i></button></div>' +
            '<div class="modal-list" id="plList"><div class="skeleton sk-line" style="height:44px"></div><div class="skeleton sk-line" style="height:44px"></div></div>' +
            '<form id="plCreate" class="field" style="margin:0">' +
            '<label class="label">Новый плейлист или коллекция</label>' +
            '<div style="display:flex;gap:8px"><input class="input" name="title" maxlength="80" placeholder="Название" required>' +
            '<button class="btn btn-primary" type="submit"><i data-lucide="plus"></i></button></div>' +
            '<div style="display:flex;gap:18px;margin-top:10px;flex-wrap:wrap">' +
            '<label class="check"><input type="checkbox" name="collection">Это коллекция</label>' +
            '<label class="check"><input type="checkbox" name="publicVisible" checked>Публичный</label></div></form>');
        const list = $('#plList', m.root);

        function row(p) {
            const icon = p.collection ? (p.icon || 'layers') : 'list-video';
            return '<label class="pl-option"><span class="pl-ico" style="--c:' + esc(p.color || '#8b5cf6') + '"><i data-lucide="' + esc(icon) + '"></i></span>' +
                '<span>' + esc(p.title) + '<small>' + (p.collection ? 'Коллекция' : 'Плейлист') + ' • ' + p.size + ' видео</small></span>' +
                '<span class="check"><input type="checkbox" data-pl="' + p.id + '"' + (p.contains ? ' checked' : '') + '></span></label>';
        }

        async function load() {
            try {
                const pls = await api('/api/videos/' + videoId + '/playlists');
                list.innerHTML = pls.length ? pls.map(row).join('') : '<p class="dim" style="padding:10px 4px">У вас пока нет плейлистов — создайте первый ниже.</p>';
                icons(list);
            } catch (e) { list.innerHTML = '<p class="field-error">' + esc(e.message) + '</p>'; }
        }

        list.addEventListener('change', async e => {
            const cb = e.target.closest('input[data-pl]');
            if (!cb) return;
            try {
                const st = await api('/api/videos/' + videoId + '/playlists/' + cb.dataset.pl, { method: 'POST' });
                cb.checked = st.active;
                toast(st.active ? 'Добавлено' : 'Удалено');
            } catch (err) { cb.checked = !cb.checked; toast(err.message, 'error'); }
        });

        $('#plCreate', m.root).addEventListener('submit', async e => {
            e.preventDefault();
            const f = e.target;
            try {
                const p = await api('/api/playlists', {
                    method: 'POST',
                    body: { title: f.title.value, collection: f.collection.checked, publicVisible: f.publicVisible.checked }
                });
                await api('/api/videos/' + videoId + '/playlists/' + p.id, { method: 'POST' });
                f.reset();
                f.publicVisible.checked = true;
                toast('Создано и добавлено: ' + p.title);
                load();
            } catch (err) { toast(err.message, 'error'); }
        });
        load();
    }

    // ------------------------------------------------------------ Mini Player (persists across pages)
    const Mini = {
        key: 'vybe.mini',
        el: null,
        state: null,
        open(state) {
            this.state = state;
            this.save();
            this.render(true);
        },
        save() {
            try {
                if (this.state) sessionStorage.setItem(this.key, JSON.stringify(this.state));
                else sessionStorage.removeItem(this.key);
            } catch (e) { /* ignore */ }
        },
        restore() {
            let s = null;
            try { s = JSON.parse(sessionStorage.getItem(this.key) || 'null'); } catch (e) { s = null; }
            if (!s) return;
            const watchMatch = location.pathname.match(/^\/watch\/(\d+)/);
            if (watchMatch && watchMatch[1] === String(s.id)) {
                // the full player takes over on the same video
                this.state = null;
                this.save();
                return;
            }
            if (location.pathname.startsWith('/together/')) return;
            this.state = s;
            this.render(s.playing);
        },
        render(autoplay) {
            if (this.el) this.el.remove();
            const s = this.state;
            const el = document.createElement('div');
            el.className = 'mini-player';
            el.innerHTML = '<video playsinline preload="auto"></video>' +
                '<div class="mini-progress"><span></span></div>' +
                '<div class="mini-bar"><div class="mini-info"><b>' + esc(s.title) + '</b><small>' + esc(s.author || '') + '</small></div>' +
                '<button class="cbtn" data-m="play" aria-label="Пауза"><i data-lucide="pause"></i></button>' +
                '<button class="cbtn" data-m="expand" aria-label="Открыть"><i data-lucide="maximize-2"></i></button>' +
                '<button class="cbtn" data-m="close" aria-label="Закрыть"><i data-lucide="x"></i></button></div>';
            document.body.appendChild(el);
            this.el = el;
            icons(el);
            const v = $('video', el), bar = $('.mini-progress span', el), playBtn = $('[data-m="play"]', el);
            v.src = s.src;
            const setIcon = () => {
                playBtn.innerHTML = '<i data-lucide="' + (v.paused ? 'play' : 'pause') + '"></i>';
                icons(playBtn);
            };
            v.addEventListener('loadedmetadata', () => {
                if (s.time) v.currentTime = Math.min(s.time, (v.duration || s.time) - 0.5);
                if (autoplay) v.play().catch(() => { v.muted = true; v.play().catch(() => setIcon()); });
            }, { once: true });
            v.addEventListener('timeupdate', () => {
                s.time = v.currentTime;
                if (v.duration) bar.style.width = (v.currentTime / v.duration * 100) + '%';
            });
            v.addEventListener('play', () => { s.playing = true; setIcon(); this.save(); });
            v.addEventListener('pause', () => { s.playing = false; setIcon(); this.save(); });
            v.addEventListener('ended', () => { s.playing = false; s.time = 0; this.save(); setIcon(); });
            v.addEventListener('click', () => (v.paused ? v.play() : v.pause()));
            playBtn.addEventListener('click', () => (v.paused ? v.play() : v.pause()));
            $('[data-m="expand"]', el).addEventListener('click', () => {
                this.save();
                location.href = '/watch/' + s.id + '?t=' + Math.floor(v.currentTime || 0);
            });
            $('[data-m="close"]', el).addEventListener('click', () => this.close());
            this.timer = setInterval(() => this.save(), 2000);
            window.addEventListener('beforeunload', () => this.save());
        },
        close() {
            if (this.el) {
                const v = $('video', this.el);
                if (v) { v.pause(); v.removeAttribute('src'); v.load(); }
                this.el.remove();
            }
            clearInterval(this.timer);
            this.el = null;
            this.state = null;
            this.save();
        }
    };

    // ------------------------------------------------------------ helpers shared with pages
    function formatTime(sec) {
        sec = Math.max(0, Math.floor(sec || 0));
        const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
        const mm = h ? String(m).padStart(2, '0') : String(m);
        return (h ? h + ':' : '') + mm + ':' + String(s).padStart(2, '0');
    }

    function initConfirmForms() {
        document.addEventListener('submit', e => {
            const f = e.target;
            if (f.dataset && f.dataset.confirm && !confirm(f.dataset.confirm)) e.preventDefault();
        }, true);
    }

    window.VYBE = { api, toast, esc, icons, modal, openSaveModal, requireLogin, Mini, formatTime, initLazyImages, initCardPreviews };

    document.addEventListener('DOMContentLoaded', () => {
        icons();
        initLayout();
        initSearch();
        initLazyImages();
        initCardPreviews();
        initShelves();
        initCardActions();
        initFeed();
        initConfirmForms();
        Mini.restore();
    });
})();
