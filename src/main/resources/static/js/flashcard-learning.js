/* Client-only study modes. A mode is a real URL; this file only renders the
   mode selected by the server and keeps the session score in memory. */
(function () {
    'use strict';
    function ready(fn) { document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', fn) : fn(); }
    ready(function () {
        var host = document.getElementById('fcLearning');
        if (!host) return;
        var cards;
        try { cards = JSON.parse(host.dataset.cards || '[]'); } catch (e) { cards = []; }
        cards = cards.filter(function (c) { return c && c.front && c.back; });
        var mode = host.dataset.activeMode || 'learn';
        var focusUnknown = mode === 'learn' && new URLSearchParams(window.location.search).get('focus') === 'unknown';
        if (focusUnknown) {
            var reviewStorageKey = 'ksh:flashcards:' + host.dataset.deckId + ':unknown';
            try {
                var unknownIds = JSON.parse(window.sessionStorage.getItem(reviewStorageKey) || '[]').map(String);
                cards = cards.filter(function (card) { return unknownIds.indexOf(String(card.id)) >= 0; });
            } catch (e) {
                cards = [];
            }
        }
        var score = 0;
        var state = {
            learn: { items: shuffle(cards), index: 0, locked: false },
            test: { items: shuffle(cards), index: 0, locked: false },
            match: { done: 0, first: null, tiles: [] },
            blocks: { items: shuffle(cards), index: 0, locked: false },
            blast: { items: shuffle(cards), index: 0, running: false, timer: 30, interval: null, combo: 0, hits: 0 }
        };
        var scoreEl = document.getElementById('fcSessionScore');
        var progress = document.getElementById('fcProgressBar');
        var progressLabel = document.getElementById('fcProgressLabel');
        var headerMode = document.getElementById('fcHeaderMode');
        var labels = { learn: 'Học nhanh', test: 'Kiểm tra', match: 'Ghép cặp', blocks: 'Khối từ', blast: 'Bắn từ' };
        if (headerMode) headerMode.textContent = labels[mode] || 'Học';

        function shuffle(arr) { var a = arr.slice(); for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
        function norm(v) { return String(v || '').trim().toLocaleLowerCase().replace(/\s+/g, ' '); }
        function alternativeLabels(card) {
            if (!card || !card.alternativesJson) return [];
            try {
                var values = JSON.parse(card.alternativesJson);
                return Array.isArray(values) ? values.map(function (value) {
                    return String(value || '').trim();
                }).filter(Boolean) : [];
            } catch (e) {
                return String(card.alternativesJson).split(/[,;|]/).map(function (value) {
                    return String(value || '').trim();
                }).filter(Boolean);
            }
        }
        function alternatives(card) { return alternativeLabels(card).map(norm).filter(Boolean); }
        function acceptedAnswerText(card) {
            var alsoAccepted = alternativeLabels(card);
            return 'Đáp án: ' + card.front +
                (alsoAccepted.length ? ' · Cũng chấp nhận: ' + alsoAccepted.join(', ') : '');
        }
        function setMedia(id, url, alt) {
            var image = document.getElementById(id);
            if (!image) return;
            image.hidden = !url;
            if (url) { image.src = url; image.alt = alt || ''; }
            else image.removeAttribute('src');
        }
        function setScore(delta) { score = Math.max(0, score + delta); if (scoreEl) scoreEl.textContent = String(score); }
        function setProgress(done, total, label) { if (progress) progress.style.width = (total ? Math.min(100, Math.round(done / total * 100)) : 0) + '%'; if (progressLabel) progressLabel.textContent = label || ''; }
        function feedback(id, text, good) { var el = document.getElementById(id); if (!el) return; el.textContent = text || ''; el.classList.toggle('is-good', good === true); el.classList.toggle('is-bad', good === false); }
        function speak(text) { if (!text || !window.speechSynthesis) return; window.speechSynthesis.cancel(); var u = new SpeechSynthesisUtterance(text); u.lang = /[\uac00-\ud7af]/.test(text) ? 'ko-KR' : 'vi-VN'; window.speechSynthesis.speak(u); }

        if (!cards.length) {
            document.getElementById('fcLearnCount').textContent = '0 / 0';
            document.getElementById('fcLearnPrompt').textContent = focusUnknown ? 'Bạn đã biết tất cả các từ' : '—';
            feedback('fcLearnFeedback', focusUnknown ? 'Không còn từ chưa biết trong lượt ôn này.' : 'Bộ thẻ chưa có nội dung để luyện tập.', focusUnknown);
            setProgress(0, 0, focusUnknown ? 'Đã hoàn thành toàn bộ thẻ' : 'Chưa có thẻ để học');
            return;
        }

        function renderLearn() {
            var s = state.learn, card = s.items[s.index], answers = document.getElementById('fcLearnAnswers');
            s.locked = false;
            document.getElementById('fcLearnPrompt').textContent = card.front;
            setMedia('fcLearnImage', card.frontImage, card.front);
            document.getElementById('fcLearnCount').textContent = (s.index + 1) + ' / ' + s.items.length;
            answers.textContent = '';
            var choices = shuffle([card].concat(shuffle(cards.filter(function (c) { return c.id !== card.id; })).slice(0, 3)));
            choices.forEach(function (choice) {
                var b = document.createElement('button'); b.type = 'button'; b.className = 'fc-answer-button'; b.textContent = choice.back;
                b.addEventListener('click', function () {
                    if (s.locked) return; s.locked = true;
                    var ok = choice.id === card.id; b.classList.add(ok ? 'is-correct' : 'is-wrong');
                    if (!ok) Array.prototype.forEach.call(answers.children, function (x) { if (norm(x.textContent) === norm(card.back)) x.classList.add('is-correct'); });
                    setScore(ok ? 10 : 0); feedback('fcLearnFeedback', ok ? 'Đúng rồi.' : 'Đáp án: ' + card.back, ok);
                    window.setTimeout(function () { s.index = (s.index + 1) % s.items.length; renderLearn(); }, 720);
                });
                answers.appendChild(b);
            });
            setProgress(s.index, s.items.length, 'Đã luyện ' + s.index + ' / ' + s.items.length + ' thẻ');
        }

        function renderTest() {
            var s = state.test, card = s.items[s.index]; s.locked = false;
            document.getElementById('fcTestPrompt').textContent = card.back;
            setMedia('fcTestImage', card.backImage, card.back);
            document.getElementById('fcTestCount').textContent = (s.index + 1) + ' / ' + s.items.length;
            var input = document.getElementById('fcTestInput'); input.value = ''; input.disabled = false;
            feedback('fcTestFeedback', '');
            setProgress(s.index, s.items.length, 'Đã kiểm tra ' + s.index + ' / ' + s.items.length + ' thẻ');
        }
        function checkTest(showAnswer) {
            var s = state.test; if (s.locked) return; var card = s.items[s.index], input = document.getElementById('fcTestInput');
            s.locked = true; input.disabled = true;
            var answer = norm(input.value);
            var ok = answer === norm(card.front) || alternatives(card).indexOf(answer) >= 0;
            setScore(ok ? 15 : 0);
            feedback('fcTestFeedback',
                showAnswer ? acceptedAnswerText(card) : (ok ? 'Chính xác.' : acceptedAnswerText(card)),
                ok);
            window.setTimeout(function () { s.index = (s.index + 1) % s.items.length; renderTest(); }, 850);
        }

        function renderMatch() {
            var board = document.getElementById('fcMatchBoard'), pool = shuffle(cards).slice(0, Math.min(8, cards.length));
            var s = state.match; s.done = 0; s.first = null; s.tiles = []; board.textContent = '';
            pool.forEach(function (c) { s.tiles.push({ key: String(c.id), text: c.front, kind: 'front' }, { key: String(c.id), text: c.back, kind: 'back' }); });
            shuffle(s.tiles).forEach(function (tile, index) {
                var b = document.createElement('button'); b.type = 'button'; b.className = 'fc-match-tile'; b.textContent = tile.text;
                b.dataset.key = tile.key; b.dataset.kind = tile.kind;
                b.addEventListener('click', function () {
                    if (b.classList.contains('is-matched') || b.classList.contains('is-selected')) return;
                    b.classList.add('is-selected');
                    if (!s.first) { s.first = b; return; }
                    var first = s.first; s.first = null; var ok = first.dataset.key === b.dataset.key && first.dataset.kind !== b.dataset.kind;
                    if (ok) { first.classList.add('is-matched'); b.classList.add('is-matched'); s.done++; setScore(12); feedback('fcMatchFeedback', 'Đúng cặp.', true); }
                    else { first.classList.add('is-error'); b.classList.add('is-error'); feedback('fcMatchFeedback', 'Chưa khớp, thử lại.', false); window.setTimeout(function () { first.classList.remove('is-selected','is-error'); b.classList.remove('is-selected','is-error'); }, 450); }
                    document.getElementById('fcMatchCount').textContent = s.done + ' / ' + pool.length + ' cặp'; setProgress(s.done, pool.length, 'Đã ghép ' + s.done + ' / ' + pool.length + ' cặp');
                });
                board.appendChild(b);
            });
            document.getElementById('fcMatchCount').textContent = '0 / ' + pool.length + ' cặp'; setProgress(0, pool.length, 'Ghép đúng các cặp');
        }

        function renderBlocks() {
            var s = state.blocks, card = s.items[s.index], board = document.getElementById('fcBlocksBoard');
            s.locked = false; document.getElementById('fcBlocksPrompt').textContent = card.back; board.textContent = '';
            var choices = shuffle([card].concat(shuffle(cards.filter(function (c) { return c.id !== card.id; })).slice(0, 3)));
            choices.forEach(function (choice) { var b = document.createElement('button'); b.type = 'button'; b.className = 'fc-block'; b.textContent = choice.front; b.addEventListener('click', function () {
                if (s.locked) return; s.locked = true; var ok = choice.id === card.id; b.classList.add(ok ? 'is-good' : 'is-bad'); setScore(ok ? 8 : 0); feedback('fcBlocksFeedback', ok ? 'Khối đã vào đúng vị trí.' : 'Khối đúng là: ' + card.front, ok); window.setTimeout(function () { s.index = (s.index + 1) % s.items.length; renderBlocks(); }, 700);
            }); board.appendChild(b); });
            document.getElementById('fcBlocksCount').textContent = score + ' điểm'; setProgress(s.index, s.items.length, 'Đã xếp ' + s.index + ' / ' + s.items.length + ' khối');
        }

        function blastTargets() {
            var s = state.blast, card = s.items[s.index % s.items.length], hostTargets = document.getElementById('fcBlastTargets');
            document.getElementById('fcBlastPrompt').textContent = card.back; hostTargets.textContent = '';
            var choices = shuffle([card].concat(shuffle(cards.filter(function (c) { return c.id !== card.id; })).slice(0, 3)));
            choices.forEach(function (choice, i) {
                var b = document.createElement('button'); b.type = 'button'; b.className = 'fc-blast-target'; b.textContent = choice.front;
                b.style.setProperty('--x', (12 + i * 24 + Math.random() * 8) + '%'); b.style.setProperty('--delay', (Math.random() * 1.5) + 's'); b.style.setProperty('--drift', (Math.random() * 80 - 40) + 'px');
                b.addEventListener('click', function () { if (!s.running) return; var ok = choice.id === card.id; b.classList.add(ok ? 'is-correct' : 'is-wrong'); if (ok) { s.hits++; s.combo++; setScore(20); feedback('fcBlastFeedback', 'Bắn trúng! +' + 20 + ' điểm', true); setProgress(s.hits, s.items.length, 'Đã bắn trúng ' + s.hits + ' từ'); window.setTimeout(function () { s.index++; blastTargets(); }, 330); } else { s.combo = 0; feedback('fcBlastFeedback', 'Trượt rồi.', false); window.setTimeout(function () { b.classList.remove('is-wrong'); }, 340); } });
                hostTargets.appendChild(b);
            });
            var dots = document.getElementById('fcBlastComboDots'); if (dots) { dots.textContent = ''; for (var d = 0; d < 5; d++) { var dot = document.createElement('i'); dot.className = d < Math.min(s.combo, 5) ? 'is-lit' : ''; dots.appendChild(dot); } }
            var level = document.getElementById('fcBlastLevelBar'); if (level) level.style.width = Math.min(100, s.hits / s.items.length * 100) + '%';
        }
        function finishBlast() { var s = state.blast; s.running = false; window.clearInterval(s.interval); document.getElementById('fcBlastOverlay').classList.add('is-visible'); document.getElementById('fcBlastOverlayTitle').textContent = 'Hết giờ'; document.getElementById('fcBlastOverlayText').textContent = 'Bạn bắn trúng ' + s.hits + ' từ · ' + score + ' điểm'; document.getElementById('fcBlastStart').textContent = 'Chơi lại'; }
        function startBlast() { var s = state.blast; s.items = shuffle(cards); s.index = 0; s.timer = 30; s.hits = 0; s.combo = 0; s.running = true; document.getElementById('fcBlastOverlay').classList.remove('is-visible'); document.getElementById('fcBlastTimer').textContent = '30s'; blastTargets(); s.interval = window.setInterval(function () { s.timer--; document.getElementById('fcBlastTimer').textContent = s.timer + 's'; if (s.timer <= 0) finishBlast(); }, 1000); }

        if (mode === 'learn') renderLearn();
        if (mode === 'test') renderTest();
        if (mode === 'match') { renderMatch(); document.getElementById('fcMatchRestart').addEventListener('click', renderMatch); }
        if (mode === 'blocks') renderBlocks();
        if (mode === 'blast') { document.getElementById('fcBlastOverlay').classList.add('is-visible'); document.getElementById('fcBlastStart').addEventListener('click', startBlast); }
        var audio = document.getElementById('fcGlobalAudio'); if (audio) audio.addEventListener('click', function () { var prompt = document.querySelector('[id$="Prompt"], #fcBlastPrompt'); if (prompt) speak(prompt.textContent); });
        var speakButton = document.querySelector('[data-speak-target]'); if (speakButton) speakButton.addEventListener('click', function () { speak(document.getElementById(speakButton.dataset.speakTarget).textContent); });
        var testCheck = document.getElementById('fcTestCheck'); if (testCheck) testCheck.addEventListener('click', function () { checkTest(false); });
        var testHint = document.getElementById('fcTestHint'); if (testHint) testHint.addEventListener('click', function () { checkTest(true); });
        var testInput = document.getElementById('fcTestInput'); if (testInput) testInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') checkTest(false); });
    });
})();
