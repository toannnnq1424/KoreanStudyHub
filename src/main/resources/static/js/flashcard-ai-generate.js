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
        var countInput = document.getElementById('fcAiCount');
        var languageInput = document.getElementById('fcAiLanguage');
        var state = document.getElementById('fcAiState');
        var busy = false;

        function setState(message, error) {
            state.textContent = message;
            state.classList.toggle('is-error', !!error);
        }

        function setBusy(value) {
            busy = value;
            generateButton.disabled = value;
            generateButton.textContent = value ? 'Đang tạo…' : 'Tạo bản nháp';
        }

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
            var file = fileInput.files && fileInput.files[0];
            var text = textInput.value.trim();
            if (!file && !text) {
                setState('Hãy chọn tài liệu hoặc dán nội dung trước.', true);
                return;
            }

            var formData = new FormData();
            if (file) formData.append('file', file);
            if (text) formData.append('text', text);
            formData.append('count', countInput.value || '20');
            if (languageInput.value) formData.append('language', languageInput.value);

            setBusy(true);
            setState('AI đang đọc tài liệu và tạo bản nháp…', false);
            window.FcCommon.postForm(
                '/api/flashcards/' + editor.deckId + '/ai-generate',
                formData
            ).then(function (response) {
                var cards = response.data && Array.isArray(response.data.cards)
                    ? response.data.cards : [];
                cards.forEach(function (card) {
                    editor.addRow({ front: card.front, back: card.back });
                });
                setState('Đã thêm ' + cards.length + ' thẻ chưa lưu vào cuối danh sách.', false);
                toast('success', 'Kiểm tra thẻ AI vừa tạo rồi nhấn Lưu bộ thẻ');
            }).catch(function (error) {
                var message = error.message || 'Không thể tạo thẻ bằng AI';
                setState(message, true);
                toast('error', message);
            }).then(function () {
                setBusy(false);
            });
        });
    });
})();
