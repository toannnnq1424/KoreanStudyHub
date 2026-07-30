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
        if (mode === 'blocks') renderBlocks();
        if (mode === 'blast') { document.getElementById('fcBlastOverlay').classList.add('is-visible'); document.getElementById('fcBlastStart').addEventListener('click', startBlast); }
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
