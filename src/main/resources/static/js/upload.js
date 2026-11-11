/* VYBE — upload flow: drag & drop, frame picker, progress */
(function () {
    'use strict';
    const { toast, esc, icons } = window.VYBE;
    const $ = s => document.querySelector(s);
    const MAX = 2 * 1024 * 1024 * 1024;
    const VIDEO_EXT = ['mp4', 'webm', 'mov', 'm4v', 'ogv', 'ogg', 'mkv'];

    const drop = $('#dropzone'), fileInput = $('#fileInput'), form = $('#uploadForm');
    const preview = $('#previewVideo'), picker = $('#framePicker'), thumbImg = $('#thumbImg');
    let file = null, duration = 0, frames = [], chosenFrame = null, userPickedFrame = false, customThumb = null, uploading = false;

    function fmtSize(b) {
        if (b > 1073741824) return (b / 1073741824).toFixed(2) + ' ГБ';
        if (b > 1048576) return (b / 1048576).toFixed(1) + ' МБ';
        return Math.round(b / 1024) + ' КБ';
    }

    ['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
    ['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
    drop.addEventListener('drop', e => { if (e.dataTransfer.files.length) pick(e.dataTransfer.files[0]); });
    fileInput.addEventListener('change', () => { if (fileInput.files.length) pick(fileInput.files[0]); });

    function pick(f) {
        const ext = (f.name.split('.').pop() || '').toLowerCase();
        if (!(f.type.startsWith('video/') || VIDEO_EXT.includes(ext))) { toast('Выберите видеофайл', 'error'); return; }
        if (f.size > MAX) { toast('Файл больше 2 ГБ', 'error'); return; }
        file = f;
        drop.hidden = true;
        form.hidden = false;
        $('#fileName').textContent = f.name;
        $('#fileSize').textContent = fmtSize(f.size);
        const title = $('#title');
        if (!title.value) {
            title.value = f.name.replace(/\.[^.]+$/, '').replace(/[_]+/g, ' ').slice(0, 120);
            $('#titleCount').textContent = title.value.length;
        }
        preview.src = URL.createObjectURL(f);
        preview.addEventListener('loadedmetadata', () => {
            duration = isFinite(preview.duration) ? Math.round(preview.duration) : 0;
            extractFrames(preview.src);
        }, { once: true });
        preview.addEventListener('error', () => {
            picker.innerHTML = '<span class="hint">Браузер не может показать этот формат — обложку сгенерирует сервер.</span>';
        }, { once: true });
    }

    $('#title').addEventListener('input', e => { $('#titleCount').textContent = e.target.value.length; });

    async function extractFrames(src) {
        picker.innerHTML = '';
        for (let i = 0; i < 4; i++) picker.insertAdjacentHTML('beforeend', '<button type="button" class="skeleton" disabled></button>');
        const v = document.createElement('video');
        v.muted = true;
        v.playsInline = true;
        v.preload = 'auto';
        v.src = src;
        await new Promise(res => { v.addEventListener('loadeddata', res, { once: true }); v.addEventListener('error', res, { once: true }); });
        const points = [0.1, 0.3, 0.55, 0.8];
        frames = [];
        for (const p of points) {
            const blob = await grab(v, Math.max(0.1, (v.duration || 1) * p));
            if (blob) frames.push(blob);
        }
        picker.innerHTML = '';
        frames.forEach((b, i) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.innerHTML = '<img alt="Кадр ' + (i + 1) + '" src="' + URL.createObjectURL(b) + '">';
            btn.addEventListener('click', () => { userPickedFrame = true; choose(i); });
            picker.appendChild(btn);
        });
        if (frames.length) choose(0);
        else picker.innerHTML = '<span class="hint">Не удалось получить кадры — обложку сгенерирует сервер.</span>';
    }

    function grab(v, t) {
        return new Promise(resolve => {
            const done = () => {
                try {
                    const c = document.createElement('canvas');
                    const w = 1280, h = Math.round(1280 * (v.videoHeight || 720) / (v.videoWidth || 1280));
                    c.width = w; c.height = h;
                    c.getContext('2d').drawImage(v, 0, 0, w, h);
                    c.toBlob(b => resolve(b), 'image/jpeg', 0.86);
                } catch (e) { resolve(null); }
            };
            const timeout = setTimeout(() => resolve(null), 6000);
            v.addEventListener('seeked', () => { clearTimeout(timeout); done(); }, { once: true });
            v.currentTime = t;
        });
    }

    function choose(i) {
        chosenFrame = frames[i];
        customThumb = null;
        Array.from(picker.children).forEach((b, j) => b.classList.toggle('active', j === i));
        thumbImg.src = URL.createObjectURL(chosenFrame);
        thumbImg.hidden = false;
    }

    $('#thumbInput').addEventListener('change', e => {
        const f = e.target.files[0];
        if (!f) return;
        if (!/^image\/(png|jpe?g|webp)$/.test(f.type)) { toast('Обложка: PNG, JPG или WebP', 'error'); return; }
        customThumb = f;
        Array.from(picker.children).forEach(b => b.classList.remove('active'));
        thumbImg.src = URL.createObjectURL(f);
        thumbImg.hidden = false;
    });

    $('#cancelBtn').addEventListener('click', () => { if (!uploading || confirm('Отменить загрузку?')) location.reload(); });

    form.addEventListener('submit', e => {
        e.preventDefault();
        if (uploading) return;
        const title = $('#title').value.trim();
        if (!title) { toast('Укажите название', 'error'); $('#title').focus(); return; }
        const fd = new FormData();
        fd.append('title', title);
        fd.append('description', $('#description').value);
        fd.append('categoryId', $('#categoryId').value);
        fd.append('tags', $('#tags').value);
        fd.append('visibility', form.querySelector('input[name=visibility]:checked').value);
        fd.append('commentsEnabled', $('#commentsEnabled').checked ? 'true' : 'false');
        fd.append('duration', String(duration));
        fd.append('file', file, file.name);
        if (customThumb) fd.append('thumbnail', customThumb, customThumb.name);
        else if (chosenFrame && userPickedFrame) fd.append('thumbnail', chosenFrame, 'frame.jpg');
        else if (chosenFrame) fd.append('autoThumbnail', chosenFrame, 'frame.jpg');

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/videos');
        const token = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
        const header = (document.querySelector('meta[name="_csrf_header"]') || {}).content || 'X-CSRF-TOKEN';
        xhr.setRequestHeader(header, token);
        xhr.setRequestHeader('Accept', 'application/json');
        const bar = $('#progressBar'), pct = $('#progressPct'), txt = $('#progressText'), btn = $('#publishBtn');
        uploading = true;
        btn.disabled = true;
        btn.innerHTML = '<i data-lucide="loader-circle"></i>Загрузка…';
        icons(btn);
        const started = Date.now();
        xhr.upload.addEventListener('progress', ev => {
            if (!ev.lengthComputable) return;
            const p = ev.loaded / ev.total;
            bar.style.width = (p * 100).toFixed(1) + '%';
            pct.textContent = Math.round(p * 100) + '%';
            const speed = ev.loaded / Math.max(1, (Date.now() - started) / 1000);
            const left = (ev.total - ev.loaded) / Math.max(1, speed);
            txt.textContent = p < 1 ? 'Загрузка… осталось ~' + Math.ceil(left) + ' с' : 'Обработка: обложка, главы, метаданные…';
        });
        xhr.addEventListener('load', () => {
            uploading = false;
            let data = null;
            try { data = JSON.parse(xhr.responseText); } catch (err) { data = null; }
            if (xhr.status === 401) { location.href = '/login'; return; }
            if (xhr.status >= 200 && xhr.status < 300 && data) {
                form.hidden = true;
                $('#successCard').hidden = false;
                $('#watchLink').href = data.url;
                $('#editLink').href = data.editUrl;
                return;
            }
            const msg = (data && (data.message || data.error)) || (xhr.status === 413 ? 'Файл слишком большой' : 'Ошибка загрузки (' + xhr.status + ')');
            toast(msg, 'error');
            txt.textContent = msg;
            btn.disabled = false;
            btn.innerHTML = '<i data-lucide="rocket"></i>Опубликовать';
            icons(btn);
        });
        xhr.addEventListener('error', () => {
            uploading = false;
            toast('Сеть недоступна', 'error');
            btn.disabled = false;
            btn.innerHTML = '<i data-lucide="rocket"></i>Опубликовать';
            icons(btn);
        });
        xhr.send(fd);
    });

    window.addEventListener('beforeunload', e => { if (uploading) { e.preventDefault(); e.returnValue = ''; } });
})();
