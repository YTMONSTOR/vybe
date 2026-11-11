/* VYBE — lightweight SVG charts (area / bars) with hover tooltips */
(function () {
    'use strict';
    const { api, esc } = window.VYBE;
    const NS = 'http://www.w3.org/2000/svg';
    let uid = 0;

    function el(name, attrs, parent) {
        const n = document.createElementNS(NS, name);
        for (const k in attrs) n.setAttribute(k, attrs[k]);
        if (parent) parent.appendChild(n);
        return n;
    }

    function niceMax(v) {
        if (v <= 5) return 5;
        const p = Math.pow(10, Math.floor(Math.log10(v)));
        const n = v / p;
        return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
    }

    function fmt(n) {
        if (n >= 1e6) return (n / 1e6).toFixed(1).replace('.0', '') + 'M';
        if (n >= 1e3) return (n / 1e3).toFixed(1).replace('.0', '') + 'K';
        return String(n);
    }

    function render(box, data) {
        box.innerHTML = '';
        const compact = box.dataset.compact === '1';
        const bars = box.dataset.bars === '1';
        const c1 = box.dataset.color || '#8b5cf6', c2 = box.dataset.color2 || '#3b6bff';
        const unit = box.dataset.unit || '';
        const W = box.clientWidth || 600, H = box.clientHeight || 260;
        const padL = compact ? 4 : 40, padR = 8, padT = 12, padB = compact ? 6 : 26;
        const iw = W - padL - padR, ih = H - padT - padB;
        const values = data.values, labels = data.labels, n = values.length;
        const total = values.reduce((a, b) => a + b, 0);
        const max = niceMax(Math.max(1, ...values));
        const id = 'g' + (++uid);

        const svg = el('svg', { viewBox: '0 0 ' + W + ' ' + H, preserveAspectRatio: 'none' }, box);
        const defs = el('defs', {}, svg);
        const grad = el('linearGradient', { id: id + 'a', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
        el('stop', { offset: '0%', 'stop-color': c1, 'stop-opacity': '.45' }, grad);
        el('stop', { offset: '100%', 'stop-color': c2, 'stop-opacity': '0' }, grad);
        const lg = el('linearGradient', { id: id + 'l', x1: 0, y1: 0, x2: 1, y2: 0 }, defs);
        el('stop', { offset: '0%', 'stop-color': c1 }, lg);
        el('stop', { offset: '100%', 'stop-color': c2 }, lg);

        if (!compact) {
            for (let i = 0; i <= 4; i++) {
                const y = padT + ih - (ih * i / 4);
                el('line', { x1: padL, x2: W - padR, y1: y, y2: y, class: 'grid-line' }, svg);
                const t = el('text', { x: padL - 8, y: y + 4, 'text-anchor': 'end', class: 'axis-label' }, svg);
                t.textContent = fmt(Math.round(max * i / 4));
            }
            const step = Math.max(1, Math.ceil(n / 7));
            for (let i = 0; i < n; i += step) {
                const x = padL + (n === 1 ? iw / 2 : iw * i / (n - 1));
                const t = el('text', { x, y: H - 6, 'text-anchor': 'middle', class: 'axis-label' }, svg);
                t.textContent = labels[i];
            }
        }

        const xAt = i => padL + (n === 1 ? iw / 2 : iw * i / (n - 1));
        const yAt = v => padT + ih - (v / max) * ih;

        if (bars) {
            const bw = Math.max(2, iw / n * 0.62);
            values.forEach((v, i) => {
                const x = padL + iw * (i + 0.5) / n - bw / 2;
                const h = Math.max(v > 0 ? 3 : 0, (v / max) * ih);
                el('rect', { x, y: padT + ih - h, width: bw, height: h, rx: Math.min(4, bw / 2), fill: 'url(#' + id + 'l)', opacity: '.9' }, svg);
            });
        } else {
            const pts = values.map((v, i) => [xAt(i), yAt(v)]);
            let d = 'M' + pts[0][0] + ',' + pts[0][1];
            for (let i = 1; i < pts.length; i++) {
                const [x0, y0] = pts[i - 1], [x1, y1] = pts[i];
                const cx = (x0 + x1) / 2;
                d += ' C' + cx + ',' + y0 + ' ' + cx + ',' + y1 + ' ' + x1 + ',' + y1;
            }
            el('path', { d: d + ' L' + pts[pts.length - 1][0] + ',' + (padT + ih) + ' L' + pts[0][0] + ',' + (padT + ih) + ' Z', fill: 'url(#' + id + 'a)', class: 'area' }, svg);
            el('path', { d, stroke: 'url(#' + id + 'l)', class: 'line chart-draw' }, svg);
        }

        const cursor = el('line', { y1: padT, y2: padT + ih, stroke: 'rgba(255,255,255,.18)', 'stroke-dasharray': '4 4', opacity: 0 }, svg);
        const dot = el('circle', { r: 5, class: 'dot', stroke: c1 }, svg);
        const tip = document.createElement('div');
        tip.className = 'chart-tip';
        box.appendChild(tip);

        svg.addEventListener('mousemove', e => {
            const rect = svg.getBoundingClientRect();
            const x = (e.clientX - rect.left) * (W / rect.width);
            let i = bars ? Math.floor((x - padL) / iw * n) : Math.round((x - padL) / iw * (n - 1));
            i = Math.max(0, Math.min(n - 1, i));
            const px = bars ? padL + iw * (i + 0.5) / n : xAt(i);
            const py = yAt(values[i]);
            cursor.setAttribute('x1', px); cursor.setAttribute('x2', px); cursor.setAttribute('opacity', 1);
            dot.setAttribute('cx', px); dot.setAttribute('cy', py); dot.style.opacity = bars ? 0 : 1;
            tip.innerHTML = '<b>' + esc(values[i]) + '</b>' + esc(unit) + ' · ' + esc(labels[i]);
            tip.style.left = (px / W * rect.width) + 'px';
            tip.style.top = (py / H * rect.height) + 'px';
            tip.style.opacity = 1;
        });
        svg.addEventListener('mouseleave', () => { cursor.setAttribute('opacity', 0); dot.style.opacity = 0; tip.style.opacity = 0; });

        box.dataset.total = total;
        const head = box.closest('.chart-card');
        if (head && !compact) {
            let big = head.querySelector('.big');
            if (!big) {
                big = document.createElement('div');
                big.className = 'big';
                const h3 = head.querySelector('.chart-head > div, .chart-head > h3');
                if (h3) h3.insertAdjacentElement('afterend', big);
            }
            big.textContent = total.toLocaleString('ru-RU');
        }
    }

    async function load(box) {
        box.innerHTML = '<div class="skeleton" style="height:100%;border-radius:14px"></div>';
        try {
            const data = await api(box.dataset.src + '?days=' + (box.dataset.days || 30));
            box._data = data;
            render(box, data);
        } catch (e) {
            box.innerHTML = '<p class="field-error">' + esc(e.message) + '</p>';
        }
    }

    document.querySelectorAll('.chart[data-src]').forEach(load);
    document.querySelectorAll('[data-chart-range]').forEach(group => {
        group.addEventListener('click', e => {
            const b = e.target.closest('button[data-days]');
            if (!b) return;
            group.querySelectorAll('button').forEach(x => x.classList.toggle('active', x === b));
            const box = document.getElementById(group.dataset.chartRange);
            box.dataset.days = b.dataset.days;
            load(box);
        });
    });
    let rt = null;
    window.addEventListener('resize', () => {
        clearTimeout(rt);
        rt = setTimeout(() => document.querySelectorAll('.chart[data-src]').forEach(b => { if (b._data) render(b, b._data); }), 150);
    });
})();
