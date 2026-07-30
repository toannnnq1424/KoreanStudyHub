/* Flip study mode: tactile 3D gestures, layered recall and session-only progress. */
(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
        else fn();
    }

    function shuffleArray(items) {
        for (var i = items.length - 1; i > 0; i--) {
            var j = Math.floor(Math.random() * (i + 1));
            var value = items[i]; items[i] = items[j]; items[j] = value;
        }
        return items;
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    ready(function () {
        var host = document.getElementById('fcStudy');
        if (!host) return;

        var cards;
        try { cards = JSON.parse(host.dataset.cards || '[]'); } catch (error) { cards = []; }
        var viewer = document.getElementById('fcViewer');
        var empty = document.getElementById('fcEmpty');
        var complete = document.getElementById('fcFlipComplete');
        if (!cards.length) {
            if (empty) empty.hidden = false;
            if (viewer) viewer.hidden = true;
            return;
        }

        empty.hidden = true;
        viewer.hidden = false;

        var flipStage = document.getElementById('fcFlipStage');
        var flipCard = document.getElementById('fcFlipCard');
        var flipInner = flipCard.querySelector('.fc-flip-inner');
        var previewLayer = document.getElementById('fcFlipPreview');
        var previewLabel = document.getElementById('fcFlipPreviewLabel');
        var previewContent = document.getElementById('fcFlipPreviewContent');
        var previewImage = document.getElementById('fcFlipPreviewImage');
        var previewText = document.getElementById('fcFlipPreviewText');
        var frontText = document.getElementById('fcFrontText');
        var backText = document.getElementById('fcBackText');
        var frontImage = document.getElementById('fcFrontImage');
        var backImage = document.getElementById('fcBackImage');
        var frontFace = frontText.closest('.fc-flip-face');
        var backFace = backText.closest('.fc-flip-face');
        var focusReveal = document.getElementById('fcFocusReveal');
        var classifyModeButton = document.getElementById('fcClassifyMode');
        var classifyModeText = document.getElementById('fcClassifyModeText');
        var focusModeButton = document.getElementById('fcFocusMode');
        var gestureHint = document.getElementById('fcGestureHint');
        var posEl = document.getElementById('fcPos');
        var totalEl = document.getElementById('fcTotal');
        var seenCount = document.getElementById('fcSeenCount');
        var remainingCount = document.getElementById('fcRemainingCount');
        var progress = document.getElementById('fcFlipProgress');
        var knownBadge = document.getElementById('fcSwipeKnown');
        var unknownBadge = document.getElementById('fcSwipeUnknown');
        var knownBadgeText = document.getElementById('fcSwipeKnownText');
        var unknownBadgeText = document.getElementById('fcSwipeUnknownText');
        var knownTotal = document.getElementById('fcKnownTotal');
        var unknownTotal = document.getElementById('fcUnknownTotal');
        var learnLink = document.getElementById('fcFlipLearn');
        var deckId = host.dataset.deckId || 'unknown';
        var unknownStorageKey = 'ksh:flashcards:' + deckId + ':unknown';

        var order = cards.map(function (_, index) { return index; });
        var pos = 0;
        var ratings = {};
        var classifyMode = false;
        var focusMode = false;
        var focusRevealed = true;
        var drag = {
            active: false,
            pointerId: null,
            startX: 0,
            startY: 0,
            x: 0,
            y: 0,
            axis: null,
            lastX: 0,
            lastY: 0,
            lastTime: 0,
            velocityX: 0,
            velocityY: 0
        };
        var transitionLocked = false;
        var enterFrom = '';
        var previewOffset = 0;
        // The first card must already be at rest when the room opens. An entry
        // animation here made a freshly loaded session look like it had jumped.
        var skipEntryAnimation = true;

        function setImage(image, url, alt) {
            if (url) {
                image.src = url;
                image.alt = alt || '';
                image.hidden = false;
            } else {
                image.removeAttribute('src');
                image.alt = '';
                image.hidden = true;
            }
        }

        function currentCard() {
            return cards[order[pos]];
        }

        function cardAtOffset(offset) {
            var next = (pos + offset + order.length) % order.length;
            return cards[order[next]];
        }

        function nextUnratedCard() {
            var next = pos;
            do { next = (next + 1) % order.length; }
            while (ratings[String(cards[order[next]].id)] && next !== pos);
            return cards[order[next]];
        }

        function showDragPreview(offset, strength) {
            var nextOffset = classifyMode ? 1 : offset;
            var card = classifyMode ? nextUnratedCard() : cardAtOffset(nextOffset);
            if (previewOffset !== nextOffset) {
                previewOffset = nextOffset;
                previewLabel.textContent = classifyMode
                    ? 'THẺ TIẾP THEO'
                    : (nextOffset > 0 ? 'THẺ KẾ TIẾP' : 'THẺ TRƯỚC');
                previewText.textContent = card.front || '';
                setImage(previewImage, card.frontImage, card.front);
                previewContent.classList.toggle('has-media', Boolean(card.frontImage));
            }
            previewLayer.style.setProperty('--preview-reveal', String(clamp((strength - .05) / .65, 0, 1)));
        }

        function ratingCounts() {
            var values = Object.keys(ratings).map(function (key) { return ratings[key]; });
            return {
                known: values.filter(function (value) { return value === 'known'; }).length,
                unknown: values.filter(function (value) { return value === 'unknown'; }).length
            };
        }

        function saveUnknownCards() {
            var ids = cards.filter(function (card) {
                return ratings[String(card.id)] === 'unknown';
            }).map(function (card) { return card.id; });
            if (ids.length) window.sessionStorage.setItem(unknownStorageKey, JSON.stringify(ids));
            else window.sessionStorage.removeItem(unknownStorageKey);
            learnLink.hidden = ids.length === 0;
        }

        function clearGestureStyles() {
            flipCard.style.removeProperty('transform');
            flipCard.style.removeProperty('transition');
            flipCard.style.removeProperty('opacity');
            flipInner.style.removeProperty('transform');
            flipInner.style.removeProperty('transition');
            knownBadge.style.opacity = '0';
            unknownBadge.style.opacity = '0';
            previewLayer.style.setProperty('--preview-reveal', '0');
            previewOffset = 0;
            flipStage.classList.remove('is-dragging', 'is-dragging-x', 'is-dragging-y');
        }

        function resetDragState() {
            drag.active = false;
            drag.pointerId = null;
            drag.x = 0;
            drag.y = 0;
            drag.axis = null;
            drag.velocityX = 0;
            drag.velocityY = 0;
        }

        function updateProgress() {
            var rated = Object.keys(ratings).length;
            seenCount.textContent = String(rated);
            remainingCount.textContent = String(Math.max(cards.length - rated, 0));
            progress.style.width = (rated / cards.length * 100) + '%';
            posEl.textContent = String(pos + 1);
            totalEl.textContent = String(cards.length);
        }

        function setFocusState(card) {
            var canLayer = focusMode && Boolean(card.frontImage);
            focusRevealed = !canLayer;
            viewer.classList.toggle('is-focus-mode', focusMode);
            flipCard.classList.toggle('is-focus-active', canLayer);
            flipCard.classList.toggle('is-focus-revealed', focusRevealed);
            focusReveal.hidden = !canLayer;
        }

        function updateModeUi() {
            classifyModeButton.setAttribute('aria-pressed', String(classifyMode));
            focusModeButton.setAttribute('aria-pressed', String(focusMode));
            classifyModeButton.classList.toggle('is-active', classifyMode);
            focusModeButton.classList.toggle('is-active', focusMode);
            classifyModeText.textContent = classifyMode ? 'Trái: chưa biết · phải: đã biết' : 'Trái/phải qua thẻ khác';
            unknownBadgeText.textContent = classifyMode ? 'Chưa biết' : 'Thẻ sau';
            knownBadgeText.textContent = classifyMode ? 'Đã biết' : 'Thẻ trước';
            gestureHint.innerHTML = classifyMode
                ? '<kbd>Space</kbd> lật · vuốt dọc để lật · vuốt ngang để phân loại'
                : '<kbd>Space</kbd> lật · vuốt dọc để lật · vuốt ngang để chuyển thẻ';
            flipStage.classList.toggle('is-classify-mode', classifyMode);
        }

        function render() {
            var card = currentCard();
            frontText.textContent = card.front || '';
            backText.textContent = card.back || '';
            setImage(frontImage, card.frontImage, card.front);
            setImage(backImage, card.backImage, card.back);
            frontFace.classList.toggle('has-media', Boolean(card.frontImage));
            backFace.classList.toggle('has-media', Boolean(card.backImage));
            flipCard.classList.remove(
                'is-flipped',
                'is-exiting-left',
                'is-exiting-right',
                'is-entering',
                'is-entering-left',
                'is-entering-right',
                'is-focus-active',
                'is-focus-revealed'
            );
            clearGestureStyles();
            resetDragState();
            setFocusState(card);
            updateProgress();
            if (skipEntryAnimation) {
                skipEntryAnimation = false;
                enterFrom = '';
                return;
            }
            window.requestAnimationFrame(function () {
                var entryClass = enterFrom === 'left'
                    ? 'is-entering-left'
                    : (enterFrom === 'right' ? 'is-entering-right' : 'is-entering');
                enterFrom = '';
                flipCard.classList.add(entryClass);
                window.setTimeout(function () { flipCard.classList.remove(entryClass); }, 340);
            });
        }

        function revealFocus() {
            if (!flipCard.classList.contains('is-focus-active') || focusRevealed) return false;
            focusRevealed = true;
            flipCard.classList.add('is-focus-revealed');
            focusReveal.hidden = true;
            return true;
        }

        function flip() {
            if (transitionLocked || drag.active) return;
            if (!flipCard.classList.contains('is-flipped') && revealFocus()) return;
            flipCard.classList.toggle('is-flipped');
        }

        function slideTo(delta, direction, wrap, fromGesture) {
            if (transitionLocked) return;
            var next = pos + delta;
            if (wrap) next = (next + order.length) % order.length;
            if (next < 0 || next >= order.length || next === pos) {
                flipStage.classList.add('is-edge-bump');
                window.setTimeout(function () { flipStage.classList.remove('is-edge-bump'); }, 260);
                return;
            }
            transitionLocked = true;
            flipStage.classList.remove('is-dragging', 'is-dragging-x', 'is-dragging-y');
            knownBadge.style.opacity = '0';
            unknownBadge.style.opacity = '0';
            flipCard.style.transition = 'transform .34s cubic-bezier(.28,.02,.34,1), opacity .28s ease';
            window.requestAnimationFrame(function () {
                flipCard.style.transform = 'translate3d(' + (direction === 'left' ? '-112%' : '112%') + ',0,0) rotateZ(' + (direction === 'left' ? '-4deg' : '4deg') + ') scale(.985)';
                flipCard.style.opacity = '.06';
            });
            window.setTimeout(function () {
                pos = next;
                enterFrom = direction === 'left' ? 'right' : 'left';
                skipEntryAnimation = Boolean(fromGesture);
                transitionLocked = false;
                render();
            }, 345);
        }

        function finish() {
            var counts = ratingCounts();
            knownTotal.textContent = String(counts.known);
            unknownTotal.textContent = String(counts.unknown);
            saveUnknownCards();
            viewer.hidden = true;
            complete.hidden = false;
            complete.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }

        function rate(value, fromGesture) {
            if (transitionLocked) return;
            transitionLocked = true;
            var card = currentCard();
            ratings[String(card.id)] = value;
            saveUnknownCards();
            if (fromGesture) {
                flipStage.classList.remove('is-dragging', 'is-dragging-x', 'is-dragging-y');
                flipCard.style.transition = 'transform .3s cubic-bezier(.28,.02,.34,1), opacity .24s ease';
                window.requestAnimationFrame(function () {
                    flipCard.style.transform = 'translate3d(' + (value === 'known' ? '112%' : '-112%') + ',0,0) rotateZ(' + (value === 'known' ? '4deg' : '-4deg') + ') scale(.985)';
                    flipCard.style.opacity = '.06';
                });
            } else {
                clearGestureStyles();
                flipCard.classList.add(value === 'known' ? 'is-exiting-right' : 'is-exiting-left');
            }
            window.setTimeout(function () {
                if (Object.keys(ratings).length >= cards.length) {
                    transitionLocked = false;
                    finish();
                    return;
                }
                var nextPos = pos;
                do { nextPos = (nextPos + 1) % order.length; }
                while (ratings[String(cards[order[nextPos]].id)] && nextPos !== pos);
                pos = nextPos;
                skipEntryAnimation = Boolean(fromGesture);
                transitionLocked = false;
                render();
            }, fromGesture ? 305 : 260);
        }

        function shuffle() {
            if (transitionLocked) return;
            shuffleArray(order);
            pos = 0;
            ratings = {};
            window.sessionStorage.removeItem(unknownStorageKey);
            render();
        }

        function replay() {
            ratings = {};
            pos = 0;
            window.sessionStorage.removeItem(unknownStorageKey);
            complete.hidden = true;
            viewer.hidden = false;
            render();
            viewer.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

        function snapGesture() {
            flipCard.style.transition = 'transform .3s cubic-bezier(.2,.85,.24,1), opacity .2s ease';
            flipCard.style.transform = 'translate3d(0,0,0) rotateZ(0) scale(1)';
            flipInner.style.transition = 'transform .34s cubic-bezier(.2,.82,.2,1)';
            flipInner.style.removeProperty('transform');
            window.setTimeout(function () {
                clearGestureStyles();
                resetDragState();
            }, 350);
        }

        flipCard.addEventListener('click', function () {
            if (!flipStage.classList.contains('was-dragged')) flip();
            flipStage.classList.remove('was-dragged');
        });

        focusReveal.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            revealFocus();
        });

        flipCard.addEventListener('pointerdown', function (event) {
            if (transitionLocked || event.button !== 0 || event.target.closest('button')) return;
            drag.active = true;
            drag.pointerId = event.pointerId;
            drag.startX = event.clientX;
            drag.startY = event.clientY;
            drag.lastX = event.clientX;
            drag.lastY = event.clientY;
            drag.lastTime = performance.now();
            drag.x = 0;
            drag.y = 0;
            drag.axis = null;
            flipCard.setPointerCapture(event.pointerId);
            flipCard.style.transition = 'none';
            flipInner.style.transition = 'none';
            flipStage.classList.add('is-dragging');
        });

        flipCard.addEventListener('pointermove', function (event) {
            if (!drag.active || event.pointerId !== drag.pointerId) return;
            var now = performance.now();
            var elapsed = Math.max(now - drag.lastTime, 1);
            drag.x = event.clientX - drag.startX;
            drag.y = event.clientY - drag.startY;
            drag.velocityX = (event.clientX - drag.lastX) / elapsed;
            drag.velocityY = (event.clientY - drag.lastY) / elapsed;
            drag.lastX = event.clientX;
            drag.lastY = event.clientY;
            drag.lastTime = now;

            if (!drag.axis && Math.max(Math.abs(drag.x), Math.abs(drag.y)) > 9) {
                drag.axis = Math.abs(drag.x) > Math.abs(drag.y) * 1.08 ? 'x' : 'y';
                flipStage.classList.add(drag.axis === 'x' ? 'is-dragging-x' : 'is-dragging-y');
            }
            if (!drag.axis) return;
            flipStage.classList.add('was-dragged');

            if (drag.axis === 'x') {
                var stageWidth = Math.max(flipStage.clientWidth, 320);
                var maxTravel = Math.max(stageWidth * 1.45, window.innerWidth);
                var horizontal = clamp(drag.x, -maxTravel, maxTravel);
                var strength = Math.min(Math.abs(horizontal) / (stageWidth * .32), 1);
                var rotation = clamp(horizontal / (stageWidth * .085), -9, 9);
                flipCard.style.transform = 'translate3d(' + horizontal + 'px,' + (Math.abs(horizontal) * .01) + 'px,0) rotateZ(' + rotation + 'deg) scale(' + (1 - strength * .012) + ')';
                unknownBadge.style.opacity = String(horizontal < 0 ? strength : 0);
                knownBadge.style.opacity = String(horizontal > 0 ? strength : 0);
                showDragPreview(horizontal < 0 ? 1 : -1, Math.min(Math.abs(horizontal) / (stageWidth * .72), 1));
            } else {
                var stageHeight = Math.max(flipStage.clientHeight, 320);
                var vertical = clamp(drag.y * .92, -stageHeight * .86, stageHeight * .86);
                var baseAngle = flipCard.classList.contains('is-flipped') ? 180 : 0;
                var angle = baseAngle + clamp(-vertical / stageHeight * 205, -178, 178);
                flipCard.style.transform = 'translate3d(0,' + (vertical * .035) + 'px,0) scale(' + (1 - Math.abs(vertical) / 15000) + ')';
                flipInner.style.transform = 'rotateX(' + angle + 'deg)';
            }
        });

        function endDrag(event) {
            if (!drag.active || event.pointerId !== drag.pointerId) return;
            var axis = drag.axis;
            var distanceX = drag.x;
            var distanceY = drag.y;
            var velocityX = drag.velocityX;
            var velocityY = drag.velocityY;
            if (flipCard.hasPointerCapture(event.pointerId)) flipCard.releasePointerCapture(event.pointerId);
            drag.active = false;

            if (axis === 'y' && (Math.abs(distanceY) > 68 || Math.abs(velocityY) > .48)) {
                flipInner.style.transition = 'transform .4s cubic-bezier(.16,.82,.22,1)';
                flipInner.style.removeProperty('transform');
                flipCard.style.transition = 'transform .36s cubic-bezier(.2,.82,.2,1)';
                flipCard.style.removeProperty('transform');
                flipCard.classList.toggle('is-flipped');
                window.setTimeout(function () {
                    clearGestureStyles();
                    resetDragState();
                }, 420);
                return;
            }

            if (axis === 'x' && (Math.abs(distanceX) > flipStage.clientWidth * .17 || Math.abs(velocityX) > .62)) {
                var goesRight = distanceX > 0 || (distanceX === 0 && velocityX > 0);
                resetDragState();
                if (classifyMode) rate(goesRight ? 'known' : 'unknown', true);
                else slideTo(goesRight ? -1 : 1, goesRight ? 'right' : 'left', true, true);
                return;
            }

            snapGesture();
        }

        flipCard.addEventListener('pointerup', endDrag);
        flipCard.addEventListener('pointercancel', endDrag);
        document.getElementById('fcPrev').addEventListener('click', function () { slideTo(-1, 'right', false); });
        document.getElementById('fcNext').addEventListener('click', function () { slideTo(1, 'left', false); });
        document.getElementById('fcShuffle').addEventListener('click', shuffle);
        document.getElementById('fcRateUnknown').addEventListener('click', function () { rate('unknown'); });
        document.getElementById('fcRateKnown').addEventListener('click', function () { rate('known'); });
        document.getElementById('fcFlipReplay').addEventListener('click', replay);

        classifyModeButton.addEventListener('click', function () {
            classifyMode = !classifyMode;
            updateModeUi();
        });

        focusModeButton.addEventListener('click', function () {
            focusMode = !focusMode;
            updateModeUi();
            setFocusState(currentCard());
        });

        document.addEventListener('keydown', function (event) {
            if (event.target.matches('input, textarea, select, button, a')) return;
            if (event.key === ' ') { event.preventDefault(); flip(); }
            else if (event.key === 'ArrowLeft') { event.preventDefault(); slideTo(-1, 'right', false); }
            else if (event.key === 'ArrowRight') { event.preventDefault(); slideTo(1, 'left', false); }
            else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') { event.preventDefault(); flip(); }
        });

        updateModeUi();
        render();
    });
})();
