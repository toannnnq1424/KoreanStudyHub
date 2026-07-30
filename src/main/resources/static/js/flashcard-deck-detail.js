(function () {
    'use strict';
    function ready(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
        else fn();
    }
    ready(function () {
        Array.prototype.forEach.call(document.querySelectorAll('.fc-term-cell img'), function (image) {
            function hideBrokenImage() {
                var button = image.closest('.fc-term-image-button');
                if (button) button.hidden = true;
                else image.hidden = true;
            }
            image.addEventListener('error', hideBrokenImage);
            if (image.complete && image.naturalWidth === 0) hideBrokenImage();
        });
        var imageViewer = document.getElementById('fcImageViewer');
        var imageViewerImage = document.getElementById('fcImageViewerImage');
        var imageViewerClose = document.getElementById('fcImageViewerClose');
        var viewerReturnFocus = null;
        function closeImageViewer() {
            if (!imageViewer || imageViewer.hidden) return;
            imageViewer.hidden = true;
            imageViewerImage.removeAttribute('src');
            document.body.classList.remove('fc-viewer-open');
            if (viewerReturnFocus) viewerReturnFocus.focus();
        }
        Array.prototype.forEach.call(document.querySelectorAll('.fc-term-image-button'), function (button) {
            button.addEventListener('click', function () {
                viewerReturnFocus = button;
                imageViewerImage.src = button.dataset.fullImage;
                imageViewer.hidden = false;
                document.body.classList.add('fc-viewer-open');
                imageViewerClose.focus();
            });
        });
        if (imageViewer) {
            imageViewerClose.addEventListener('click', closeImageViewer);
            imageViewer.addEventListener('click', function (event) {
                if (event.target === imageViewer) closeImageViewer();
            });
            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape' && !imageViewer.hidden) {
                    event.preventDefault();
                    closeImageViewer();
                }
            });
        }
        var cardButton = document.getElementById('fcSetCard');
        if (!cardButton) return;
        var cards = Array.prototype.map.call(document.querySelectorAll('.fc-set-data'), function (node) {
            return {
                id: node.dataset.cardId || '',
                front: node.dataset.front || '',
                back: node.dataset.back || '',
                frontImage: node.dataset.frontImage || '',
                backImage: node.dataset.backImage || ''
            };
        });
        if (!cards.length) return;
        var position = 0;
        var order = cards.map(function (_, index) { return index; });
        var frontText = document.getElementById('fcSetFrontText');
        var backText = document.getElementById('fcSetBackText');
        var frontImage = document.getElementById('fcSetFrontImage');
        var backImage = document.getElementById('fcSetBackImage');
        var posText = document.getElementById('fcSetPos');
        var totalText = document.getElementById('fcSetTotal');
        var prevButton = document.getElementById('fcSetPrev');
        var nextButton = document.getElementById('fcSetNext');
        var shuffleButton = document.getElementById('fcSetShuffle');
        var studySection = document.getElementById('fcSetStudy');
        var completeSection = document.getElementById('fcSetComplete');
        var completeLearn = document.getElementById('fcCompleteLearn');
        var completeBack = document.getElementById('fcCompleteBack');
        var completeReset = document.getElementById('fcCompleteReset');
        var completeTitle = document.getElementById('fcCompleteTitle');
        var completeKnownLabel = document.getElementById('fcCompleteKnownLabel');
        var completeUnknownLabel = document.getElementById('fcCompleteUnknownLabel');
        var completeKnown = document.getElementById('fcCompleteKnown');
        var completeUnknown = document.getElementById('fcCompleteUnknown');
        var completeRing = document.querySelector('.fc-complete-ring-progress');
        var completeCheck = document.getElementById('fcCompleteCheck');
        var completePercent = document.getElementById('fcCompletePercent');
        var trackingToggle = document.getElementById('fcSetTrackingToggle');
        var unknownButton = document.getElementById('fcSetUnknown');
        var knownButton = document.getElementById('fcSetKnown');
        var autoPlayButton = document.getElementById('fcSetAutoPlay');
        var controls = document.querySelector('.fc-set-controls');
        var ratings = cards.map(function () { return null; });
        var trackingEnabled = false;
        var autoPlaying = false;
        var autoTimer = null;
        var autoStepDelay = 2500;
        var redirectTimer = null;
        var completionActive = false;
        var autoRedirectDelay = 4200;
        var reviewStorageKey = 'ksh:flashcards:' + completeSection.dataset.deckId + ':unknown';
        window.sessionStorage.removeItem(reviewStorageKey);
        function showImage(image, url) {
            image.hidden = true;
            image.onload = function () { image.hidden = false; };
            image.onerror = function () {
                image.hidden = true;
                image.removeAttribute('src');
            };
            if (url) image.src = url;
            else image.removeAttribute('src');
        }
        function render() {
            var card = cards[order[position]];
            frontText.textContent = card.front;
            backText.textContent = card.back;
            showImage(frontImage, card.frontImage);
            showImage(backImage, card.backImage);
            posText.textContent = String(position + 1);
            totalText.textContent = String(order.length);
            cardButton.classList.remove('is-flipped');
            prevButton.disabled = position === 0;
            nextButton.classList.toggle('is-finish', position === order.length - 1);
            nextButton.setAttribute('aria-label', position === order.length - 1 ? 'Hoàn thành ôn tập' : 'Thẻ tiếp theo');
            updateRatingButtons();
            if (autoPlaying) scheduleAutoStep();
        }
        function updateRatingButtons() {
            var rating = ratings[order[position]];
            unknownButton.classList.toggle('is-active', rating === 'unknown');
            knownButton.classList.toggle('is-active', rating === 'known');
            unknownButton.setAttribute('aria-pressed', rating === 'unknown' ? 'true' : 'false');
            knownButton.setAttribute('aria-pressed', rating === 'known' ? 'true' : 'false');
        }
        function markCurrent(rating) {
            ratings[order[position]] = rating;
            updateRatingButtons();
        }
        function rateAndContinue(rating) {
            markCurrent(rating);
            window.setTimeout(goNext, 160);
        }
        function setTracking(enabled) {
            trackingEnabled = enabled;
            trackingToggle.setAttribute('aria-checked', enabled ? 'true' : 'false');
            trackingToggle.classList.toggle('is-active', enabled);
            controls.classList.toggle('is-tracking', enabled);
            unknownButton.hidden = !enabled;
            knownButton.hidden = !enabled;
            prevButton.hidden = enabled;
            nextButton.hidden = enabled;
        }
        function refreshAutoButton() {
            autoPlayButton.classList.toggle('is-playing', autoPlaying);
            autoPlayButton.setAttribute('aria-label', autoPlaying ? 'Tạm dừng tự lật' : 'Bắt đầu tự lật');
        }
        function restartAutoRing() {
            autoPlayButton.classList.remove('is-timing');
            void autoPlayButton.offsetWidth;
            if (autoPlaying) autoPlayButton.classList.add('is-timing');
        }
        function clearAutoTimer() {
            if (autoTimer) window.clearTimeout(autoTimer);
            autoTimer = null;
            autoPlayButton.classList.remove('is-timing');
        }
        function stopAutoPlay() {
            autoPlaying = false;
            clearAutoTimer();
            refreshAutoButton();
        }
        function scheduleAutoStep() {
            clearAutoTimer();
            if (!autoPlaying || completionActive) return;
            restartAutoRing();
            autoTimer = window.setTimeout(function () {
                if (!cardButton.classList.contains('is-flipped')) {
                    cardButton.classList.add('is-flipped');
                    scheduleAutoStep();
                    return;
                }
                if (position < order.length - 1) {
                    position += 1;
                    render();
                } else {
                    enterCompletion();
                }
            }, autoStepDelay);
        }
        function toggleAutoPlay() {
            autoPlaying = !autoPlaying;
            refreshAutoButton();
            if (autoPlaying) scheduleAutoStep();
            else clearAutoTimer();
        }
        function prepareLearnSelection() {
            var knownCount = trackingEnabled
                ? ratings.filter(function (value) { return value === 'known'; }).length
                : 0;
            var unknownCards = trackingEnabled
                ? cards.filter(function (_, index) { return ratings[index] !== 'known'; })
                : cards.slice();
            var percent = trackingEnabled ? Math.round(knownCount / cards.length * 100) : 100;
            completeKnownLabel.textContent = trackingEnabled ? 'Đã biết' : 'Hoàn thành';
            completeUnknownLabel.textContent = trackingEnabled ? 'Đang học' : 'Còn lại';
            completeKnown.textContent = String(trackingEnabled ? knownCount : cards.length);
            completeUnknown.textContent = String(trackingEnabled ? unknownCards.length : 0);
            completeRing.style.setProperty('--fc-ring-target', String(251.33 * (1 - percent / 100)));
            completePercent.textContent = percent + '%';
            completePercent.hidden = percent === 100;
            completeCheck.hidden = percent !== 100;
            completeTitle.textContent = trackingEnabled && unknownCards.length
                ? 'Bạn đang làm rất tuyệt! Hãy tiếp tục để tăng cường tự tin.'
                : 'Chúc mừng! Bạn đã ôn tập tất cả các thẻ.';
            if (trackingEnabled) {
                window.sessionStorage.setItem(reviewStorageKey, JSON.stringify(unknownCards.map(function (card) {
                    return String(card.id);
                })));
                completeLearn.href = completeSection.dataset.learnUrl;
            } else {
                window.sessionStorage.removeItem(reviewStorageKey);
                completeLearn.href = completeSection.dataset.learnUrl.split('?')[0];
            }
        }
        function stopAutoRedirect() {
            if (redirectTimer) window.clearTimeout(redirectTimer);
            redirectTimer = null;
        }
        function leaveCompletion() {
            stopAutoRedirect();
            completionActive = false;
            completeSection.hidden = true;
            completeSection.classList.remove('is-active');
            studySection.hidden = false;
            window.requestAnimationFrame(function () { cardButton.focus(); });
        }
        function enterCompletion() {
            if (completionActive) return;
            stopAutoPlay();
            prepareLearnSelection();
            completionActive = true;
            studySection.hidden = true;
            completeSection.hidden = false;
            completeSection.classList.remove('is-active');
            window.requestAnimationFrame(function () {
                window.requestAnimationFrame(function () {
                    completeSection.classList.add('is-active');
                    completeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
                });
            });
            stopAutoRedirect();
            redirectTimer = window.setTimeout(function () {
                window.location.assign(completeLearn.href);
            }, autoRedirectDelay);
        }
        function goNext() {
            if (position < order.length - 1) {
                position += 1;
                render();
                return;
            }
            enterCompletion();
        }
        function goPrevious() {
            if (position > 0) {
                position -= 1;
                render();
            }
        }
        cardButton.addEventListener('click', function () {
            cardButton.classList.toggle('is-flipped');
            if (autoPlaying) scheduleAutoStep();
        });
        prevButton.addEventListener('click', goPrevious);
        nextButton.addEventListener('click', goNext);
        trackingToggle.addEventListener('click', function () { setTracking(!trackingEnabled); });
        unknownButton.addEventListener('click', function () { rateAndContinue('unknown'); });
        knownButton.addEventListener('click', function () { rateAndContinue('known'); });
        autoPlayButton.addEventListener('click', toggleAutoPlay);
        shuffleButton.addEventListener('click', function () {
            for (var i = order.length - 1; i > 0; i -= 1) {
                var j = Math.floor(Math.random() * (i + 1));
                var value = order[i]; order[i] = order[j]; order[j] = value;
            }
            position = 0;
            render();
        });
        completeBack.addEventListener('click', leaveCompletion);
        completeReset.addEventListener('click', function () {
            position = 0;
            order = cards.map(function (_, index) { return index; });
            ratings = cards.map(function () { return null; });
            leaveCompletion();
            render();
        });
        completeLearn.addEventListener('click', stopAutoRedirect);
        document.addEventListener('keydown', function (event) {
            var target = event.target;
            if (target && (target.matches('input, textarea, select') || target.isContentEditable)) return;
            if (completionActive) {
                if (event.key === 'ArrowLeft' || event.key === 'Escape') {
                    event.preventDefault();
                    leaveCompletion();
                }
                return;
            }
            if (event.key === 'ArrowRight') {
                event.preventDefault();
                goNext();
            } else if (event.key === 'ArrowLeft') {
                event.preventDefault();
                goPrevious();
            }
        });
        window.addEventListener('pageshow', function (event) {
            if (!event.persisted) return;
            stopAutoRedirect();
            stopAutoPlay();
            completionActive = false;
            position = 0;
            order = cards.map(function (_, index) { return index; });
            ratings = cards.map(function () { return null; });
            completeSection.hidden = true;
            completeSection.classList.remove('is-active');
            studySection.hidden = false;
            window.sessionStorage.removeItem(reviewStorageKey);
            setTracking(false);
            render();
        });
        setTracking(false);
        refreshAutoButton();
        render();
    });
})();
