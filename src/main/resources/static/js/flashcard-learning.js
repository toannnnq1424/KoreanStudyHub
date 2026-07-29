/* Browser-only learning room. No attempt, effort or score is persisted. */
(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
        else fn();
    }

    ready(function () {
        var host = document.getElementById('fcLearning');
        if (!host) return;
        var cards;
        try { cards = JSON.parse(host.dataset.cards || '[]'); } catch (e) { cards = []; }
        cards = cards.filter(function (card) { return card && card.front && card.back; });

        var panels = Array.prototype.slice.call(host.querySelectorAll('[data-panel]'));
        var tabs = Array.prototype.slice.call(host.querySelectorAll('.fc-mode-tab'));
        var scoreEl = document.getElementById('fcSessionScore');
        var progressBar = document.getElementById('fcProgressBar');
        var progressLabel = document.getElementById('fcProgressLabel');
        var modeLabel = document.getElementById('fcModeLabel');
        var score = 0;
        var modeNames = {
            hub: 'Chọn một cách học để bắt đầu',
            learn: 'Học nhanh · trắc nghiệm',
            flash: 'Lật thẻ · ôn nhẹ',
            test: 'Kiểm tra · tự gõ',
            match: 'Ghép cặp · tốc độ',
            blast: 'Bắn từ · phản xạ'
        };

        function shuffle(items) {
            var copy = items.slice();
            for (var i = copy.length - 1; i > 0; i -= 1) {
                var j = Math.floor(Math.random() * (i + 1));
                var tmp = copy[i]; copy[i] = copy[j]; copy[j] = tmp;
            }
            return copy;
        }
        function normalize(value) {
            return String(value || '').trim().toLocaleLowerCase().replace(/\s+/g, ' ');
        }
        function setScore(delta) {
            score = Math.max(0, score + delta);
            if (scoreEl) scoreEl.textContent = score + ' điểm';
        }
        function setProgress(done, total, label) {
            var percent = total ? Math.min(100, Math.round(done / total * 100)) : 0;
            if (progressBar) progressBar.style.width = percent + '%';
            if (progressLabel) progressLabel.textContent = label || (done + ' / ' + total);
        }
        function setFeedback(el, text, good) {
            if (!el) return;
            el.textContent = text || '';
            el.classList.toggle('is-good', !!good);
            el.classList.toggle('is-bad', good === false);
        }
        function speak(text) {
            if (!text || !window.speechSynthesis) return;
            window.speechSynthesis.cancel();
            var utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = /[\uac00-\ud7af]/.test(text) ? 'ko-KR' : 'vi-VN';
            window.speechSynthesis.speak(utterance);
        }

        if (!cards.length) {
            panels.forEach(function (panel) { panel.classList.remove('is-active'); });
            var empty = document.createElement('div');
            empty.className = 'fc-empty';
            empty.textContent = 'Bộ thẻ chưa có nội dung để luyện tập.';
            host.appendChild(empty);
            return;
        }

        var state = {
            mode: 'hub',
            learnCards: shuffle(cards),
            learnIndex: 0,
            learnLocked: false,
            flashCards: shuffle(cards),
            flashIndex: 0,
            testCards: shuffle(cards),
            testIndex: 0,
            testLocked: false,
            matchTiles: [],
            matchFirst: null,
            matchDone: 0,
            blastCards: shuffle(cards),
            blastIndex: 0,
            blastRunning: false,
            blastTimer: 30,
            blastInterval: null
        };

        function showMode(mode) {
            state.mode = mode;
            tabs.forEach(function (tab) { tab.classList.toggle('is-active', tab.dataset.mode === mode); });
            panels.forEach(function (panel) { panel.classList.toggle('is-active', panel.dataset.panel === mode); });
            if (modeLabel) modeLabel.textContent = modeNames[mode] + ' · không lưu lượt chơi';
            if (mode === 'learn') renderLearn();
            if (mode === 'flash') renderFlash();
            if (mode === 'test') renderTest();
            if (mode === 'match') renderMatch();
            if (mode === 'blast') renderBlast();
            if (mode === 'hub') setProgress(0, 0, modeNames.hub);
        }

        function renderLearn() {
            var card = state.learnCards[state.learnIndex];
            var prompt = document.getElementById('fcLearnPrompt');
            var count = document.getElementById('fcLearnCount');
            var answers = document.getElementById('fcLearnAnswers');
            var feedback = document.getElementById('fcLearnFeedback');
            state.learnLocked = false;
            prompt.textContent = card.front;
            count.textContent = (state.learnIndex + 1) + ' / ' + state.learnCards.length;
            setFeedback(feedback, '');
            setProgress(state.learnIndex, state.learnCards.length, 'Đã luyện ' + state.learnIndex + ' / ' + state.learnCards.length);
            answers.textContent = '';
            var choices = shuffle([card].concat(shuffle(cards.filter(function (item) {
                return item.id !== card.id && normalize(item.back) !== normalize(card.back);
            })).slice(0, 3)));
            choices.forEach(function (choice) {
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'fc-answer-button';
                button.textContent = choice.back;
                button.addEventListener('click', function () {
                    if (state.learnLocked) return;
                    state.learnLocked = true;
                    var correct = normalize(choice.back) === normalize(card.back);
                    button.classList.add(correct ? 'is-correct' : 'is-wrong');
                    if (!correct) {
                        Array.prototype.forEach.call(answers.children, function (item) {
                            if (normalize(item.textContent) === normalize(card.back)) item.classList.add('is-correct');
                        });
                    }
                    setScore(correct ? 10 : 0);
                    setFeedback(feedback, correct ? 'Chính xác — từ này đã vào trí nhớ tốt hơn.' : 'Chưa đúng. Đáp án: ' + card.back, correct);
                    window.setTimeout(function () {
                        state.learnIndex = (state.learnIndex + 1) % state.learnCards.length;
                        renderLearn();
                    }, 850);
                });
                answers.appendChild(button);
            });
        }

        function renderFlash() {
            var card = state.flashCards[state.flashIndex];
            document.getElementById('fcFlashFront').textContent = card.front;
            document.getElementById('fcFlashBack').textContent = card.back;
            document.getElementById('fcFlashSource').textContent = 'Tự đánh dấu trong buổi luyện này';
            document.getElementById('fcFlashCount').textContent = (state.flashIndex + 1) + ' / ' + state.flashCards.length;
            document.getElementById('fcBigFlashcard').classList.remove('is-flipped');
            setProgress(state.flashIndex, state.flashCards.length, 'Đã xem ' + state.flashIndex + ' / ' + state.flashCards.length);
        }

        function renderTest() {
            var card = state.testCards[state.testIndex];
            state.testLocked = false;
            document.getElementById('fcTestPrompt').textContent = card.back;
            document.getElementById('fcTestCount').textContent = (state.testIndex + 1) + ' / ' + state.testCards.length;
            document.getElementById('fcTestInput').value = '';
            document.getElementById('fcTestInput').disabled = false;
            setFeedback(document.getElementById('fcTestFeedback'), '');
            setProgress(state.testIndex, state.testCards.length, 'Đã kiểm tra ' + state.testIndex + ' / ' + state.testCards.length);
        }

        function checkTest(showAnswer) {
            if (state.testLocked) return;
            var card = state.testCards[state.testIndex];
            var input = document.getElementById('fcTestInput');
            var feedback = document.getElementById('fcTestFeedback');
            var correct = normalize(input.value) === normalize(card.front);
            state.testLocked = true;
            input.disabled = true;
            setScore(correct ? 15 : 0);
            setFeedback(feedback, showAnswer ? 'Đáp án: ' + card.front : (correct ? 'Đúng rồi — tự gõ là nhớ lâu hơn.' : 'Chưa khớp. Đáp án: ' + card.front), correct);
            window.setTimeout(function () {
                state.testIndex = (state.testIndex + 1) % state.testCards.length;
                renderTest();
            }, 1050);
        }

        function renderMatch() {
            var board = document.getElementById('fcMatchBoard');
            var pool = shuffle(cards.slice(0, Math.min(8, cards.length)));
            state.matchTiles = [];
            state.matchFirst = null;
            state.matchDone = 0;
            board.textContent = '';
            pool.forEach(function (card) {
                state.matchTiles.push({ key: String(card.id), value: card.front, kind: 'front' });
                state.matchTiles.push({ key: String(card.id), value: card.back, kind: 'back' });
            });
            shuffle(state.matchTiles).forEach(function (tile, index) {
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'fc-match-tile';
                button.textContent = tile.value;
                button.dataset.tileKey = tile.key;
                button.dataset.tileKind = tile.kind;
                button.dataset.tileIndex = String(index);
                button.addEventListener('click', function () { pickMatchTile(button); });
                board.appendChild(button);
            });
            document.getElementById('fcMatchCount').textContent = '0 / ' + pool.length + ' cặp';
            setFeedback(document.getElementById('fcMatchFeedback'), 'Chọn một từ Hàn và nghĩa tương ứng.', null);
            setProgress(0, pool.length, 'Đã ghép 0 / ' + pool.length + ' cặp');
        }

        function pickMatchTile(button) {
            if (button.classList.contains('is-matched') || button.classList.contains('is-selected')) return;
            button.classList.add('is-selected');
            if (!state.matchFirst) { state.matchFirst = button; return; }
            var first = state.matchFirst;
            state.matchFirst = null;
            var matched = first.dataset.tileKey === button.dataset.tileKey && first.dataset.tileKind !== button.dataset.tileKind;
            if (matched) {
                first.classList.remove('is-selected');
                first.classList.add('is-matched');
                button.classList.remove('is-selected');
                button.classList.add('is-matched');
                state.matchDone += 1;
                setScore(12);
                setFeedback(document.getElementById('fcMatchFeedback'), state.matchDone === state.matchTiles.length / 2 ? 'Hoàn thành! Tốc độ rất tốt.' : 'Đúng cặp — tiếp tục nhé.', true);
                var matchTotal = state.matchTiles.length / 2;
                document.getElementById('fcMatchCount').textContent = state.matchDone + ' / ' + matchTotal + ' cặp';
                setProgress(state.matchDone, matchTotal, 'Đã ghép ' + state.matchDone + ' / ' + matchTotal + ' cặp');
            } else {
                first.classList.add('is-error');
                button.classList.add('is-error');
                setFeedback(document.getElementById('fcMatchFeedback'), 'Chưa khớp — thử lại.', false);
                window.setTimeout(function () {
                    first.classList.remove('is-selected', 'is-error');
                    button.classList.remove('is-selected', 'is-error');
                }, 520);
            }
        }

        function renderBlast() {
            if (state.blastInterval) window.clearInterval(state.blastInterval);
            state.blastRunning = false;
            state.blastTimer = 30;
            document.getElementById('fcBlastTimer').textContent = '30s';
            document.getElementById('fcBlastStart').hidden = false;
            document.getElementById('fcBlastStart').textContent = 'Bắt đầu game';
            document.getElementById('fcBlastTargets').textContent = '';
            document.getElementById('fcBlastPrompt').textContent = 'Sẵn sàng?';
            setFeedback(document.getElementById('fcBlastFeedback'), 'Mỗi đáp án đúng ghi 20 điểm.', null);
            setProgress(0, cards.length, 'Chưa bắt đầu');
        }

        function startBlast() {
            state.blastCards = shuffle(cards);
            state.blastIndex = 0;
            state.blastRunning = true;
            document.getElementById('fcBlastStart').hidden = true;
            renderBlastQuestion();
            state.blastInterval = window.setInterval(function () {
                state.blastTimer -= 1;
                document.getElementById('fcBlastTimer').textContent = state.blastTimer + 's';
                if (state.blastTimer <= 0) {
                    window.clearInterval(state.blastInterval);
                    state.blastRunning = false;
                    document.getElementById('fcBlastStart').hidden = false;
                    document.getElementById('fcBlastStart').textContent = 'Chơi lại';
                    setFeedback(document.getElementById('fcBlastFeedback'), 'Hết giờ — bạn được ' + score + ' điểm trong buổi này.', false);
                }
            }, 1000);
        }

        function renderBlastQuestion() {
            var card = state.blastCards[state.blastIndex % state.blastCards.length];
            var targets = shuffle([card].concat(shuffle(cards.filter(function (item) { return item.id !== card.id; })).slice(0, 3)));
            document.getElementById('fcBlastPrompt').textContent = card.back;
            var targetHost = document.getElementById('fcBlastTargets');
            targetHost.textContent = '';
            targets.forEach(function (choice) {
                var target = document.createElement('button');
                target.type = 'button';
                target.className = 'fc-blast-target';
                target.textContent = choice.front;
                target.addEventListener('click', function () {
                    if (!state.blastRunning) return;
                    var correct = choice.id === card.id;
                    target.classList.add(correct ? 'is-correct' : 'is-wrong');
                    if (correct) {
                        setScore(20);
                        state.blastIndex += 1;
                        setFeedback(document.getElementById('fcBlastFeedback'), 'Bắn trúng! +20 điểm.', true);
                        setProgress(state.blastIndex, cards.length, 'Đã bắn trúng ' + state.blastIndex + ' từ');
                        window.setTimeout(renderBlastQuestion, 260);
                    } else {
                        setFeedback(document.getElementById('fcBlastFeedback'), 'Trượt rồi — đừng để mất nhịp.', false);
                        window.setTimeout(function () { target.classList.remove('is-wrong'); }, 320);
                    }
                });
                targetHost.appendChild(target);
            });
        }

        tabs.forEach(function (tab) { tab.addEventListener('click', function () { showMode(tab.dataset.mode); }); });
        Array.prototype.forEach.call(host.querySelectorAll('.fc-mode-card[data-mode]'), function (button) {
            button.addEventListener('click', function () { showMode(button.dataset.mode); });
        });
        document.getElementById('fcBigFlashcard').addEventListener('click', function () { this.classList.toggle('is-flipped'); });
        document.getElementById('fcFlashPrev').addEventListener('click', function () {
            state.flashIndex = (state.flashIndex - 1 + state.flashCards.length) % state.flashCards.length; renderFlash();
        });
        document.getElementById('fcFlashNext').addEventListener('click', function () {
            state.flashIndex = (state.flashIndex + 1) % state.flashCards.length; renderFlash();
        });
        document.getElementById('fcFlashShuffle').addEventListener('click', function () {
            state.flashCards = shuffle(state.flashCards); state.flashIndex = 0; renderFlash();
        });
        document.getElementById('fcTestCheck').addEventListener('click', function () { checkTest(false); });
        document.getElementById('fcTestHint').addEventListener('click', function () { checkTest(true); });
        document.getElementById('fcTestInput').addEventListener('keydown', function (event) {
            if (event.key === 'Enter') checkTest(false);
        });
        document.getElementById('fcMatchRestart').addEventListener('click', renderMatch);
        document.getElementById('fcBlastStart').addEventListener('click', startBlast);
        Array.prototype.forEach.call(host.querySelectorAll('[data-speak-target]'), function (button) {
            button.addEventListener('click', function () { speak(document.getElementById(button.dataset.speakTarget).textContent); });
        });

        setScore(0);
        showMode('hub');
    });
})();
