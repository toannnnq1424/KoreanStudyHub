(function () {
    'use strict';

    function ready(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
        else fn();
    }

    function toast(kind, message) {
        if (window.FcCommon) window.FcCommon.toast(kind, message);
    }

    ready(function () {
        var editor = window.FcDeckEditor;
        var panel = document.getElementById('fcAiPanel');
        var openButton = document.getElementById('fcAiGenBtn');
        if (!editor || !editor.deckId || !panel || !openButton) return;

        var closeButton = document.getElementById('fcAiClose');
        var generateButton = document.getElementById('fcAiGenerate');
        var textInput = document.getElementById('fcAiText');
        var fileInput = document.getElementById('fcAiFile');
        var fileName = document.getElementById('fcAiFileName');
        var countInput = document.getElementById('fcAiCount');
        var languageInput = document.getElementById('fcAiLanguage');
        var state = document.getElementById('fcAiState');
        var busy = false;
        var selectedFile = null;
        var selectedFileReady = Promise.resolve(null);

        var FILE_READ_ERROR = 'Trình duyệt không còn giữ được file này. Hãy chọn lại PDF/DOCX.';

        function setState(message, error) {
            state.textContent = message;
            state.classList.toggle('is-error', !!error);
        }

        function setBusy(value) {
            busy = value;
            generateButton.disabled = value;
            generateButton.textContent = value ? 'Đang tạo…' : 'Tạo bản nháp';
        }

        function formatFileSize(bytes) {
            if (!Number.isFinite(bytes) || bytes < 1) return '';
            return bytes >= 1024 * 1024
                ? (bytes / (1024 * 1024)).toFixed(1) + ' MB'
                : Math.ceil(bytes / 1024) + ' KB';
        }

        function renderFile(file) {
            if (!fileName) return;
            fileName.textContent = file
                ? file.name + ' · ' + formatFileSize(file.size)
                : 'Chưa chọn file';
            fileName.classList.toggle('has-file', !!file);
            fileName.title = file ? file.name : '';
        }

        function rememberFile(file) {
            if (!file) {
                selectedFile = null;
                selectedFileReady = Promise.resolve(null);
                renderFile(null);
                return;
            }

            selectedFile = file;
            renderFile(file);
            setState('Đang chuẩn bị ' + file.name + '…', false);

            // Keep an in-memory copy. Some embedded browsers clear input.files after
            // the first multipart request while leaving the native filename visible.
            selectedFileReady = file.arrayBuffer().then(function (bytes) {
                selectedFile = new File([bytes], file.name, {
                    type: file.type || 'application/octet-stream',
                    lastModified: file.lastModified
                });
                renderFile(selectedFile);
                setState('Đã chọn ' + file.name + '. File sẽ được giữ nếu cần thử lại.', false);
                return selectedFile;
            }).catch(function () {
                selectedFile = null;
                renderFile(null);
                setState(FILE_READ_ERROR, true);
                return null;
            });
        }

        function textClaimsAttachedFile(text) {
            return /\b(pdf|docx|file|tệp)\b/i.test(text)
                || /tài liệu (tôi |đã )?(gửi|chọn|tải)/i.test(text);
        }

        fileInput.addEventListener('change', function () {
            var file = fileInput.files && fileInput.files[0];
            rememberFile(file || null);
            if (!file && fileInput.value) {
                fileInput.value = '';
                setState(FILE_READ_ERROR, true);
            }
        });

        openButton.addEventListener('click', function () {
            panel.hidden = !panel.hidden;
            openButton.setAttribute('aria-expanded', String(!panel.hidden));
            if (!panel.hidden) textInput.focus();
        });

        closeButton.addEventListener('click', function () {
            panel.hidden = true;
            openButton.setAttribute('aria-expanded', 'false');
            openButton.focus();
        });

        generateButton.addEventListener('click', function () {
            if (busy) return;
            var liveFile = fileInput.files && fileInput.files[0];
            if (liveFile && liveFile !== selectedFile) rememberFile(liveFile);
            var text = textInput.value.trim();
            if (!selectedFile && fileInput.value) {
                fileInput.value = '';
                renderFile(null);
                setState(FILE_READ_ERROR, true);
                return;
            }
            if (!selectedFile && !text) {
                setState('Hãy chọn tài liệu hoặc dán nội dung trước.', true);
                return;
            }
            if (!selectedFile && textClaimsAttachedFile(text)) {
                setState('Bạn đang nhắc đến file nhưng chưa có PDF/DOCX nào được đính kèm.', true);
                return;
            }

            setBusy(true);
            setState(selectedFile ? 'Đang đọc file đã chọn…' : 'AI đang đọc nội dung…', false);
            selectedFileReady.then(function (file) {
                if (!file && textClaimsAttachedFile(text)) {
                    throw new Error(FILE_READ_ERROR);
                }
                var formData = new FormData();
                if (file) formData.append('file', file, file.name);
                if (text) formData.append('text', text);
                formData.append('count', countInput.value || '20');
                if (languageInput.value) formData.append('language', languageInput.value);

                setState('AI đang đọc tài liệu và tạo bản nháp…', false);
                return window.FcCommon.postForm(
                    '/api/flashcards/' + editor.deckId + '/ai-generate',
                    formData
                );
            }).then(function (response) {
                var cards = response.data && Array.isArray(response.data.cards)
                    ? response.data.cards : [];
                cards.forEach(function (card) {
                    editor.addRow({ front: card.front, back: card.back });
                });
                setState('Đã thêm ' + cards.length + ' thẻ chưa lưu vào cuối danh sách.', false);
                toast('success', 'Kiểm tra thẻ AI vừa tạo rồi nhấn Lưu bộ thẻ');
            }).catch(function (error) {
                var message = error.message || 'Không thể tạo thẻ bằng AI';
                if (selectedFile && message !== FILE_READ_ERROR) {
                    message += ' File vẫn được giữ; bạn có thể bấm Tạo bản nháp để thử lại.';
                }
                setState(message, true);
                toast('error', message);
            }).then(function () {
                setBusy(false);
            });
        });
    });
})();
