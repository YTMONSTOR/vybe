/* VYBE — auth helpers: show password, strength meter */
(function () {
    'use strict';
    document.querySelectorAll('[data-pw]').forEach(btn => {
        btn.addEventListener('click', () => {
            const input = document.getElementById(btn.dataset.pw);
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.innerHTML = '<i data-lucide="' + (show ? 'eye-off' : 'eye') + '"></i>';
            window.VYBE.icons(btn);
        });
    });
    const meter = document.getElementById('pwMeter');
    const pw = document.getElementById('password');
    if (meter && pw) {
        const colors = ['#f43f5e', '#f59e0b', '#8b5cf6', '#22c55e'];
        pw.addEventListener('input', () => {
            const v = pw.value;
            let score = 0;
            if (v.length >= 8) score++;
            if (/[A-ZА-Я]/.test(v) && /[a-zа-я]/.test(v)) score++;
            if (/\d/.test(v)) score++;
            if (/[^\w\s]/.test(v) || v.length >= 14) score++;
            Array.from(meter.children).forEach((s, i) => { s.style.background = i < score ? colors[score - 1] : ''; });
        });
    }
})();
