/* Lecturer AI question preview. Generated content is never persisted until confirm. */
(function () {
    'use strict';

    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var MSG_NO_MATERIAL = 'Vui lòng tải lên file PDF/DOCX hoặc dán nội dung tài liệu';
    var MSG_NO_SELECTION = 'Vui lòng chọn ít nhất một câu hỏi để chèn';
    var MSG_GENERATE_FAILED = 'Không sinh được câu hỏi, vui lòng thử lại';
    var MSG_CONFIRM_FAILED = 'Không chèn được câu hỏi vào bài test';
    var MSG_LOCKED = 'Bài test đã có lượt làm nên không thể thêm câu hỏi mới.';

    function ready(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            fn();
        }
    }

    function toast(kind, message) {
        if (window.FcCommon) {
            window.FcCommon.toast(kind, message);
        }
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function mount() {
        var panel = document.getElementById('lfAiGenPanel');
        var form = document.getElementById('lfForm');
        if (!panel || !form || panel.dataset.aiMounted === '1') return;
        panel.dataset.aiMounted = '1';

        var generateUrl = form.getAttribute('data-ai-generate-url') || '';
        var confirmUrl = form.getAttribute('data-ai-confirm-url') || '';
        var editUrl = form.getAttribute('data-edit-url') || '';
        var openBtn = document.getElementById('lfOpenAiGen');
        var closeBtn = document.getElementById('lfAiClose');
        var generateBtn = document.getElementById('lfAiGenerate');
        var confirmBtn = document.getElementById('lfAiConfirm');
        var selectAllBtn = document.getElementById('lfAiSelectAll');
        var confirmBar = document.getElementById('lfAiConfirmBar');
        var stateEl = document.getElementById('lfAiState');
        var resultsEl = document.getElementById('lfAiResults');
        var textEl = document.getElementById('lfAiText');
        var fileEl = document.getElementById('lfAiFile');
        var sessionId = null;

        function isLocked() {
            return form.dataset.questionBankLocked === '1';
        }

        function setState(message, busy) {
            if (!stateEl) return;
            stateEl.textContent = message;
            stateEl.setAttribute('aria-busy', busy ? 'true' : 'false');
        }

        function clearPreview() {
            sessionId = null;
            if (resultsEl) resultsEl.replaceChildren();
            if (confirmBar) confirmBar.hidden = true;
        }

        function renderPreview(questions) {
            if (!resultsEl) return;
            resultsEl.innerHTML = questions.map(function (question, index) {
                var options = (question.options || []).map(function (option) {
                    return '<li class="lf-bank-option'
                        + (option.correct ? ' is-correct' : '') + '">'
                        + escapeHtml(option.content)
                        + (option.correct ? ' (Đúng)' : '') + '</li>';
                }).join('');
                var explanation = question.explanation
                    ? '<p class="lf-media-hint">Giải thích: '
                    + escapeHtml(question.explanation) + '</p>'
                    : '';
                return '<article class="lf-bank-item">'
                    + '<div class="lf-bank-item-head"><label class="lf-ai-pick">'
                    + '<input type="checkbox" class="lf-ai-check" value="'
                    + index + '" checked><span>'
                    + '<span class="lf-bank-meta"><span>'
                    + escapeHtml(question.type || 'MCQ') + '</span></span>'
                    + '<span class="lf-bank-preview">'
                    + escapeHtml(question.content) + '</span></span></label></div>'
                    + '<ol class="lf-bank-options">' + options + '</ol>'
                    + explanation + '</article>';
            }).join('');
            if (confirmBar) confirmBar.hidden = false;
            setState('Đã sinh ' + questions.length
                + ' câu hỏi. Bỏ chọn câu không muốn giữ rồi xác nhận.', false);
        }

        function selectedIndexes() {
            if (!resultsEl) return [];
            return Array.from(resultsEl.querySelectorAll('.lf-ai-check:checked'))
                .map(function (box) { return Number(box.value); });
        }

        function generate() {
            if (!window.FcCommon || !generateUrl) {
                toast('error', 'Lưu bài test trước khi sinh câu hỏi bằng AI.');
                return;
            }
            if (isLocked()) {
                toast('error', MSG_LOCKED);
                return;
            }
            var text = textEl ? textEl.value.trim() : '';
            var file = fileEl && fileEl.files.length ? fileEl.files[0] : null;
            if (!file && !text) {
                toast('error', MSG_NO_MATERIAL);
                return;
            }
            if (file && file.size > MAX_FILE_BYTES) {
                toast('error', 'File vượt quá kích thước tối đa 5 MB.');
                return;
            }

            var payload = new FormData();
            if (file) payload.append('file', file);
            if (text) payload.append('text', text);
            payload.append('count', document.getElementById('lfAiCount').value);
            payload.append('type', document.getElementById('lfAiType').value);
            payload.append('difficulty', document.getElementById('lfAiDifficulty').value);

            clearPreview();
            generateBtn.disabled = true;
            setState('Đang sinh câu hỏi, quá trình này có thể mất một lúc…', true);
            window.FcCommon.postForm(generateUrl, payload)
                .then(function (result) {
                    var preview = result.data || {};
                    sessionId = preview.sessionId || null;
                    renderPreview(preview.questions || []);
                })
                .catch(function (error) {
                    var message = error.message || MSG_GENERATE_FAILED;
                    setState(message, false);
                    toast('error', message);
                })
                .finally(function () {
                    generateBtn.disabled = false;
                });
        }

        function confirmSelection() {
            if (!window.FcCommon || !sessionId || !confirmUrl) {
                toast('error', 'Phiên sinh câu hỏi đã hết hạn, vui lòng sinh lại.');
                return;
            }
            var indexes = selectedIndexes();
            if (!indexes.length) {
                toast('error', MSG_NO_SELECTION);
                return;
            }
            confirmBtn.disabled = true;
            setState('Đang chèn các câu hỏi đã chọn…', true);
            window.FcCommon.postJson(confirmUrl, {
                sessionId: sessionId,
                indexes: indexes
            }).then(function (result) {
                var inserted = result.data && result.data.insertedCount;
                toast('success', 'Đã chèn ' + (inserted || indexes.length)
                    + ' câu hỏi vào bài test.');
                window.location.href = editUrl
                    ? editUrl + '?tab=info'
                    : window.location.href;
            }).catch(function (error) {
                var message = error.message || MSG_CONFIRM_FAILED;
                confirmBtn.disabled = false;
                setState(message, false);
                toast('error', message);
            });
        }

        if (openBtn) {
            openBtn.addEventListener('click', function () {
                if (isLocked()) {
                    toast('error', MSG_LOCKED);
                    return;
                }
                panel.hidden = false;
                if (textEl) textEl.focus();
            });
        }
        if (closeBtn) {
            closeBtn.addEventListener('click', function () {
                panel.hidden = true;
            });
        }
        if (generateBtn) generateBtn.addEventListener('click', generate);
        if (confirmBtn) confirmBtn.addEventListener('click', confirmSelection);
        if (selectAllBtn) {
            selectAllBtn.addEventListener('click', function () {
                if (!resultsEl) return;
                resultsEl.querySelectorAll('.lf-ai-check').forEach(function (box) {
                    box.checked = true;
                });
            });
        }
    }

    if (window.LfForm && typeof window.LfForm.mount === 'function') {
        var formMount = window.LfForm.mount;
        window.LfForm.mount = function () {
            formMount.apply(null, arguments);
            mount();
        };
    }

    window.LfAiQuestions = { mount: mount };
    ready(mount);
})();
