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
        var matchDeckOptions;
        var matchSelectedDeckIds;
        try { matchDeckOptions = JSON.parse(host.dataset.matchDecks || '[]'); } catch (e) { matchDeckOptions = []; }
        try { matchSelectedDeckIds = JSON.parse(host.dataset.matchSelected || '[]'); } catch (e) { matchSelectedDeckIds = []; }
        matchDeckOptions = Array.isArray(matchDeckOptions) ? matchDeckOptions : [];
        matchSelectedDeckIds = Array.isArray(matchSelectedDeckIds)
            ? matchSelectedDeckIds.map(String)
            : [];
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
        function hangulCount(value) {
            return (String(value || '').match(/[\uac00-\ud7a3]/g) || []).length;
        }
        function gameClue(value) {
            var text = String(value || '').replace(/\s+/g, ' ').trim();
            var separator = text.indexOf(' — ');
            if (separator > 0) text = text.slice(0, separator).trim();
            return text.length > 120 ? text.slice(0, 117).trimEnd() + '…' : text;
        }
        function toHangulEntry(card) {
            var front = String(card.front || '').trim().normalize('NFC');
            var back = String(card.back || '').trim().normalize('NFC');
            var word = hangulCount(front) >= hangulCount(back) ? front : back;
            var clue = word === front ? back : front;
            if (!/^[\uac00-\ud7a3]{2,8}$/.test(word)) return null;
            return { card: card, word: word, clue: clue, syllables: Array.from(word) };
        }
        var hangulEntries = cards.map(toHangulEntry).filter(Boolean);
        var score = 0;
        var state = {
            learn: {
                items: shuffle(cards),
                index: 0,
                locked: false,
                currentType: 'choice',
                questionTypes: buildLearnQuestionTypes(cards.length),
                correct: 0,
                review: 0
            },
            test: { items: shuffle(cards), selections: {}, submitted: false },
            match: {
                done: 0,
                first: null,
                tiles: [],
                queue: [],
                offset: 0,
                batchSize: Math.max(1, Math.min(4, cards.length)),
                batchTotal: 0,
                batchDone: 0,
                round: 1,
                locked: false,
                media: true,
                feedbackTimer: null
            },
            tiles: { items: shuffle(hangulEntries), index: 0, completed: 0, combo: 0, answer: [], bank: [], locked: false, timerId: null, deadline: 0 },
            search: { round: 0, grid: [], entries: [], found: {}, start: null, selection: [], selecting: false },
            connect: { items: shuffle(hangulEntries), index: 0, completed: 0, combo: 0, tokens: [], selection: [], locked: false, drawing: false, pointer: null, resetTimer: null },
            blast: {
                items: shuffle(cards), index: 0, running: false, timer: 60,
                duration: 60000, startedAt: 0, raf: null, roundTimeout: null,
                combo: 0, hits: 0, shotLocked: false, roundId: 0, entities: []
            }
        };
        var scoreEl = document.getElementById('fcSessionScore');
        var progress = document.getElementById('fcProgressBar');
        var progressLabel = document.getElementById('fcProgressLabel');
        var headerMode = document.getElementById('fcHeaderMode');
        var labels = {
            learn: 'Học nhanh',
            test: 'Kiểm tra',
            match: 'Ghép cặp',
            tiles: 'Xếp âm tiết',
            'word-search': 'Săn chữ Hàn',
            'word-connect': 'Nối âm tiết',
            blast: 'Bắn từ'
        };
        if (headerMode) headerMode.textContent = labels[mode] || 'Học';

        function shuffle(arr) { var a = arr.slice(); for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
        function buildLearnQuestionTypes(total) {
            var types = [];
            for (var i = 0; i < total; i++) types.push(Math.random() < 0.5 ? 'choice' : 'write');
            if (total > 1) {
                types[0] = 'choice';
                types[1] = 'write';
            }
            return shuffle(types);
        }
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

        function learnChoicePool(card) {
            var result = [card], seen = {};
            seen[norm(card.back)] = true;
            shuffle(cards).forEach(function (candidate) {
                var key = norm(candidate.back);
                if (result.length < 4 && String(candidate.id) !== String(card.id) && key && !seen[key]) {
                    result.push(candidate);
                    seen[key] = true;
                }
            });
            return shuffle(result);
        }
        function resetLearnResult() {
            var learnCard = document.getElementById('fcLearnCard');
            var input = document.getElementById('fcLearnInput');
            learnCard.classList.remove('is-correct', 'is-wrong', 'is-skipped');
            input.classList.remove('is-correct', 'is-wrong');
            input.disabled = false;
            input.value = '';
            document.getElementById('fcLearnQuestionActions').hidden = false;
            document.getElementById('fcLearnCorrectAnswer').hidden = true;
            document.getElementById('fcLearnContinue').hidden = true;
            document.getElementById('fcLearnContinueHint').hidden = true;
            document.getElementById('fcLearnHintText').hidden = true;
            feedback('fcLearnFeedback', '');
        }
        function renderLearn() {
            var s = state.learn;
            var card = s.items[s.index];
            var answers = document.getElementById('fcLearnAnswers');
            var choiceStage = document.getElementById('fcLearnChoiceStage');
            var writeStage = document.getElementById('fcLearnWriteStage');
            var complete = document.getElementById('fcLearnComplete');
            var questionCard = document.getElementById('fcLearnCard');
            s.locked = false;
            s.currentType = s.questionTypes[s.index] || 'choice';
            complete.hidden = true;
            questionCard.hidden = false;
            resetLearnResult();
            document.getElementById('fcLearnCount').textContent = (s.index + 1) + ' / ' + s.items.length;
            answers.textContent = '';

            if (s.currentType === 'write') {
                document.getElementById('fcLearnTitle').textContent = 'Điền thuật ngữ';
                document.getElementById('fcLearnPromptLabel').textContent = 'NGHĨA TIẾNG VIỆT';
                document.getElementById('fcLearnPrompt').textContent = card.back;
                setMedia('fcLearnImage', card.backImage, card.back);
                choiceStage.hidden = true;
                writeStage.hidden = false;
                document.getElementById('fcLearnSubmit').disabled = true;
                window.requestAnimationFrame(function () {
                    var input = document.getElementById('fcLearnInput');
                    if (!input) return;
                    try {
                        input.focus({ preventScroll: true });
                    } catch (error) {
                        input.focus();
                    }
                });
            } else {
                document.getElementById('fcLearnTitle').textContent = 'Chọn nghĩa đúng';
                document.getElementById('fcLearnPromptLabel').textContent = 'TỪ HÀN';
                document.getElementById('fcLearnPrompt').textContent = card.front;
                setMedia('fcLearnImage', card.frontImage, card.front);
                choiceStage.hidden = false;
                writeStage.hidden = true;
                learnChoicePool(card).forEach(function (choice, index) {
                    var button = document.createElement('button');
                    var number = document.createElement('span');
                    var text = document.createElement('span');
                    button.type = 'button';
                    button.className = 'fc-answer-button';
                    button.dataset.cardId = String(choice.id);
                    number.className = 'fc-answer-number';
                    number.textContent = String(index + 1);
                    text.className = 'fc-answer-text';
                    text.textContent = choice.back;
                    button.appendChild(number);
                    button.appendChild(text);
                    button.addEventListener('click', function () { answerLearnChoice(button, choice, card); });
                    answers.appendChild(button);
                });
            }
            setProgress(s.index, s.items.length, 'Đã luyện ' + s.index + ' / ' + s.items.length + ' thẻ');
        }
        function finishLearnAnswer(ok, skipped, userAnswer) {
            var s = state.learn;
            var card = s.items[s.index];
            var learnCard = document.getElementById('fcLearnCard');
            s.locked = true;
            if (ok) {
                s.correct++;
                setScore(10);
                learnCard.classList.add('is-correct');
                feedback('fcLearnFeedback', 'Tuyệt vời!', true);
            } else {
                s.review++;
                learnCard.classList.add(skipped ? 'is-skipped' : 'is-wrong');
                feedback('fcLearnFeedback', skipped ? 'Không sao, hãy xem lại đáp án.' : 'Chưa chính xác, bạn vẫn đang học mà!', false);
                if (s.currentType === 'write') {
                    document.getElementById('fcLearnCorrectText').textContent = card.front;
                    document.getElementById('fcLearnCorrectAnswer').hidden = false;
                }
            }
            document.getElementById('fcLearnQuestionActions').hidden = true;
            document.getElementById('fcLearnContinue').hidden = false;
            document.getElementById('fcLearnContinueHint').hidden = false;
            setProgress(s.index + 1, s.items.length, 'Đã luyện ' + (s.index + 1) + ' / ' + s.items.length + ' thẻ');
        }
        function answerLearnChoice(button, choice, card) {
            var s = state.learn;
            if (s.locked) return;
            var ok = String(choice.id) === String(card.id);
            var answers = document.getElementById('fcLearnAnswers');
            Array.prototype.forEach.call(answers.children, function (answerButton) {
                answerButton.disabled = true;
                if (answerButton.dataset.cardId === String(card.id)) answerButton.classList.add('is-correct');
            });
            button.classList.add(ok ? 'is-correct' : 'is-wrong');
            finishLearnAnswer(ok, false, choice.back);
        }
        function submitLearnWrite(skipped) {
            var s = state.learn;
            if (s.locked || s.currentType !== 'write') return;
            var card = s.items[s.index];
            var input = document.getElementById('fcLearnInput');
            var answer = norm(input.value);
            var ok = !skipped && (answer === norm(card.front) || alternatives(card).indexOf(answer) >= 0);
            input.disabled = true;
            input.classList.add(ok ? 'is-correct' : 'is-wrong');
            finishLearnAnswer(ok, skipped, input.value);
        }
        function skipLearnQuestion() {
            var s = state.learn;
            if (s.locked) return;
            var card = s.items[s.index];
            if (s.currentType === 'write') {
                submitLearnWrite(true);
                return;
            }
            Array.prototype.forEach.call(document.getElementById('fcLearnAnswers').children, function (button) {
                button.disabled = true;
                if (button.dataset.cardId === String(card.id)) button.classList.add('is-correct');
            });
            finishLearnAnswer(false, true, '');
        }
        function continueLearn() {
            var s = state.learn;
            if (!s.locked) return;
            s.index++;
            if (s.index >= s.items.length) {
                document.getElementById('fcLearnCard').hidden = true;
                document.getElementById('fcLearnComplete').hidden = false;
                document.getElementById('fcLearnTitle').textContent = 'Kết quả phiên học';
                document.getElementById('fcLearnCount').textContent = s.items.length + ' / ' + s.items.length;
                document.getElementById('fcLearnCorrectCount').textContent = String(s.correct);
                document.getElementById('fcLearnReviewCount').textContent = String(s.review);
                setProgress(s.items.length, s.items.length, 'Đã hoàn thành ' + s.items.length + ' / ' + s.items.length + ' thẻ');
                return;
            }
            renderLearn();
        }
        function restartLearn() {
            var s = state.learn;
            s.items = shuffle(cards);
            s.index = 0;
            s.locked = false;
            s.correct = 0;
            s.review = 0;
            s.questionTypes = buildLearnQuestionTypes(s.items.length);
            renderLearn();
        }
        function learnHint(word) {
            return Array.from(String(word || '')).map(function (character, index) {
                return index === 0 || /\s/.test(character) ? character : '＿';
            }).join('');
        }

        function testChoicePool(card) {
            var result = [card], seen = {};
            seen[norm(card.front)] = true;
            shuffle(cards).forEach(function (candidate) {
                var key = norm(candidate.front);
                if (result.length >= 4 || seen[key]) return;
                seen[key] = true;
                result.push(candidate);
            });
            return shuffle(result);
        }
        function updateTestProgress() {
            var s = state.test;
            var answered = Object.keys(s.selections).length;
            var total = s.items.length;
            var headerCount = document.getElementById('fcTestHeaderCount');
            var answeredLabel = document.getElementById('fcTestAnsweredLabel');
            if (headerCount) headerCount.textContent = answered + ' / ' + total;
            if (answeredLabel) answeredLabel.textContent = 'Đã trả lời ' + answered + ' / ' + total + ' câu';
            setProgress(answered, total, 'Đã trả lời ' + answered + ' / ' + total + ' câu');
            Array.prototype.forEach.call(document.querySelectorAll('#fcTestOutlineLinks a'), function (link) {
                link.classList.toggle('is-answered',
                    Object.prototype.hasOwnProperty.call(s.selections, link.dataset.questionId));
            });
        }
        function selectTestAnswer(question, choice, button) {
            var s = state.test;
            if (s.submitted) return;
            var article = button.closest('.fc-test-question');
            Array.prototype.forEach.call(article.querySelectorAll('.fc-test-choice'), function (choiceButton) {
                choiceButton.classList.remove('is-selected');
            });
            article.querySelector('.fc-test-unknown').classList.remove('is-active');
            button.classList.add('is-selected');
            s.selections[String(question.id)] = String(choice.id);
            updateTestProgress();
        }
        function skipTestQuestion(question, button) {
            var s = state.test;
            if (s.submitted) return;
            var article = button.closest('.fc-test-question');
            Array.prototype.forEach.call(article.querySelectorAll('.fc-test-choice'), function (choiceButton) {
                choiceButton.classList.remove('is-selected');
            });
            button.classList.add('is-active');
            s.selections[String(question.id)] = '';
            updateTestProgress();
        }
        function renderTest() {
            var s = state.test;
            var hostQuestions = document.getElementById('fcTestQuestions');
            var outlineLinks = document.getElementById('fcTestOutlineLinks');
            var result = document.getElementById('fcTestResult');
            s.selections = {};
            s.submitted = false;
            hostQuestions.textContent = '';
            outlineLinks.textContent = '';
            result.hidden = true;
            document.getElementById('fcTestSubmit').disabled = false;

            s.items.forEach(function (card, index) {
                var article = document.createElement('article');
                article.className = 'fc-test-question';
                article.id = 'fcTestQuestion' + (index + 1);

                var meta = document.createElement('div');
                meta.className = 'fc-test-question-meta';
                var label = document.createElement('span');
                label.textContent = 'Định nghĩa';
                var audioButton = document.createElement('button');
                audioButton.type = 'button';
                audioButton.className = 'fc-test-audio';
                audioButton.title = 'Phát âm';
                audioButton.setAttribute('aria-label', 'Phát âm câu ' + (index + 1));
                audioButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 5 6 9H3v6h3l5 4Z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18 6a8.5 8.5 0 0 1 0 12"/></svg>';
                audioButton.addEventListener('click', function () { speak(card.back); });
                var count = document.createElement('span');
                count.className = 'fc-test-question-count';
                count.textContent = (index + 1) + '/' + s.items.length;
                meta.appendChild(label);
                meta.appendChild(audioButton);
                meta.appendChild(count);
                article.appendChild(meta);

                if (card.backImage) {
                    var image = document.createElement('img');
                    image.className = 'fc-test-question-image';
                    image.src = card.backImage;
                    image.alt = card.back;
                    article.appendChild(image);
                }

                var prompt = document.createElement('div');
                prompt.className = 'fc-test-question-prompt';
                prompt.textContent = card.back;
                article.appendChild(prompt);

                var instruction = document.createElement('span');
                instruction.className = 'fc-test-question-instruction';
                instruction.textContent = 'Chọn đáp án đúng';
                article.appendChild(instruction);

                var choices = document.createElement('div');
                choices.className = 'fc-test-choice-grid';
                testChoicePool(card).forEach(function (choice) {
                    var choiceButton = document.createElement('button');
                    choiceButton.type = 'button';
                    choiceButton.className = 'fc-test-choice';
                    choiceButton.dataset.choiceId = String(choice.id);
                    choiceButton.textContent = choice.front;
                    choiceButton.addEventListener('click', function () {
                        selectTestAnswer(card, choice, choiceButton);
                    });
                    choices.appendChild(choiceButton);
                });
                article.appendChild(choices);

                var unknown = document.createElement('button');
                unknown.type = 'button';
                unknown.className = 'fc-test-unknown';
                unknown.textContent = 'Bạn không biết?';
                unknown.addEventListener('click', function () { skipTestQuestion(card, unknown); });
                article.appendChild(unknown);
                hostQuestions.appendChild(article);

                var outlineLink = document.createElement('a');
                outlineLink.href = '#' + article.id;
                outlineLink.dataset.questionId = String(card.id);
                outlineLink.innerHTML = '<span>' + (index + 1) + '</span><strong>' +
                    card.back.replace(/[&<>"']/g, function (character) {
                        return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character];
                    }) + '</strong>';
                outlineLinks.appendChild(outlineLink);
            });
            updateTestProgress();
        }
        function submitTest() {
            var s = state.test;
            if (s.submitted) return;
            s.submitted = true;
            var correct = 0;
            s.items.forEach(function (card, index) {
                var article = document.getElementById('fcTestQuestion' + (index + 1));
                var selected = s.selections[String(card.id)];
                Array.prototype.forEach.call(article.querySelectorAll('.fc-test-choice'), function (button) {
                    button.disabled = true;
                    if (button.dataset.choiceId === String(card.id)) button.classList.add('is-correct');
                    if (selected && button.dataset.choiceId === String(selected) && selected !== String(card.id)) {
                        button.classList.add('is-wrong');
                    }
                });
                article.querySelector('.fc-test-unknown').disabled = true;
                article.classList.add(selected === String(card.id) ? 'is-correct' : 'is-wrong');
                if (selected === String(card.id)) correct++;
            });
            setScore(correct * 15);
            var total = s.items.length;
            var percent = total ? Math.round(correct / total * 100) : 0;
            document.getElementById('fcTestResultTitle').textContent =
                percent >= 80 ? 'Rất tốt, bạn đã nắm khá chắc bộ thẻ.' : 'Đã chấm xong bài kiểm tra.';
            document.getElementById('fcTestResultText').textContent =
                'Bạn trả lời đúng ' + correct + ' / ' + total + ' câu (' + percent + '%).';
            document.getElementById('fcTestResult').hidden = false;
            document.getElementById('fcTestSubmit').disabled = true;
            document.getElementById('fcTestHeaderCount').textContent = correct + ' / ' + total;
            setProgress(total, total, 'Đã hoàn thành bài kiểm tra');
            document.getElementById('fcTestResult').scrollIntoView({ behavior: 'smooth', block: 'center' });
        }

        function showMatchFeedback(message, kind, anchor) {
            var toast = document.getElementById('fcMatchFeedback');
            var game = document.querySelector('.fc-match-game');
            var s = state.match;
            window.clearTimeout(s.feedbackTimer);
            toast.textContent = message;
            toast.className = 'fc-match-toast is-' + kind;
            toast.hidden = false;
            if (anchor && game) {
                var buttonBox = anchor.getBoundingClientRect();
                var gameBox = game.getBoundingClientRect();
                toast.style.left = Math.max(14, Math.min(gameBox.width - 180, buttonBox.left - gameBox.left + buttonBox.width / 2 - 70)) + 'px';
                toast.style.top = Math.max(12, buttonBox.top - gameBox.top - 38) + 'px';
            }
            s.feedbackTimer = window.setTimeout(function () {
                toast.hidden = true;
                toast.className = 'fc-match-toast';
            }, kind === 'success' ? 650 : 900);
        }

        function matchTileMedia(card, kind) {
            if (!state.match.media) return '';
            if (kind === 'front') return card.frontImage || '';
            return card.backImage || '';
        }

        function appendMatchTile(board, tile) {
            var s = state.match;
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'fc-match-tile';
            button.dataset.key = tile.key;
            button.dataset.kind = tile.kind;
            if (tile.image) {
                button.classList.add('has-image');
                var image = document.createElement('img');
                image.src = tile.image;
                image.alt = tile.text || '';
                button.appendChild(image);
                var label = document.createElement('span');
                label.textContent = 'ẢNH';
                button.appendChild(label);
            } else {
                button.textContent = tile.text;
            }
            button.addEventListener('click', function () {
                if (s.locked || button.classList.contains('is-selected')) return;
                button.classList.add('is-selected');
                if (!s.first) {
                    s.first = button;
                    return;
                }
                var first = s.first;
                s.first = null;
                s.locked = true;
                var correct = first.dataset.key === button.dataset.key &&
                    first.dataset.kind !== button.dataset.kind;
                if (correct) {
                    first.classList.add('is-matched');
                    button.classList.add('is-matched');
                    s.done++;
                    s.batchDone++;
                    setScore(12);
                    showMatchFeedback('+12', 'success', button);
                    document.getElementById('fcMatchCount').textContent =
                        s.done + ' / ' + cards.length + ' cặp';
                    setProgress(s.done, cards.length,
                        'Đã ghép ' + s.done + ' / ' + cards.length + ' cặp');
                    window.setTimeout(function () {
                        first.remove();
                        button.remove();
                        s.locked = false;
                        if (s.batchDone >= s.batchTotal) {
                            if (s.done >= cards.length) renderMatchComplete();
                            else {
                                s.offset += s.batchTotal;
                                s.round++;
                                showMatchFeedback('Vòng tiếp theo', 'round', board);
                                window.setTimeout(renderMatchRound, 430);
                            }
                        }
                    }, 310);
                } else {
                    first.classList.add('is-error');
                    button.classList.add('is-error');
                    showMatchFeedback('Chưa khớp', 'error', button);
                    window.setTimeout(function () {
                        first.classList.remove('is-selected', 'is-error');
                        button.classList.remove('is-selected', 'is-error');
                        s.locked = false;
                    }, 520);
                }
            });
            board.appendChild(button);
        }

        function renderMatchRound() {
            var board = document.getElementById('fcMatchBoard');
            var s = state.match;
            var batch = s.queue.slice(s.offset, s.offset + s.batchSize);
            s.batchTotal = batch.length;
            s.batchDone = 0;
            s.first = null;
            s.locked = false;
            s.tiles = [];
            board.textContent = '';
            document.getElementById('fcMatchRoundLabel').textContent =
                'Vòng ' + s.round + ' · ' + batch.length + ' cặp';
            batch.forEach(function (card) {
                var frontImage = matchTileMedia(card, 'front');
                var backImage = matchTileMedia(card, 'back');
                var mediaKind = '';
                if (frontImage && backImage) mediaKind = Math.random() < .5 ? 'front' : 'back';
                else if (frontImage) mediaKind = 'front';
                else if (backImage) mediaKind = 'back';
                s.tiles.push({
                    key: String(card.id),
                    text: card.front,
                    image: mediaKind === 'front' ? frontImage : '',
                    kind: 'front'
                }, {
                    key: String(card.id),
                    text: card.back,
                    image: mediaKind === 'back' ? backImage : '',
                    kind: 'back'
                });
            });
            shuffle(s.tiles).forEach(function (tile) { appendMatchTile(board, tile); });
        }

        function renderMatchComplete() {
            var board = document.getElementById('fcMatchBoard');
            board.textContent = '';
            board.classList.add('is-complete');
            var panel = document.createElement('div');
            panel.className = 'fc-match-complete';
            panel.innerHTML =
                '<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="32" r="27"/><path d="m19 32 9 9 18-20"/></svg>' +
                '<span>HOÀN THÀNH</span><strong>Bạn đã ghép hết ' + cards.length + ' cặp.</strong>' +
                '<p>Thử tăng số cặp mỗi vòng hoặc bật ảnh để khó hơn.</p>';
            board.appendChild(panel);
            document.getElementById('fcMatchRoundLabel').textContent = 'Đã hoàn thành';
        }

        function renderMatch() {
            var s = state.match;
            var mediaToggle = document.getElementById('fcMatchMedia');
            var hasMedia = cards.some(function (card) { return card.frontImage || card.backImage; });
            s.done = 0;
            s.first = null;
            s.queue = shuffle(cards);
            s.offset = 0;
            s.round = 1;
            s.locked = false;
            s.batchSize = Math.max(1, Math.min(s.batchSize, cards.length));
            s.media = Boolean(mediaToggle && mediaToggle.checked && hasMedia);
            if (mediaToggle) {
                mediaToggle.disabled = !hasMedia;
                mediaToggle.closest('label').classList.toggle('is-disabled', !hasMedia);
            }
            document.getElementById('fcMatchBoard').classList.remove('is-complete');
            document.getElementById('fcMatchCount').textContent = '0 / ' + cards.length + ' cặp';
            setProgress(0, cards.length, 'Ghép đúng các cặp');
            renderMatchRound();
        }

        function renderMatchDeckPicker() {
            var optionsHost = document.getElementById('fcMatchDeckOptions');
            var count = document.getElementById('fcMatchDeckCount');
            if (!optionsHost || !count) return;
            var currentDeckId = String(host.dataset.deckId || '');
            var selected = {};
            matchSelectedDeckIds.forEach(function (id) { selected[String(id)] = true; });
            selected[currentDeckId] = true;
            optionsHost.textContent = '';

            function updateCount() {
                var total = 1 + optionsHost.querySelectorAll('input[name="mix"]:checked').length;
                count.textContent = total + ' bộ đang chọn';
            }

            matchDeckOptions.forEach(function (deck) {
                if (!deck || deck.id == null) return;
                var id = String(deck.id);
                var current = id === currentDeckId;
                var option = document.createElement('label');
                option.className = 'fc-match-deck-option';
                if (current) option.classList.add('is-current');

                var checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.checked = Boolean(selected[id]);
                checkbox.disabled = current;
                if (!current) {
                    checkbox.name = 'mix';
                    checkbox.value = id;
                    checkbox.addEventListener('change', function () {
                        var checked = optionsHost.querySelectorAll('input[name="mix"]:checked');
                        if (checked.length > 7) {
                            checkbox.checked = false;
                            var picker = document.getElementById('fcMatchDeckPicker');
                            picker.classList.remove('is-limit');
                            void picker.offsetWidth;
                            picker.classList.add('is-limit');
                            window.setTimeout(function () { picker.classList.remove('is-limit'); }, 480);
                        }
                        updateCount();
                    });
                }

                var copy = document.createElement('span');
                var title = document.createElement('strong');
                title.textContent = deck.title || ('Bộ thẻ #' + id);
                var meta = document.createElement('small');
                var owner = deck.owner ? 'Của bạn' : (deck.ownerName || 'Được chia sẻ');
                meta.textContent = (deck.cardCount || 0) + ' thẻ · ' + owner +
                    (current ? ' · Bộ hiện tại' : '');
                copy.appendChild(title);
                copy.appendChild(meta);
                option.appendChild(checkbox);
                option.appendChild(copy);
                optionsHost.appendChild(option);
            });

            if (!optionsHost.children.length) {
                var empty = document.createElement('p');
                empty.className = 'fc-match-deck-empty';
                empty.textContent = 'Chưa có bộ thẻ nào khác để trộn.';
                optionsHost.appendChild(empty);
            }
            updateCount();
        }

        function setEligibility(id, playable) {
            var element = document.getElementById(id);
            if (element) element.hidden = playable;
        }

        function renderTiles() {
            var s = state.tiles;
            var bank = document.getElementById('fcTilesBank');
            var answer = document.getElementById('fcTilesAnswer');
            var check = document.getElementById('fcTilesCheck');
            var reset = document.getElementById('fcTilesReset');
            setEligibility('fcTilesEligibility', Boolean(s.items.length));
            window.clearInterval(s.timerId);
            bank.textContent = '';
            answer.textContent = '';
            answer.classList.remove('is-correct', 'is-wrong');
            if (!s.items.length) {
                check.disabled = true;
                reset.disabled = true;
                document.getElementById('fcTilesCount').textContent = '0 từ';
                setProgress(0, 0, 'Cần từ Hangul để chơi');
                return;
            }
            var entry = s.items[s.index % s.items.length];
            s.locked = false;
            s.answer = new Array(entry.syllables.length).fill(null);
            var correctTiles = entry.syllables.map(function (syllable, index) {
                return { id: 'tile-' + s.index + '-' + index, value: syllable, answer: true };
            });
            var usedValues = {};
            entry.syllables.forEach(function (syllable) { usedValues[syllable] = true; });
            var noisePool = shuffle(hangulEntries.reduce(function (all, item) {
                return all.concat(item.syllables || []);
            }, []).filter(function (syllable) {
                if (!syllable || usedValues[syllable]) return false;
                usedValues[syllable] = true;
                return true;
            }));
            var noiseCount = Math.min(2, Math.max(1, Math.floor(entry.syllables.length / 3)));
            var noiseTiles = noisePool.slice(0, noiseCount).map(function (syllable, index) {
                return { id: 'tile-' + s.index + '-noise-' + index, value: syllable, answer: false };
            });
            s.bank = shuffle(correctTiles.concat(noiseTiles));
            if (s.bank.length > 1 && s.bank.map(function (token) { return token.value; }).join('') === entry.word) {
                s.bank.push(s.bank.shift());
            }
            document.getElementById('fcTilesClue').textContent = gameClue(entry.clue);
            document.getElementById('fcTilesCount').textContent = s.completed + ' từ';
            document.getElementById('fcTilesCombo').textContent = s.combo;
            feedback('fcTilesFeedback', '', null);

            var beltStepSeconds = Math.max(.95, 1.28 - Math.floor(s.completed / 3) * .06);
            var beltCycleSeconds = Math.max(5.2, s.bank.length * beltStepSeconds);
            var roundSeconds = Math.max(9, Math.ceil(beltCycleSeconds + 3 - Math.floor(s.completed / 4)));
            s.deadline = Date.now() + roundSeconds * 1000;
            function updateTilesTimer() {
                var remaining = Math.max(0, s.deadline - Date.now());
                document.getElementById('fcTilesSeconds').textContent = Math.ceil(remaining / 1000);
                document.getElementById('fcTilesTimer').style.width = (remaining / (roundSeconds * 1000) * 100) + '%';
                if (remaining || s.locked) return;
                window.clearInterval(s.timerId);
                s.combo = 0;
                document.getElementById('fcTilesCombo').textContent = '0';
                answer.classList.remove('is-correct');
                answer.classList.add('is-wrong');
                feedback('fcTilesFeedback', 'Băng chuyền quá nhiệt — mất chuỗi, các âm tiết đã bắt vẫn được giữ.', false);
                s.deadline = Date.now() + roundSeconds * 1000;
                window.setTimeout(function () { answer.classList.remove('is-wrong'); }, 440);
                s.timerId = window.setInterval(updateTilesTimer, 80);
            }
            updateTilesTimer();
            s.timerId = window.setInterval(updateTilesTimer, 80);

            function filledCount() {
                return s.answer.filter(Boolean).length;
            }

            function placeToken(token, slotIndex) {
                if (s.locked || !token || slotIndex < 0 || slotIndex >= s.answer.length) return;
                var currentIndex = s.answer.findIndex(function (item) { return item && item.id === token.id; });
                var displaced = s.answer[slotIndex];
                if (currentIndex >= 0) s.answer[currentIndex] = displaced || null;
                else s.answer[slotIndex] = token;
                if (currentIndex >= 0 && currentIndex !== slotIndex) s.answer[slotIndex] = token;
                paint();
            }

            function paint() {
                answer.textContent = '';
                answer.style.setProperty('--slot-count', entry.syllables.length);
                answer.classList.toggle('has-progress', filledCount() > 0);
                s.answer.forEach(function (token, slotIndex) {
                    var slot = document.createElement('div');
                    slot.className = 'fc-tiles-slot' + (token ? ' is-filled' : '');
                    slot.dataset.slotIndex = String(slotIndex);
                    slot.setAttribute('aria-label', 'Vị trí ' + (slotIndex + 1));
                    slot.addEventListener('dragover', function (event) { if (!s.locked) event.preventDefault(); });
                    slot.addEventListener('drop', function (event) {
                        event.preventDefault();
                        var id = event.dataTransfer.getData('text/plain');
                        placeToken(s.bank.find(function (item) { return item.id === id; }), slotIndex);
                    });
                    if (token) {
                        var placed = document.createElement('button');
                        placed.type = 'button';
                        placed.className = 'fc-syllable-tile is-placed';
                        placed.draggable = !s.locked;
                        placed.textContent = token.value;
                        placed.setAttribute('aria-label', 'Bỏ âm tiết ' + token.value + ' khỏi vị trí ' + (slotIndex + 1));
                        placed.addEventListener('dragstart', function (event) { event.dataTransfer.setData('text/plain', token.id); });
                        placed.addEventListener('click', function () {
                            if (s.locked) return;
                            s.answer[slotIndex] = null;
                            paint();
                        });
                        slot.appendChild(placed);
                    } else {
                        var index = document.createElement('span');
                        index.textContent = String(slotIndex + 1);
                        slot.appendChild(index);
                    }
                    answer.appendChild(slot);
                });
                if (!bank.children.length) {
                    s.bank.forEach(function (token, bankIndex) {
                        var button = document.createElement('button');
                        button.type = 'button';
                        button.className = 'fc-syllable-tile';
                        button.dataset.tileId = token.id;
                        button.style.setProperty('--tile-delay', (bankIndex * beltStepSeconds) + 's');
                        button.style.setProperty('--tile-cycle', beltCycleSeconds + 's');
                        button.textContent = token.value;
                        button.addEventListener('dragstart', function (event) { event.dataTransfer.setData('text/plain', token.id); });
                        button.addEventListener('click', function () {
                            var isUsed = s.answer.some(function (item) { return item && item.id === token.id; });
                            if (s.locked || isUsed) return;
                            placeToken(token, s.answer.findIndex(function (item) { return !item; }));
                        });
                        bank.appendChild(button);
                    });
                }
                Array.prototype.forEach.call(bank.children, function (button) {
                    var used = s.answer.some(function (item) { return item && item.id === button.dataset.tileId; });
                    button.disabled = used || s.locked;
                    button.draggable = !used && !s.locked;
                });
                check.disabled = filledCount() !== entry.syllables.length || s.locked;
            }
            reset.disabled = false;
            reset.onclick = function () { if (!s.locked) { s.answer = new Array(entry.syllables.length).fill(null); paint(); } };
            check.onclick = function () {
                if (s.locked || filledCount() !== entry.syllables.length) return;
                var correct = s.answer.map(function (token) { return token.value; }).join('') === entry.word;
                answer.classList.remove('is-correct', 'is-wrong');
                answer.classList.add(correct ? 'is-correct' : 'is-wrong');
                if (!correct) {
                    s.combo = 0;
                    document.getElementById('fcTilesCombo').textContent = '0';
                    s.deadline = Math.max(Date.now() + 1200, s.deadline - 1800);
                    feedback('fcTilesFeedback', 'Sai thứ tự — lõi ráp đang quá nhiệt!', false);
                    window.setTimeout(function () { answer.classList.remove('is-wrong'); }, 520);
                    return;
                }
                s.locked = true;
                window.clearInterval(s.timerId);
                s.completed++;
                s.combo++;
                document.getElementById('fcTilesCombo').textContent = s.combo;
                document.getElementById('fcTilesCount').textContent = s.completed + ' từ';
                setScore(12 + Math.min(18, s.combo * 3));
                feedback('fcTilesFeedback', 'Chính xác: ' + entry.word, true);
                setProgress(s.completed, s.items.length, 'Đã ghép ' + s.completed + ' từ');
                paint();
                window.setTimeout(function () {
                    answer.classList.remove('is-correct');
                    s.index = (s.index + 1) % s.items.length;
                    renderTiles();
                }, 760);
            };
            paint();
        }

        function straightPath(start, end, size) {
            var sr = Math.floor(start / size), sc = start % size;
            var er = Math.floor(end / size), ec = end % size;
            var dr = er === sr ? 0 : (er > sr ? 1 : -1);
            var dc = ec === sc ? 0 : (ec > sc ? 1 : -1);
            if (!(sr === er || sc === ec || Math.abs(er - sr) === Math.abs(ec - sc))) return [];
            var length = Math.max(Math.abs(er - sr), Math.abs(ec - sc));
            var result = [];
            for (var i = 0; i <= length; i++) result.push((sr + dr * i) * size + sc + dc * i);
            return result;
        }

        function buildSearchRound() {
            var s = state.search;
            var size = 9;
            var filler = Array.from('가나다라마바사아자차카타파하공부학교문화여행음식영화친구사랑뉴스한국');
            var selected = shuffle(hangulEntries).slice(0, Math.min(5, hangulEntries.length));
            var grid = new Array(size * size).fill('');
            var directions = [[0, 1], [1, 0], [1, 1], [1, -1]];
            var placed = [];
            selected.forEach(function (entry) {
                var success = false;
                for (var attempt = 0; attempt < 120 && !success; attempt++) {
                    var direction = directions[Math.floor(Math.random() * directions.length)];
                    var row = Math.floor(Math.random() * size);
                    var col = Math.floor(Math.random() * size);
                    var lastRow = row + direction[0] * (entry.syllables.length - 1);
                    var lastCol = col + direction[1] * (entry.syllables.length - 1);
                    if (lastRow < 0 || lastRow >= size || lastCol < 0 || lastCol >= size) continue;
                    var path = [];
                    var fits = entry.syllables.every(function (syllable, index) {
                        var cell = (row + direction[0] * index) * size + col + direction[1] * index;
                        path.push(cell);
                        return !grid[cell] || grid[cell] === syllable;
                    });
                    if (!fits) continue;
                    entry.syllables.forEach(function (syllable, index) { grid[path[index]] = syllable; });
                    placed.push({ word: entry.word, clue: entry.clue, path: path });
                    success = true;
                }
            });
            grid = grid.map(function (value) { return value || filler[Math.floor(Math.random() * filler.length)]; });
            s.grid = grid;
            s.entries = placed;
            s.found = {};
            s.selection = [];
            s.round++;
        }

        function renderWordSearch() {
            var s = state.search;
            var board = document.getElementById('fcSearchBoard');
            var words = document.getElementById('fcSearchWords');
            setEligibility('fcSearchEligibility', Boolean(hangulEntries.length));
            board.textContent = '';
            words.textContent = '';
            if (!hangulEntries.length) {
                document.getElementById('fcSearchCount').textContent = '0 / 0 từ';
                return;
            }
            if (!s.grid.length) buildSearchRound();

            function paintSelection() {
                board.querySelectorAll('.fc-search-cell').forEach(function (cell) {
                    var index = Number(cell.dataset.index);
                    cell.classList.toggle('is-selected', s.selection.indexOf(index) >= 0);
                    cell.classList.toggle('is-found', s.entries.some(function (entry) {
                        return s.found[entry.word] && entry.path.indexOf(index) >= 0;
                    }));
                });
            }
            function finishSelection() {
                if (!s.selection.length) return;
                var value = s.selection.map(function (index) { return s.grid[index]; }).join('');
                var reverse = Array.from(value).reverse().join('');
                var match = s.entries.find(function (entry) {
                    return !s.found[entry.word] && (entry.word === value || entry.word === reverse);
                });
                if (match) {
                    s.found[match.word] = true;
                    setScore(10);
                    feedback('fcSearchFeedback', 'Đã tìm thấy ' + match.word + ' · ' + match.clue, true);
                } else {
                    board.classList.remove('is-miss');
                    void board.offsetWidth;
                    board.classList.add('is-miss');
                    feedback('fcSearchFeedback', 'Chưa có từ nào trên đường kéo đó.', false);
                }
                s.selection = [];
                paintSelection();
                paintWords();
                var foundCount = Object.keys(s.found).length;
                document.getElementById('fcSearchCount').textContent = foundCount + ' / ' + s.entries.length + ' từ';
                setProgress(foundCount, s.entries.length, 'Đã tìm ' + foundCount + ' / ' + s.entries.length + ' từ');
                if (foundCount === s.entries.length) feedback('fcSearchFeedback', 'Hoàn thành bảng! Tạo bảng mới để chơi tiếp.', true);
            }
            function paintWords() {
                words.textContent = '';
                s.entries.forEach(function (entry) {
                    var chip = document.createElement('span');
                    chip.className = 'fc-search-word' + (s.found[entry.word] ? ' is-found' : '');
                    chip.innerHTML = '<strong class="fc-search-word-term"></strong><small class="fc-search-word-clue"></small>';
                    chip.querySelector('.fc-search-word-term').textContent = entry.word;
                    chip.querySelector('.fc-search-word-clue').textContent = entry.clue;
                    words.appendChild(chip);
                });
            }
            s.grid.forEach(function (syllable, index) {
                var cell = document.createElement('button');
                cell.type = 'button';
                cell.className = 'fc-search-cell';
                cell.dataset.index = String(index);
                cell.textContent = syllable;
                board.appendChild(cell);
            });
            board.onpointerdown = function (event) {
                var cell = event.target.closest('.fc-search-cell');
                if (!cell) return;
                event.preventDefault();
                s.selecting = true;
                s.start = Number(cell.dataset.index);
                s.selection = [s.start];
                board.setPointerCapture(event.pointerId);
                paintSelection();
            };
            board.onpointermove = function (event) {
                if (!s.selecting) return;
                var target = document.elementFromPoint(event.clientX, event.clientY);
                var cell = target && target.closest ? target.closest('.fc-search-cell') : null;
                if (!cell || !board.contains(cell)) return;
                var path = straightPath(s.start, Number(cell.dataset.index), 9);
                if (path.length) { s.selection = path; paintSelection(); }
            };
            board.onpointerup = function (event) {
                if (!s.selecting) return;
                s.selecting = false;
                if (board.hasPointerCapture(event.pointerId)) board.releasePointerCapture(event.pointerId);
                finishSelection();
            };
            board.onpointercancel = board.onpointerup;
            document.getElementById('fcSearchRestart').onclick = function () {
                buildSearchRound();
                feedback('fcSearchFeedback', '', null);
                renderWordSearch();
            };
            paintWords();
            document.getElementById('fcSearchCount').textContent = '0 / ' + s.entries.length + ' từ';
            setProgress(0, s.entries.length, 'Bảng mới · ' + s.entries.length + ' từ cần tìm');
        }

        function renderConnectLegacy() {
            var s = state.connect;
            var wheel = document.getElementById('fcConnectWheel');
            var nodes = document.getElementById('fcConnectNodes');
            var lines = document.getElementById('fcConnectLines');
            var answer = document.getElementById('fcConnectAnswer');
            setEligibility('fcConnectEligibility', Boolean(s.items.length));
            nodes.textContent = '';
            lines.textContent = '';
            window.clearTimeout(s.resetTimer);
            s.drawing = false;
            s.pointer = null;
            if (!s.items.length) {
                document.getElementById('fcConnectCount').textContent = '0 từ';
                return;
            }

            var entry = s.items[s.index % s.items.length];
            var usedSyllables = {};
            var correctTokens = entry.syllables.map(function (syllable, index) {
                usedSyllables[syllable] = true;
                return { id: 'connect-' + s.index + '-correct-' + index, value: syllable, order: index, distractor: false };
            });
            var distractorPool = shuffle(hangulEntries.reduce(function (all, item) {
                return all.concat(item.syllables || []);
            }, []).filter(function (syllable) {
                if (!syllable || usedSyllables[syllable]) return false;
                usedSyllables[syllable] = true;
                return true;
            }));
            var fallbackPool = ['가', '나', '다', '라', '마', '바', '사', '아', '자', '차', '카', '타', '파', '하'].filter(function (syllable) {
                return !usedSyllables[syllable];
            });
            var distractorCount = 3 + Math.floor(Math.random() * 4);
            var distractors = distractorPool.concat(fallbackPool).slice(0, distractorCount).map(function (syllable, index) {
                return { id: 'connect-' + s.index + '-noise-' + index, value: syllable, order: -1, distractor: true };
            });

            s.locked = false;
            s.selection = [];
            s.tokens = shuffle(correctTokens.concat(distractors));
            s.coordinates = {};
            wheel.classList.toggle('is-dense', s.tokens.length > 8);
            document.getElementById('fcConnectClue').textContent = gameClue(entry.clue);
            document.getElementById('fcConnectCount').textContent = s.completed + ' từ';
            feedback('fcConnectFeedback', '', null);

            function selectionHas(token) {
                return s.selection.some(function (item) { return item.id === token.id; });
            }

            function nextIs(token) {
                return !token.distractor && token.order === s.selection.length;
            }

            function coordinatePoint(token) {
                return s.coordinates[token.id];
            }

            function createLine(className, points) {
                if (points.length < 2) return;
                var polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
                polyline.setAttribute('class', className);
                polyline.setAttribute('points', points.map(function (point) { return point.x + ',' + point.y; }).join(' '));
                lines.appendChild(polyline);
            }

            function paintPath() {
                lines.textContent = '';
                Array.prototype.forEach.call(nodes.children, function (button) {
                    var selected = s.selection.some(function (item) { return item.id === button.dataset.connectId; });
                    button.classList.toggle('is-selected', selected);
                    button.classList.toggle('is-next', !s.locked && button.dataset.connectOrder === String(s.selection.length));
                });
                var points = s.selection.map(coordinatePoint).filter(Boolean);
                createLine('fc-connect-path', points);
                if (s.drawing && s.pointer && points.length) createLine('fc-connect-draft', [points[points.length - 1], s.pointer]);
                answer.textContent = s.selection.length ? s.selection.map(function (item) { return item.value; }).join('') : 'Giữ và kéo qua các âm tiết để nối';
            }

            function pointFromEvent(event) {
                var rect = wheel.getBoundingClientRect();
                return {
                    x: Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100)),
                    y: Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100))
                };
            }

            function tokenFromEvent(event) {
                var target = document.elementFromPoint(event.clientX, event.clientY);
                var button = target && target.closest ? target.closest('.fc-connect-node') : null;
                if (!button || !wheel.contains(button)) return null;
                return s.tokens.find(function (token) { return token.id === button.dataset.connectId; }) || null;
            }

            function clearWithError(message) {
                if (s.locked) return;
                s.drawing = false;
                s.pointer = null;
                wheel.classList.remove('is-correct');
                wheel.classList.add('is-wrong');
                feedback('fcConnectFeedback', message || 'Đường nối chưa đúng. Hãy thử lại.', false);
                paintPath();
                window.clearTimeout(s.resetTimer);
                s.resetTimer = window.setTimeout(function () {
                    s.selection = [];
                    wheel.classList.remove('is-wrong');
                    paintPath();
                }, 520);
            }

            function completeIfReady() {
                if (s.selection.length !== entry.syllables.length) return;
                s.locked = true;
                s.drawing = false;
                s.pointer = null;
                wheel.classList.remove('is-wrong');
                wheel.classList.add('is-correct');
                s.completed++;
                document.getElementById('fcConnectCount').textContent = s.completed + ' từ';
                setScore(15);
                feedback('fcConnectFeedback', 'Nối đúng: ' + entry.word, true);
                setProgress(s.completed, s.items.length, 'Đã nối ' + s.completed + ' từ');
                paintPath();
                window.setTimeout(function () {
                    wheel.classList.remove('is-correct');
                    s.index = (s.index + 1) % s.items.length;
                    renderConnect();
                }, 820);
            }

            function addToken(token) {
                if (s.locked || !token) return false;
                var existing = s.selection.findIndex(function (item) { return item.id === token.id; });
                if (existing >= 0) {
                    s.selection = s.selection.slice(0, existing + 1);
                    paintPath();
                    return false;
                }
                if (!nextIs(token)) return false;
                s.selection.push(token);
                paintPath();
                completeIfReady();
                return true;
            }

            function renderNodes() {
                var radius = s.tokens.length > 9 ? 38 : 35;
                var rotation = ((s.index + 1) * 0.43) % (Math.PI * 2);
                s.tokens.forEach(function (token, index) {
                    var angle = rotation + (Math.PI * 2 * index / s.tokens.length) - Math.PI / 2;
                    var x = 50 + Math.cos(angle) * radius;
                    var y = 50 + Math.sin(angle) * radius;
                    var button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'fc-connect-node' + (token.distractor ? ' is-distractor' : '');
                    button.dataset.connectId = token.id;
                    button.dataset.connectOrder = String(token.order);
                    button.style.left = x + '%';
                    button.style.top = y + '%';
                    button.setAttribute('aria-label', (token.distractor ? 'Âm tiết nhiễu ' : 'Âm tiết ') + token.value);
                    button.textContent = token.value;
                    button.addEventListener('pointerdown', function (event) {
                        event.preventDefault();
                        event.stopPropagation();
                        if (s.locked) return;
                        window.clearTimeout(s.resetTimer);
                        s.drawing = true;
                        s.pointer = pointFromEvent(event);
                        try { wheel.setPointerCapture(event.pointerId); } catch (ignore) { }
                        if (!selectionHas(token) && !nextIs(token)) {
                            clearWithError('Âm tiết này chưa phải bước kế tiếp. Hãy kéo theo đúng thứ tự.');
                            return;
                        }
                        addToken(token);
                        paintPath();
                    });
                    button.addEventListener('keydown', function (event) {
                        if (event.key !== 'Enter' && event.key !== ' ') return;
                        event.preventDefault();
                        if (!selectionHas(token) && !nextIs(token)) {
                            clearWithError('Âm tiết này chưa phải bước kế tiếp. Hãy thử lại.');
                            return;
                        }
                        addToken(token);
                    });
                    nodes.appendChild(button);
                    s.coordinates[token.id] = { x: x, y: y };
                });
            }

            wheel.onpointermove = function (event) {
                if (!s.drawing || s.locked) return;
                s.pointer = pointFromEvent(event);
                var token = tokenFromEvent(event);
                if (token && nextIs(token)) addToken(token);
                paintPath();
            };
            wheel.onpointerup = function (event) {
                if (!s.drawing) return;
                var token = tokenFromEvent(event);
                s.drawing = false;
                s.pointer = null;
                try { if (wheel.hasPointerCapture(event.pointerId)) wheel.releasePointerCapture(event.pointerId); } catch (ignore) { }
                if (!s.locked && token && !selectionHas(token) && !nextIs(token)) {
                    clearWithError('Đường nối chưa đúng. Hãy thử lại.');
                    return;
                }
                paintPath();
            };
            wheel.onpointercancel = function (event) {
                s.drawing = false;
                s.pointer = null;
                try { if (wheel.hasPointerCapture(event.pointerId)) wheel.releasePointerCapture(event.pointerId); } catch (ignore) { }
                paintPath();
            };
            document.getElementById('fcConnectReset').onclick = function () {
                if (s.locked) return;
                window.clearTimeout(s.resetTimer);
                s.selection = [];
                s.drawing = false;
                s.pointer = null;
                wheel.classList.remove('is-wrong');
                feedback('fcConnectFeedback', '', null);
                paintPath();
            };
            renderNodes();
            paintPath();
        }

        function renderConnect() {
            var s = state.connect;
            var wheel = document.getElementById('fcConnectWheel');
            var nodes = document.getElementById('fcConnectNodes');
            var lines = document.getElementById('fcConnectLines');
            var answer = document.getElementById('fcConnectAnswer');
            setEligibility('fcConnectEligibility', Boolean(s.items.length));
            nodes.textContent = '';
            lines.textContent = '';
            window.clearTimeout(s.resetTimer);
            s.drawing = false;
            s.pointer = null;
            s.trace = [];
            s.lastHitId = null;
            s.result = null;
            wheel.classList.remove('is-correct', 'is-wrong', 'is-drawing');

            if (!s.items.length) {
                document.getElementById('fcConnectCount').textContent = '0 từ';
                return;
            }

            var entry = s.items[s.index % s.items.length];
            var usedSyllables = {};
            var correctTokens = entry.syllables.map(function (syllable, index) {
                usedSyllables[syllable] = true;
                return { id: 'connect-' + s.index + '-correct-' + index, value: syllable, order: index };
            });
            var distractorPool = shuffle(hangulEntries.reduce(function (all, item) {
                return all.concat(item.syllables || []);
            }, []).filter(function (syllable) {
                if (!syllable || usedSyllables[syllable]) return false;
                usedSyllables[syllable] = true;
                return true;
            }));
            var fallbackPool = ['가', '나', '다', '라', '마', '바', '사', '아', '자', '차', '카', '타', '파', '하'].filter(function (syllable) {
                return !usedSyllables[syllable];
            });
            var connectLevel = 1 + Math.floor(s.completed / 3);
            var distractorCount = Math.min(6, 3 + Math.floor(s.completed / 2));
            var distractors = distractorPool.concat(fallbackPool).slice(0, distractorCount).map(function (syllable, index) {
                return { id: 'connect-' + s.index + '-noise-' + index, value: syllable, order: -1 };
            });

            s.locked = false;
            s.selection = [];
            s.tokens = shuffle(correctTokens.concat(distractors));
            s.coordinates = {};
            wheel.classList.toggle('is-dense', s.tokens.length > 8);
            document.getElementById('fcConnectClue').textContent = gameClue(entry.clue);
            document.getElementById('fcConnectCount').textContent = s.completed + ' từ';
            document.getElementById('fcConnectLevel').textContent = connectLevel;
            document.getElementById('fcConnectCombo').textContent = s.combo;
            document.getElementById('fcConnectEnergy').style.width = Math.min(100, s.combo * 20) + '%';
            feedback('fcConnectFeedback', '', null);

            function updateConnectHud() {
                document.getElementById('fcConnectLevel').textContent = 1 + Math.floor(s.completed / 3);
                document.getElementById('fcConnectCombo').textContent = s.combo;
                document.getElementById('fcConnectEnergy').style.width = Math.min(100, s.combo * 20) + '%';
            }

            function burstConnect(correct) {
                var effects = document.getElementById('fcConnectEffects');
                if (!effects) return;
                effects.textContent = '';
                for (var i = 0; i < (correct ? 18 : 8); i++) {
                    var spark = document.createElement('i');
                    var angle = Math.PI * 2 * i / (correct ? 18 : 8) + Math.random() * .25;
                    var distance = 48 + Math.random() * 72;
                    spark.className = 'fc-connect-spark ' + (correct ? 'is-good' : 'is-bad');
                    spark.style.setProperty('--spark-x', (Math.cos(angle) * distance).toFixed(1) + 'px');
                    spark.style.setProperty('--spark-y', (Math.sin(angle) * distance).toFixed(1) + 'px');
                    spark.style.setProperty('--spark-delay', (Math.random() * 70).toFixed(0) + 'ms');
                    effects.appendChild(spark);
                }
                window.setTimeout(function () { effects.textContent = ''; }, 720);
                if (navigator.vibrate) navigator.vibrate(correct ? [18, 24, 35] : 28);
            }

            function selectionHas(token) {
                return s.selection.some(function (item) { return item.id === token.id; });
            }

            function coordinatePoint(token) {
                return s.coordinates[token.id];
            }

            function pointFromEvent(event) {
                var rect = wheel.getBoundingClientRect();
                return {
                    x: Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100)),
                    y: Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100))
                };
            }

            function pointDistance(a, b) {
                var dx = a.x - b.x;
                var dy = a.y - b.y;
                return Math.sqrt(dx * dx + dy * dy);
            }

            function appendTrace(point) {
                var last = s.trace[s.trace.length - 1];
                if (!last || pointDistance(last, point) >= 0.35) s.trace.push(point);
            }

            function closestToken(point) {
                var closest = null;
                var bestDistance = Infinity;
                var hitRadius = s.tokens.length > 8 ? 8.6 : 10.1;
                s.tokens.forEach(function (token) {
                    var position = coordinatePoint(token);
                    var distance = pointDistance(position, point);
                    if (distance < bestDistance) {
                        closest = token;
                        bestDistance = distance;
                    }
                });
                return bestDistance <= hitRadius ? closest : null;
            }

            function createStroke(className, points) {
                if (points.length < 2) return;
                var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                var lastPoint = points[0];
                var d = 'M ' + lastPoint.x.toFixed(2) + ' ' + lastPoint.y.toFixed(2);
                for (var i = 1; i < points.length - 1; i++) {
                    var point = points[i];
                    var next = points[i + 1];
                    d += ' Q ' + point.x.toFixed(2) + ' ' + point.y.toFixed(2) + ' ' +
                        ((point.x + next.x) / 2).toFixed(2) + ' ' + ((point.y + next.y) / 2).toFixed(2);
                    lastPoint = point;
                }
                var finalPoint = points[points.length - 1];
                d += ' L ' + finalPoint.x.toFixed(2) + ' ' + finalPoint.y.toFixed(2);
                path.setAttribute('class', className);
                path.setAttribute('d', d);
                lines.appendChild(path);
            }

            function paintPath() {
                lines.textContent = '';
                Array.prototype.forEach.call(nodes.children, function (button) {
                    var selected = s.selection.some(function (item) { return item.id === button.dataset.connectId; });
                    // Keep every syllable visually neutral while the pointer is
                    // down.  The freehand stroke is the only live affordance;
                    // correctness styling appears after release.
                    button.classList.toggle('is-selected', selected && !s.drawing && !s.result);
                    button.classList.toggle('is-result-correct', selected && s.result === 'correct');
                    button.classList.toggle('is-result-wrong', selected && s.result === 'wrong');
                });
                createStroke('fc-connect-glow' + (s.result ? ' is-' + s.result : ''), s.trace);
                createStroke('fc-connect-stroke' + (s.result ? ' is-' + s.result : ''), s.trace);
                wheel.classList.toggle('is-drawing', s.drawing);
                answer.textContent = s.drawing ? 'Đang vẽ một nét tự do…' : 'Giữ và vẽ một nét qua các âm tiết';
            }

            function sequenceIsCorrect() {
                return s.selection.length === correctTokens.length && s.selection.every(function (token, index) {
                    return token.id === correctTokens[index].id;
                });
            }

            function resetWithError(message) {
                s.drawing = false;
                s.pointer = null;
                s.result = 'wrong';
                s.combo = 0;
                updateConnectHud();
                burstConnect(false);
                wheel.classList.remove('is-correct');
                wheel.classList.add('is-wrong');
                feedback('fcConnectFeedback', message || 'Nét nối chưa đúng. Hãy thử lại bằng một đường khác.', false);
                paintPath();
                window.clearTimeout(s.resetTimer);
                s.resetTimer = window.setTimeout(function () {
                    s.selection = [];
                    s.trace = [];
                    s.lastHitId = null;
                    s.result = null;
                    wheel.classList.remove('is-wrong');
                    feedback('fcConnectFeedback', '', null);
                    paintPath();
                }, 820);
            }

            function completeStroke() {
                if (s.locked) return;
                s.locked = true;
                s.drawing = false;
                s.pointer = null;
                s.result = 'correct';
                wheel.classList.remove('is-wrong');
                wheel.classList.add('is-correct');
                s.completed++;
                s.combo++;
                setScore(15 + Math.min(15, s.combo * 3));
                updateConnectHud();
                burstConnect(true);
                feedback('fcConnectFeedback', 'Nối đúng: ' + entry.word, true);
                setProgress(s.completed, s.items.length, 'Đã nối ' + s.completed + ' từ');
                paintPath();
                window.setTimeout(function () {
                    wheel.classList.remove('is-correct');
                    s.index = (s.index + 1) % s.items.length;
                    renderConnect();
                }, 900);
            }

            function visitToken(token) {
                if (!token || selectionHas(token)) return;
                s.selection.push(token);
                s.lastHitId = token.id;
            }

            function finishStroke() {
                if (s.locked) return;
                if (sequenceIsCorrect()) completeStroke();
                else resetWithError();
            }

            function startStroke(event) {
                if (s.locked || event.button > 0) return;
                event.preventDefault();
                window.clearTimeout(s.resetTimer);
                s.drawing = true;
                s.pointer = pointFromEvent(event);
                s.selection = [];
                s.trace = [];
                s.lastHitId = null;
                s.result = null;
                wheel.classList.remove('is-wrong', 'is-correct');
                try { wheel.setPointerCapture(event.pointerId); } catch (ignore) { }
                appendTrace(s.pointer);
                visitToken(closestToken(s.pointer));
                paintPath();
            }

            function moveStroke(event) {
                if (!s.drawing || s.locked) return;
                event.preventDefault();
                s.pointer = pointFromEvent(event);
                appendTrace(s.pointer);
                visitToken(closestToken(s.pointer));
                paintPath();
            }

            function endStroke(event) {
                if (!s.drawing) return;
                event.preventDefault();
                s.pointer = pointFromEvent(event);
                appendTrace(s.pointer);
                visitToken(closestToken(s.pointer));
                s.drawing = false;
                try { if (wheel.hasPointerCapture(event.pointerId)) wheel.releasePointerCapture(event.pointerId); } catch (ignore) { }
                finishStroke();
            }

            function resetStroke() {
                if (s.locked) return;
                window.clearTimeout(s.resetTimer);
                s.selection = [];
                s.trace = [];
                s.drawing = false;
                s.pointer = null;
                s.result = null;
                s.lastHitId = null;
                wheel.classList.remove('is-wrong', 'is-correct');
                feedback('fcConnectFeedback', '', null);
                paintPath();
            }

            function renderNodes() {
                var radius = s.tokens.length > 9 ? 38 : 35;
                var rotation = ((s.index + 1) * 0.43) % (Math.PI * 2);
                s.tokens.forEach(function (token, index) {
                    var angle = rotation + (Math.PI * 2 * index / s.tokens.length) - Math.PI / 2;
                    var x = 50 + Math.cos(angle) * radius;
                    var y = 50 + Math.sin(angle) * radius;
                    var button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'fc-connect-node';
                    button.dataset.connectId = token.id;
                    button.style.left = x + '%';
                    button.style.top = y + '%';
                    button.setAttribute('aria-label', 'Âm tiết ' + token.value);
                    button.textContent = token.value;
                    button.addEventListener('keydown', function (event) {
                        if (event.key !== 'Enter' && event.key !== ' ') return;
                        event.preventDefault();
                        if (s.locked) return;
                        var point = coordinatePoint(token);
                        if (!s.trace.length) s.trace.push(point);
                        else s.trace.push(point);
                        visitToken(token);
                        if (s.selection.length >= correctTokens.length) finishStroke();
                        else paintPath();
                    });
                    nodes.appendChild(button);
                    s.coordinates[token.id] = { x: x, y: y };
                });
            }

            wheel.onpointerdown = startStroke;
            wheel.onpointermove = moveStroke;
            wheel.onpointerup = endStroke;
            wheel.onpointercancel = function (event) {
                if (!s.drawing) return;
                s.drawing = false;
                s.pointer = null;
                try { if (wheel.hasPointerCapture(event.pointerId)) wheel.releasePointerCapture(event.pointerId); } catch (ignore) { }
                resetStroke();
            };
            document.getElementById('fcConnectReset').onclick = resetStroke;
            renderNodes();
            paintPath();
        }

        function seedBlastAmbient() {
            var ambient = document.getElementById('fcBlastAmbient');
            if (!ambient || ambient.children.length) return;
            for (var i = 0; i < 54; i++) {
                var star = document.createElement('i');
                star.style.setProperty('--x', Math.random() * 100 + '%');
                star.style.setProperty('--y', Math.random() * 100 + '%');
                star.style.setProperty('--size', (1 + Math.random() * 4) + 'px');
                star.style.setProperty('--delay', (Math.random() * -7) + 's');
                ambient.appendChild(star);
            }
        }

        function updateBlastTimer(seconds, fraction) {
            var timer = document.getElementById('fcBlastTimer');
            var fill = document.getElementById('fcBlastTimeFill');
            if (timer) timer.textContent = String(Math.max(0, Math.ceil(seconds)));
            if (fill) fill.style.transform = 'scaleX(' + Math.max(0, Math.min(1, fraction)) + ')';
        }

        function spawnBlastParticles(layer, x, y, correct) {
            var ring = document.createElement('i');
            ring.className = 'fc-blast-impact-ring ' + (correct ? 'is-good' : 'is-bad');
            ring.style.left = x + 'px';
            ring.style.top = y + 'px';
            layer.appendChild(ring);
            window.setTimeout(function () { ring.remove(); }, 780);

            var count = correct ? 22 : 11;
            for (var i = 0; i < count; i++) {
                var angle = Math.random() * Math.PI * 2;
                var distance = (correct ? 42 : 26) + Math.random() * (correct ? 72 : 40);
                var particle = document.createElement('i');
                particle.className = 'fc-blast-particle ' + (correct ? 'is-good' : 'is-bad');
                particle.style.left = x + 'px';
                particle.style.top = y + 'px';
                particle.style.setProperty('--dx', (Math.cos(angle) * distance) + 'px');
                particle.style.setProperty('--dy', (Math.sin(angle) * distance) + 'px');
                particle.style.setProperty('--rotate', (Math.random() * 280 - 140) + 'deg');
                layer.appendChild(particle);
                window.setTimeout(function (node) { node.remove(); }, 900, particle);
            }
        }

        function fireBlast(target, correct, done) {
            var stage = document.getElementById('fcBlastStage');
            var layer = document.getElementById('fcBlastProjectiles');
            var ship = document.getElementById('fcBlastShip');
            if (!stage || !layer || !ship) { done(); return; }
            var stageRect = stage.getBoundingClientRect();
            var shipRect = ship.getBoundingClientRect();
            var asteroid = target.querySelector('.fc-blast-asteroid') || target;
            var targetRect = asteroid.getBoundingClientRect();
            var startX = shipRect.left + shipRect.width / 2 - stageRect.left;
            var startY = shipRect.top + 10 - stageRect.top;
            var endX = targetRect.left + targetRect.width / 2 - stageRect.left;
            var endY = targetRect.top + targetRect.height / 2 - stageRect.top;
            var deltaX = endX - startX;
            var deltaY = endY - startY;
            var distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            var angle = Math.atan2(deltaY, deltaX) * 180 / Math.PI;
            var laser = document.createElement('i');
            laser.className = 'fc-blast-laser ' + (correct ? 'is-good' : 'is-bad');
            laser.style.left = startX + 'px';
            laser.style.top = startY + 'px';
            laser.style.transform = 'translateY(-50%) rotate(' + angle + 'deg)';
            layer.appendChild(laser);
            var projectile = document.createElement('i');
            projectile.className = 'fc-blast-projectile ' + (correct ? 'is-good' : 'is-bad');
            projectile.style.left = startX + 'px';
            projectile.style.top = startY + 'px';
            layer.appendChild(projectile);
            ship.classList.add('is-firing');

            laser.animate([
                { width: '0px', opacity: 0 },
                { width: distance + 'px', opacity: 1, offset: .28 },
                { width: distance + 'px', opacity: 0, offset: 1 }
            ], { duration: 520, easing: 'cubic-bezier(.14,.8,.24,1)' });
            var animation = projectile.animate([
                { transform: 'translate(0,0) rotate(' + angle + 'deg) scale(.55)', opacity: .35 },
                { transform: 'translate(' + deltaX + 'px,' + deltaY + 'px) rotate(' + angle + 'deg) scale(1.15)', opacity: 1 }
            ], { duration: 520, easing: 'cubic-bezier(.14,.8,.24,1)' });
            animation.onfinish = function () {
                projectile.remove();
                laser.remove();
                ship.classList.remove('is-firing');
                spawnBlastParticles(layer, endX, endY, correct);
                done();
            };
        }

        function blastDifficulty() {
            var s = state.blast;
            // Two successful shots earn a new level.  Each level adds one
            // distracting entity until the playfield is comfortably busy.
            var level = 1 + Math.floor(s.hits / 2);
            var targetCount = Math.min(cards.length, Math.min(6, level + 2));
            return { level: level, targetCount: Math.max(1, targetCount), progress: s.hits % 2 };
        }

        function updateBlastHud() {
            var s = state.blast;
            var dots = document.getElementById('fcBlastComboDots');
            if (dots) {
                dots.textContent = '';
                for (var d = 0; d < 5; d++) {
                    var dot = document.createElement('i');
                    dot.className = d < Math.min(s.combo, 5) ? 'is-lit' : '';
                    dots.appendChild(dot);
                }
            }
            var levelLabel = document.querySelector('.fc-blast-level > span');
            var level = document.getElementById('fcBlastLevelBar');
            var difficulty = blastDifficulty();
            if (levelLabel) levelLabel.textContent = 'Cấp ' + difficulty.level + ' · ' + difficulty.targetCount + ' mục tiêu';
            if (level) level.style.width = (difficulty.progress / 2 * 100) + '%';
        }

        function blastAnchors(count) {
            var compact = window.matchMedia && window.matchMedia('(max-width: 760px)').matches;
            var anchors = compact ? [
                { x: 21, y: 18, dx: 15, dy: 17 }, { x: 75, y: 19, dx: -16, dy: 19 },
                { x: 24, y: 49, dx: 18, dy: -15 }, { x: 74, y: 49, dx: -17, dy: 15 },
                { x: 21, y: 78, dx: 16, dy: -13 }, { x: 76, y: 78, dx: -17, dy: -16 }
            ] : [
                { x: 15, y: 51, dx: 26, dy: -19 }, { x: 31, y: 24, dx: -21, dy: 24 },
                { x: 49, y: 47, dx: 24, dy: -20 }, { x: 69, y: 24, dx: -25, dy: 21 },
                { x: 86, y: 55, dx: -24, dy: -17 }, { x: 38, y: 76, dx: 21, dy: -19 }
            ];
            return shuffle(anchors).slice(0, count);
        }

        function blastChoices(card, targetCount) {
            var choices = [card];
            var seen = {};
            seen[norm(card.front)] = true;
            shuffle(cards).some(function (candidate) {
                var key = norm(candidate.front);
                if (String(candidate.id) !== String(card.id) && key && !seen[key]) {
                    choices.push(candidate);
                    seen[key] = true;
                }
                return choices.length >= Math.min(targetCount, cards.length);
            });
            return shuffle(choices);
        }

        function blastTargets() {
            var s = state.blast;
            if (!s.items.length || !s.running) return;
            var card = s.items[s.index % s.items.length];
            var hostTargets = document.getElementById('fcBlastTargets');
            var round = ++s.roundId;
            s.shotLocked = false;
            document.getElementById('fcBlastPrompt').textContent = gameClue(card.back || card.front);
            hostTargets.textContent = '';
            s.entities = [];
            var desiredCount = blastDifficulty().targetCount;
            var difficulty = blastDifficulty();
            var choices = blastChoices(card, desiredCount);
            var anchors = blastAnchors(choices.length);
            choices.forEach(function (choice, i) {
                var anchor = anchors[i];
                var target = document.createElement('button');
                var asteroid = document.createElement('span');
                var label = document.createElement('span');
                target.type = 'button';
                target.className = 'fc-blast-target fc-blast-target-' + (i % 4);
                if (choices.length >= 5) target.classList.add('is-crowded');
                target.style.left = anchor.x + '%';
                target.style.top = anchor.y + '%';
                var orbitScale = Math.min(1.75, 1 + (difficulty.level - 1) * .14);
                target.style.setProperty('--scale', (.9 + Math.random() * .16).toFixed(2));
                target.setAttribute('aria-label', 'Bắn vào ' + choice.front);
                asteroid.className = 'fc-blast-asteroid';
                label.className = 'fc-blast-target-label';
                label.textContent = choice.front;
                asteroid.appendChild(label);
                target.appendChild(asteroid);
                target.addEventListener('click', function () {
                    if (!s.running || s.shotLocked || s.roundId !== round) return;
                    var correct = String(choice.id) === String(card.id);
                    s.shotLocked = true;
                    target.classList.add('is-targeted');
                    fireBlast(target, correct, function () {
                        target.classList.remove('is-targeted');
                        if (!s.running || s.roundId !== round) return;
                        if (!correct) {
                            s.combo = 0;
                            target.classList.add('is-wrong');
                            feedback('fcBlastFeedback', 'Sai mục tiêu — thực thể bị lệch quỹ đạo.', false);
                            updateBlastHud();
                            s.roundTimeout = window.setTimeout(function () {
                                if (!s.running || s.roundId !== round) return;
                                target.classList.remove('is-wrong');
                                s.shotLocked = false;
                            }, 820);
                            return;
                        }
                        target.classList.add('is-disintegrating');
                        s.hits++;
                        s.combo++;
                        setScore(20 + Math.min(20, s.combo * 2));
                        feedback('fcBlastFeedback', 'Trúng đích! Thực thể đã phân rã.', true);
                        setProgress(Math.min(s.hits, s.items.length), s.items.length, 'Đã bắn trúng ' + s.hits + ' từ');
                        updateBlastHud();
                        s.roundTimeout = window.setTimeout(function () {
                            if (!s.running || s.roundId !== round) return;
                            s.index++;
                            blastTargets();
                        }, 820);
                    });
                });
                hostTargets.appendChild(target);
                var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                var motionScale = reducedMotion ? .42 : 1;
                s.entities.push({
                    element: target,
                    phase: Math.random() * Math.PI * 2,
                    speed: (.62 + Math.random() * .28) * (1 + (difficulty.level - 1) * .08),
                    amplitudeX: Math.abs(anchor.dx) * orbitScale * motionScale,
                    amplitudeY: Math.abs(anchor.dy) * orbitScale * motionScale,
                    spin: 1.4 + Math.random() * 2.2
                });
            });
            updateBlastHud();
        }

        function updateBlastEntities(now) {
            var s = state.blast;
            var time = (now - s.startedAt) / 1000;
            s.entities = s.entities.filter(function (entity) {
                if (!entity.element || !entity.element.isConnected) return false;
                var wave = time * entity.speed + entity.phase;
                var x = Math.sin(wave) * entity.amplitudeX + Math.sin(wave * .43 + entity.phase) * entity.amplitudeX * .24;
                var y = Math.cos(wave * .81) * entity.amplitudeY + Math.sin(wave * .57) * entity.amplitudeY * .2;
                var rotation = Math.sin(wave * .36) * entity.spin;
                entity.element.style.transform = 'translate(-50%,-50%) translate3d(' + x.toFixed(2) + 'px,' + y.toFixed(2) + 'px,0) rotate(' + rotation.toFixed(2) + 'deg)';
                return true;
            });
        }

        function tickBlast(now) {
            var s = state.blast;
            if (!s.running) return;
            var remaining = Math.max(0, s.duration - (now - s.startedAt));
            s.timer = remaining / 1000;
            updateBlastTimer(s.timer, remaining / s.duration);
            updateBlastEntities(now);
            if (!remaining) { finishBlast(); return; }
            s.raf = window.requestAnimationFrame(tickBlast);
        }

        function finishBlast() {
            var s = state.blast;
            if (!s.running) return;
            s.running = false;
            s.shotLocked = false;
            s.entities = [];
            s.roundId++;
            window.cancelAnimationFrame(s.raf);
            window.clearTimeout(s.roundTimeout);
            updateBlastTimer(0, 0);
            document.getElementById('fcBlastOverlay').classList.add('is-visible');
            document.getElementById('fcBlastOverlayTitle').textContent = 'Hoàn thành chuyến bay';
            document.getElementById('fcBlastOverlayText').textContent = 'Bạn bắn trúng ' + s.hits + ' từ · ' + score + ' điểm';
            document.getElementById('fcBlastStart').textContent = 'Chơi lại';
        }

        function startBlast() {
            var s = state.blast;
            var overlay = document.getElementById('fcBlastOverlay');
            if (!cards.length) {
                document.getElementById('fcBlastOverlayTitle').textContent = 'Chưa có thẻ để chơi';
                document.getElementById('fcBlastOverlayText').textContent = 'Hãy thêm ít nhất một thẻ vào bộ trước khi khởi động Blast.';
                return;
            }
            window.cancelAnimationFrame(s.raf);
            window.clearTimeout(s.roundTimeout);
            s.items = shuffle(cards);
            s.index = 0;
            s.timer = 60;
            s.duration = 60000;
            s.startedAt = window.performance.now();
            s.hits = 0;
            s.combo = 0;
            s.running = true;
            s.shotLocked = false;
            s.entities = [];
            s.roundId++;
            document.getElementById('fcBlastProjectiles').textContent = '';
            overlay.classList.remove('is-visible');
            updateBlastTimer(s.timer, 1);
            seedBlastAmbient();
            blastTargets();
            s.raf = window.requestAnimationFrame(tickBlast);
        }

        if (mode === 'learn') renderLearn();
        if (mode === 'test') renderTest();
        if (mode === 'match') {
            var matchState = state.match;
            var maxPairs = Math.max(1, cards.length);
            var customPairs = document.getElementById('fcMatchCustomPairs');
            if (customPairs) {
                customPairs.min = '1';
                customPairs.max = String(maxPairs);
                customPairs.value = String(matchState.batchSize);
            }
            function updateMatchPresetState() {
                document.querySelectorAll('#fcMatchLevel button[data-pairs]').forEach(function (item) {
                    item.classList.toggle('is-active',
                        Number(item.dataset.pairs) === matchState.batchSize);
                });
                if (customPairs) customPairs.value = String(matchState.batchSize);
            }
            document.querySelectorAll('#fcMatchLevel button').forEach(function (button) {
                var value = Number(button.dataset.pairs);
                button.disabled = value > cards.length;
                button.addEventListener('click', function () {
                    matchState.batchSize = Math.min(value, maxPairs);
                    updateMatchPresetState();
                    renderMatch();
                });
            });
            if (customPairs) {
                customPairs.addEventListener('change', function () {
                    var requested = Number(customPairs.value);
                    matchState.batchSize = Math.max(1, Math.min(
                        Number.isFinite(requested) ? Math.round(requested) : 4,
                        maxPairs
                    ));
                    updateMatchPresetState();
                    renderMatch();
                });
                customPairs.addEventListener('keydown', function (event) {
                    if (event.key === 'Enter') {
                        event.preventDefault();
                        customPairs.blur();
                    }
                });
            }
            document.getElementById('fcMatchMedia').addEventListener('change', renderMatch);
            document.getElementById('fcMatchRestart').addEventListener('click', renderMatch);
            renderMatchDeckPicker();
            updateMatchPresetState();
            renderMatch();
        }
        if (mode === 'tiles') {
            renderTiles();
            document.getElementById('fcTilesCheck').addEventListener('keydown', function (event) {
                if (event.key === 'Enter') event.preventDefault();
            });
        }
        if (mode === 'word-search') renderWordSearch();
        if (mode === 'word-connect') renderConnect();
        if (mode === 'blast') {
            seedBlastAmbient();
            document.getElementById('fcBlastOverlay').classList.add('is-visible');
            document.getElementById('fcBlastStart').addEventListener('click', startBlast);
        }
        var audio = document.getElementById('fcGlobalAudio'); if (audio) audio.addEventListener('click', function () { var prompt = document.querySelector('[id$="Prompt"], #fcBlastPrompt'); if (prompt) speak(prompt.textContent); });
        var speakButton = document.querySelector('[data-speak-target]'); if (speakButton) speakButton.addEventListener('click', function () { speak(document.getElementById(speakButton.dataset.speakTarget).textContent); });
        var learnUnknown = document.getElementById('fcLearnUnknown'); if (learnUnknown) learnUnknown.addEventListener('click', skipLearnQuestion);
        var learnContinue = document.getElementById('fcLearnContinue'); if (learnContinue) learnContinue.addEventListener('click', continueLearn);
        var learnRestart = document.getElementById('fcLearnRestart'); if (learnRestart) learnRestart.addEventListener('click', restartLearn);
        var learnSubmit = document.getElementById('fcLearnSubmit'); if (learnSubmit) learnSubmit.addEventListener('click', function () { submitLearnWrite(false); });
        var learnHintButton = document.getElementById('fcLearnHint'); if (learnHintButton) learnHintButton.addEventListener('click', function () {
            var s = state.learn;
            var hintText = document.getElementById('fcLearnHintText');
            hintText.textContent = learnHint(s.items[s.index].front);
            hintText.hidden = false;
        });
        var learnInput = document.getElementById('fcLearnInput'); if (learnInput) {
            learnInput.addEventListener('input', function () { document.getElementById('fcLearnSubmit').disabled = !learnInput.value.trim(); });
            learnInput.addEventListener('keydown', function (event) {
                if (event.key !== 'Enter') return;
                event.preventDefault();
                if (state.learn.locked) continueLearn();
                else if (learnInput.value.trim()) submitLearnWrite(false);
            });
        }
        var testSubmit = document.getElementById('fcTestSubmit'); if (testSubmit) testSubmit.addEventListener('click', submitTest);
        var testRetry = document.getElementById('fcTestRetry'); if (testRetry) testRetry.addEventListener('click', function () {
            state.test.items = shuffle(cards);
            renderTest();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
        var testPrint = document.getElementById('fcPrintTest'); if (testPrint) testPrint.addEventListener('click', function () { window.print(); });
        var testOutlineToggle = document.getElementById('fcTestOutlineToggle');
        var testOutline = document.getElementById('fcTestOutline');
        var testOutlineClose = document.getElementById('fcTestOutlineClose');
        function setTestOutline(open) {
            if (!testOutline || !testOutlineToggle) return;
            testOutline.hidden = !open;
            testOutlineToggle.setAttribute('aria-expanded', String(open));
        }
        if (testOutlineToggle) testOutlineToggle.addEventListener('click', function () { setTestOutline(testOutline.hidden); });
        if (testOutlineClose) testOutlineClose.addEventListener('click', function () { setTestOutline(false); });
        if (testOutline) testOutline.addEventListener('click', function (event) {
            if (event.target.closest('a')) setTestOutline(false);
        });
    });
})();
