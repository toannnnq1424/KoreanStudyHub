/* Flashcard deck editor (KSH-5.x).
 *
 * Quizlet-style rich card rows: add / remove / reorder, local image previews,
 * accepted-answer alternatives and deferred storage uploads. The SINGLE submit
 * orchestrator persists rows first, uploads pending media against the returned
 * card ids, then lets the metadata POST navigate away.
 */
(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            fn();
        }
    }

    function toast(kind, msg) {
        if (window.FcCommon) window.FcCommon.toast(kind, msg);
    }

    ready(function () {
        var form = document.getElementById('deckForm');
        if (!form) return;
        var deckId = form.getAttribute('data-deck-id');
        var list = document.getElementById('fcCardList');
        if (!list) return;

        var template = document.getElementById('fcRowTemplate');
        var emptyBox = document.getElementById('fcCardsEmpty');
        var addBtn = document.getElementById('fcAddCard');
        var addBottomBtn = document.getElementById('fcAddCardBottom');
        var cardsJsonInput = document.getElementById('fcCardsJson');
        var cardsUrl = deckId ? '/api/flashcards/' + deckId + '/cards' : null;
        var createUrl = '/api/flashcards/decks';
        var proceeding = false;
        var saving = false;
        var draggingRow = null;
        var cancelActivePointerDrag = null;
        var allowedImageTypes = ['image/jpeg', 'image/png', 'image/webp'];
        var maxImageBytes = 5 * 1024 * 1024;

        function setSubmitDisabled(disabled) {
            form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (button) {
                button.disabled = disabled;
            });
        }

        function rows() {
            return Array.prototype.slice.call(list.querySelectorAll('.fc-card-row'));
        }

        function renumber() {
            rows().forEach(function (row, i) {
                var idx = row.querySelector('.fc-row-index');
                if (idx) idx.textContent = String(i + 1);
            });
            var empty = rows().length === 0;
            if (emptyBox) emptyBox.classList.toggle('is-hidden', !empty);
        }

        function alternativeValues(value, canonical) {
            var source = String(value || '').trim();
            var values = [];
            if (source.charAt(0) === '[') {
                try {
                    var parsed = JSON.parse(source);
                    if (Array.isArray(parsed)) values = parsed;
                } catch (ignored) {
                    values = [];
                }
            }
            if (!values.length && source) {
                values = source.split(/[;\n|]+|,\s*/);
            }
            var canonicalKey = String(canonical || '').trim().toLocaleLowerCase();
            var seen = Object.create(null);
            return values.map(function (item) { return String(item || '').trim(); })
                .filter(function (item) {
                    if (!item) return false;
                    var key = item.toLocaleLowerCase();
                    if (key === canonicalKey || seen[key]) return false;
                    seen[key] = true;
                    return true;
                });
        }

        function updateAlternativeCount(input) {
            if (!input) return;
            var row = input.closest('.fc-card-row');
            var canonical = row && row.querySelector('.fc-front');
            var count = alternativeValues(input.value, canonical ? canonical.value : '').length;
            var badge = row && row.querySelector('.fc-alternative-count');
            if (badge) badge.textContent = count + (count === 1 ? ' đáp án khác' : ' đáp án khác');
        }

        function revokePendingImage(row, side) {
            if (!row._fcPendingImages || !row._fcPendingImages[side]) return;
            var pending = row._fcPendingImages[side];
            if (pending.previewUrl) URL.revokeObjectURL(pending.previewUrl);
            delete row._fcPendingImages[side];
        }

        function setImagePreview(row, side, url, pending) {
            var label = row.querySelector('.fc-image-input[data-side="' + side + '"]');
            if (!label) return;
            var image = label.querySelector('.fc-image-preview');
            var status = label.querySelector('.fc-image-copy small');
            label.classList.toggle('has-preview', Boolean(url));
            label.classList.toggle('has-pending', Boolean(pending));
            if (image) {
                image.hidden = !url;
                if (url) image.src = url;
                else image.removeAttribute('src');
            }
            if (status) {
                status.textContent = pending
                    ? 'Đã chọn · sẽ tải lên khi lưu'
                    : (url ? 'Đã lưu · nhấn để đổi ảnh' : 'Chọn ảnh để xem trước');
            }
        }

        function validateImage(file) {
            if (allowedImageTypes.indexOf(file.type) < 0) {
                return 'Chỉ nhận ảnh JPEG, PNG hoặc WebP';
            }
            if (file.size > maxImageBytes) {
                return 'Ảnh phải nhỏ hơn hoặc bằng 5 MB';
            }
            return null;
        }

        function clearDragState() {
            rows().forEach(function (item) {
                item.classList.remove('is-dragging', 'is-drag-over', 'is-pointer-dragging');
            });
            list.querySelectorAll('.fc-sort-placeholder').forEach(function (placeholder) {
                placeholder.remove();
            });
            document.body.classList.remove('fc-is-sorting');
            draggingRow = null;
            cancelActivePointerDrag = null;
        }

        function bindDrag(row) {
            var handle = row.querySelector('.fc-drag-handle');
            if (!handle) return;

            handle.addEventListener('pointerdown', function (event) {
                if (event.button !== 0) return;
                event.preventDefault();
                if (cancelActivePointerDrag) cancelActivePointerDrag();
                clearDragState();
                draggingRow = row;
                var rect = row.getBoundingClientRect();
                var pointerOffsetY = event.clientY - rect.top;
                var placeholder = document.createElement('div');
                placeholder.className = 'fc-sort-placeholder';
                placeholder.style.height = rect.height + 'px';
                list.insertBefore(placeholder, row.nextSibling);
                var finished = false;
                var previousPointerY = event.clientY;

                row.classList.add('is-pointer-dragging');
                document.body.classList.add('fc-is-sorting');
                row.style.position = 'fixed';
                row.style.left = rect.left + 'px';
                row.style.top = rect.top + 'px';
                row.style.width = rect.width + 'px';
                row.style.height = rect.height + 'px';
                row.style.zIndex = '80';
                row.style.margin = '0';
                row.style.pointerEvents = 'none';
                row.style.transform = 'translate3d(0,0,0)';

                var moveHandler = function (moveEvent) {
                    moveEvent.preventDefault();
                    var nextTop = moveEvent.clientY - pointerOffsetY;
                    var clampedTop = Math.max(8, Math.min(window.innerHeight - rect.height - 8, nextTop));
                    row.style.transform = 'translate3d(0,' + (clampedTop - rect.top) + 'px,0)';

                    var hit = document.elementFromPoint(rect.left + Math.min(rect.width / 2, 260), moveEvent.clientY);
                    var target = hit && hit.closest ? hit.closest('.fc-card-row') : null;
                    if (target && target !== row) {
                        var targetRect = target.getBoundingClientRect();
                        var deltaY = moveEvent.clientY - previousPointerY;
                        var after = deltaY > 0
                            || (Math.abs(deltaY) < 1
                                && moveEvent.clientY > targetRect.top + targetRect.height / 2);
                        list.insertBefore(placeholder, after ? target.nextSibling : target);
                    }
                    previousPointerY = moveEvent.clientY;

                    if (moveEvent.clientY < 92) window.scrollBy(0, -14);
                    else if (moveEvent.clientY > window.innerHeight - 92) window.scrollBy(0, 14);
                };
                var finishHandler = function () {
                    if (finished) return;
                    finished = true;
                    window.removeEventListener('pointermove', moveHandler);
                    window.removeEventListener('pointerup', finishHandler);
                    window.removeEventListener('pointercancel', finishHandler);
                    window.removeEventListener('blur', finishHandler);
                    document.removeEventListener('visibilitychange', visibilityHandler);
                    if (placeholder.parentNode) {
                        list.insertBefore(row, placeholder);
                        placeholder.remove();
                    }
                    row.removeAttribute('style');
                    clearDragState();
                    renumber();
                    handle.focus({ preventScroll: true });
                };
                var visibilityHandler = function () {
                    if (document.hidden) finishHandler();
                };
                cancelActivePointerDrag = finishHandler;
                window.addEventListener('pointermove', moveHandler, { passive: false });
                window.addEventListener('pointerup', finishHandler);
                window.addEventListener('pointercancel', finishHandler);
                window.addEventListener('blur', finishHandler);
                document.addEventListener('visibilitychange', visibilityHandler);
            });

            // Native drag stays as a fallback for browsers without PointerEvent.
            handle.addEventListener('dragstart', function (event) {
                if ('PointerEvent' in window) {
                    event.preventDefault();
                    return;
                }
                draggingRow = row;
                row.classList.add('is-dragging');
                if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
            });
            handle.addEventListener('dragend', clearDragState);

            row.addEventListener('dragover', function (event) {
                if (!draggingRow || draggingRow === row) return;
                event.preventDefault();
                row.classList.add('is-drag-over');
                var rect = row.getBoundingClientRect();
                var after = event.clientY > rect.top + rect.height / 2;
                list.insertBefore(draggingRow, after ? row.nextElementSibling : row);
                renumber();
            });
            row.addEventListener('dragleave', function () {
                row.classList.remove('is-drag-over');
            });
            row.addEventListener('drop', function (event) {
                event.preventDefault();
                clearDragState();
                renumber();
            });

            handle.addEventListener('keydown', function (event) {
                if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return;
                event.preventDefault();
                if (event.key === 'ArrowUp') {
                    var prev = row.previousElementSibling;
                    if (prev) list.insertBefore(row, prev);
                } else {
                    var next = row.nextElementSibling;
                    if (next) list.insertBefore(next, row);
                }
                renumber();
                handle.focus();
            });
        }

        function bindRow(row) {
            var alternativesInput = row.querySelector('.fc-alternatives');
            if (alternativesInput && alternativesInput.value.trim().charAt(0) === '[') {
                try {
                    var parsed = JSON.parse(alternativesInput.value);
                    if (Array.isArray(parsed)) alternativesInput.value = parsed.join('; ');
                } catch (ignored) {
                    // Keep legacy free text visible so the owner can repair it.
                }
            }
            if (alternativesInput) {
                updateAlternativeCount(alternativesInput);
                alternativesInput.addEventListener('input', function () {
                    updateAlternativeCount(alternativesInput);
                });
                var frontInput = row.querySelector('.fc-front');
                if (frontInput) frontInput.addEventListener('input', function () {
                    updateAlternativeCount(alternativesInput);
                });
            }
            row._fcPendingImages = row._fcPendingImages || Object.create(null);
            ['front', 'back'].forEach(function (side) {
                setImagePreview(row, side, row.getAttribute('data-' + side + '-image') || '', false);
            });
            var removeBtn = row.querySelector('.fc-remove-card');
            if (removeBtn) removeBtn.addEventListener('click', function () {
                revokePendingImage(row, 'front');
                revokePendingImage(row, 'back');
                row.remove();
                renumber();
            });
            bindDrag(row);
            row.querySelectorAll('.fc-image-file').forEach(function (input) {
                input.addEventListener('change', function () {
                    var file = input.files && input.files[0];
                    if (!file) return;
                    var validationMessage = validateImage(file);
                    if (validationMessage) {
                        toast('error', validationMessage);
                        input.value = '';
                        return;
                    }
                    var side = input.dataset.side;
                    revokePendingImage(row, side);
                    var previewUrl = URL.createObjectURL(file);
                    row._fcPendingImages[side] = { file: file, previewUrl: previewUrl };
                    setImagePreview(row, side, previewUrl, true);
                    input.value = '';
                });
            });
        }

        // values (optional): { front, back } pre-fills a bulk-imported row and
        // suppresses auto-focus; omit it for the manual "Thêm thẻ" button.
        function addRow(values) {
            var frag = template.content.cloneNode(true);
            var row = frag.querySelector('.fc-card-row');
            list.appendChild(frag);
            bindRow(row);
            if (values) {
                var f = row.querySelector('.fc-front');
                var b = row.querySelector('.fc-back');
                if (f) f.value = values.front || '';
                if (b) b.value = values.back || '';
            }
            renumber();
            if (!values) {
                var front = row.querySelector('.fc-front');
                if (front) front.focus();
            }
            return row;
        }

        // ── Excel import: append parsed rows for review (no auto-save) ──
        function bindImport() {
            var importBtn = document.getElementById('fcImportBtn');
            var importInput = document.getElementById('fcImportInput');
            if (!importBtn || !importInput) return;
            var importUrl = deckId
                ? '/api/flashcards/' + deckId + '/import'
                : '/api/flashcards/import-preview';

            importBtn.addEventListener('click', function () { importInput.click(); });
            importInput.addEventListener('change', function () {
                var file = importInput.files && importInput.files[0];
                if (!file) return;
                var fd = new FormData();
                fd.append('file', file);
                window.FcCommon.postForm(importUrl, fd)
                    .then(function (res) {
                        var cards = (res.data && res.data.cards) || [];
                        cards.forEach(function (c) { addRow({ front: c.front, back: c.back }); });
                        toast('success', 'Đã thêm ' + cards.length +
                            ' thẻ từ Excel, kiểm tra rồi bấm Lưu');
                    })
                    .catch(function (err) {
                        toast('error', err.message || 'Import Excel thất bại');
                    })
                    // Reset the picker so re-selecting the same file re-triggers change.
                    .then(function () { importInput.value = ''; });
            });
        }

        // ── Save orchestrator ──────────────────────────────────────────
        function collectCards() {
            var cards = [];
            var invalid = false;
            rows().forEach(function (row) {
                var front = row.querySelector('.fc-front').value.trim();
                var back = row.querySelector('.fc-back').value.trim();
                row.classList.remove('is-invalid');
                if (!front || !back) { invalid = true; row.classList.add('is-invalid'); }
                var idAttr = row.getAttribute('data-card-id');
                var alternatives = row.querySelector('.fc-alternatives');
                var acceptedAnswers = alternatives
                    ? alternativeValues(alternatives.value, front) : [];
                cards.push({
                    id: idAttr ? Number(idAttr) : null,
                    front: front,
                    back: back,
                    frontImage: row.getAttribute('data-front-image') || null,
                    backImage: row.getAttribute('data-back-image') || null,
                    alternativesJson: acceptedAnswers.length ? JSON.stringify(acceptedAnswers) : null
                });
            });
            return { cards: cards, invalid: invalid };
        }

        function applyPersistedCards(savedCards) {
            var saved = Array.isArray(savedCards) ? savedCards : [];
            rows().forEach(function (row, index) {
                var card = saved[index];
                if (!card || !card.id) return;
                row.setAttribute('data-card-id', String(card.id));
                if (card.frontImage) row.setAttribute('data-front-image', card.frontImage);
                if (card.backImage) row.setAttribute('data-back-image', card.backImage);
            });
        }

        function uploadPendingImages(savedCards) {
            applyPersistedCards(savedCards);
            var jobs = [];
            rows().forEach(function (row) {
                ['front', 'back'].forEach(function (side) {
                    var pending = row._fcPendingImages && row._fcPendingImages[side];
                    if (!pending) return;
                    var cardId = row.getAttribute('data-card-id');
                    if (!cardId) {
                        jobs.push(Promise.reject(new Error('Không lấy được mã thẻ để tải ảnh')));
                        return;
                    }
                    var label = row.querySelector('.fc-image-input[data-side="' + side + '"]');
                    if (label) label.classList.add('is-uploading');
                    var fd = new FormData();
                    fd.append('side', side);
                    fd.append('file', pending.file);
                    var job = window.FcCommon.postForm('/api/flashcards/cards/' + cardId + '/image', fd)
                        .then(function (res) {
                            var url = res.data;
                            row.setAttribute('data-' + side + '-image', url);
                            revokePendingImage(row, side);
                            setImagePreview(row, side, url, false);
                            if (label) label.classList.remove('is-uploading');
                        }, function (err) {
                            if (label) label.classList.remove('is-uploading');
                            throw err;
                        });
                    jobs.push(job);
                });
            });
            return Promise.all(jobs);
        }

        function completeSave() {
            proceeding = true;
            setSubmitDisabled(false);
            if (typeof form.requestSubmit === 'function') form.requestSubmit();
            else form.submit();
        }

        form.addEventListener('submit', function (e) {
            if (proceeding) return; // real submit: let native POST run
            e.preventDefault();
            if (saving) return;

            var title = document.getElementById('deckTitle');
            if (title && !title.value.trim()) {
                toast('error', 'Tiêu đề không được để trống');
                return;
            }
            var collected = collectCards();
            if (collected.invalid) {
                toast('error', 'Mỗi thẻ phải có cả mặt trước và mặt sau');
                return;
            }

            saving = true;
            setSubmitDisabled(true);
            if (cardsJsonInput) cardsJsonInput.value = JSON.stringify(collected.cards);
            var saveRequest = deckId
                ? window.FcCommon.postJson(cardsUrl, { cards: collected.cards })
                    .then(function (res) {
                        return { deckId: deckId, cards: res.data || [] };
                    })
                : window.FcCommon.postJson(createUrl, {
                    title: title ? title.value.trim() : '',
                    description: (document.getElementById('deckDesc') || {}).value || '',
                    cards: collected.cards
                }).then(function (res) {
                    return res.data || {};
                });

            saveRequest
                .then(function (saved) {
                    if (!saved.deckId) throw new Error('Không lấy được mã bộ thẻ sau khi lưu');
                    deckId = String(saved.deckId);
                    cardsUrl = '/api/flashcards/' + deckId + '/cards';
                    form.setAttribute('data-deck-id', deckId);
                    form.setAttribute('action', '/my/flashcards/' + deckId);
                    return uploadPendingImages(saved.cards || []);
                })
                .then(function () {
                    completeSave();
                })
                .catch(function (err) {
                    saving = false;
                    setSubmitDisabled(false);
                    toast('error', err.message || 'Lưu bộ thẻ hoặc tải ảnh thất bại');
                });
        });

        // Bind existing rows + controls.
        rows().forEach(bindRow);
        // Wrap so the click event object is not misread as pre-fill values.
        if (addBtn) addBtn.addEventListener('click', function () { addRow(); });
        if (addBottomBtn) addBottomBtn.addEventListener('click', function () { addRow(); });
        bindImport();
        renumber();

        // Narrow integration seam for append-only helpers such as AI generation.
        // Saving remains owned exclusively by this module's submit orchestrator.
        window.FcDeckEditor = {
            deckId: deckId,
            addRow: function (values) { return addRow(values); },
            renumber: renumber
        };
    });
})();
