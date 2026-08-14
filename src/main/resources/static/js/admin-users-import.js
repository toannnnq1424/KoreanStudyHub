/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Import User Accounts from Excel (Admin › Users)
   Vanilla JS for the 3-step modal on /admin/users.
   Markup lives in templates/admin/users.html (dialog#user-import).
   Mirrors the visual language of import-excel.js (lecturer roster import)
   but targets its own ids and its own API contract:
     POST /admin/users/import/upload  -> { sessionId, rows, creatableCount, errors }
     POST /admin/users/import/confirm -> { created, skipped }
   Row shape: { row, email, name, role, status: CREATE|EXISTS|ERROR, message }
   ══════════════════════════════════════════════════════════════════════════ */
(function () {
    'use strict';

    var UPLOAD_URL = '/admin/users/import/upload';
    var CONFIRM_URL = '/admin/users/import/confirm';

    var state = {
        sessionId: null, rows: [],
        uploadGeneration: 0, uploadController: null
    };

    function el(id) { return document.getElementById(id); }

    function csrfHeaders() {
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var headers = {};
        if (tokenMeta && headerMeta && tokenMeta.content && headerMeta.content) {
            headers[headerMeta.content] = tokenMeta.content;
        }
        return headers;
    }

    function showStep(stepName) {
        document.querySelectorAll('#user-import [data-account-step]').forEach(function (s) {
            s.style.display = s.getAttribute('data-account-step') === stepName ? '' : 'none';
        });
    }

    function invalidateUpload() {
        state.uploadGeneration += 1;
        if (state.uploadController) {
            state.uploadController.abort();
            state.uploadController = null;
        }
    }

    function openModal() {
        var modal = el('user-import');
        if (!modal) return;
        invalidateUpload();
        resetForm();
        showStep('step1');
        if (typeof modal.showModal === 'function') modal.showModal();
        else modal.setAttribute('open', '');
    }

    function closeModal() {
        invalidateUpload();
        var modal = el('user-import');
        if (!modal) return;
        if (typeof modal.close === 'function' && modal.open) modal.close();
        else modal.removeAttribute('open');
    }

    function resetForm() {
        state.sessionId = null; state.rows = [];
        var input = el('accountImportFile'); if (input) input.value = '';
        var fileLabel = el('accountImportFileLabel');
        if (fileLabel) fileLabel.textContent = 'Chưa chọn file';
        var btn = el('accountImportUpload'); if (btn) btn.disabled = true;
    }

    // ── Click handlers per button id ────────────────────────────────────
    function onUploadClick() {
        var input = el('accountImportFile');
        var f = input && input.files && input.files[0];
        if (f) doUpload(f);
    }
    function onBackClick() { resetForm(); showStep('step1'); }
    function onDoneClick() { closeModal(); window.location.reload(); }

    var CLICK_HANDLERS = {
        'accountImportUpload':  onUploadClick,
        'accountImportCancel':  closeModal,
        'accountImportBack':    onBackClick,
        'accountImportConfirm': doConfirm,
        'accountImportDone':    onDoneClick
    };
    function bindAllClicks() {
        Object.keys(CLICK_HANDLERS).forEach(function (id) {
            var elem = el(id);
            if (elem) elem.addEventListener('click', CLICK_HANDLERS[id]);
        });
    }

    function bindModalLifecycle() {
        var modal = el('user-import');
        if (!modal) return;

        // Capture the header close before its native dialog behaviour runs.
        var closeButton = modal.querySelector('[data-account-import-close]');
        if (closeButton) closeButton.addEventListener('click', function () {
            invalidateUpload();
            closeModal();
        });
        modal.addEventListener('cancel', invalidateUpload);
        modal.addEventListener('close', invalidateUpload);
    }

    // ── Step 1: file input + drag-and-drop ──────────────────────────────
    function bindFileInputAndDropZone() {
        var input = el('accountImportFile');
        var fileLabel = el('accountImportFileLabel');
        var uploadBtn = el('accountImportUpload');
        var dropArea = el('accountImportDropArea');
        if (input) input.addEventListener('change', function () {
            var f = input.files && input.files[0];
            if (fileLabel) fileLabel.textContent = f ? f.name : 'Chưa chọn file';
            if (uploadBtn) uploadBtn.disabled = !f;
        });
        if (!dropArea || !input) return;

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (ev) {
            dropArea.addEventListener(ev, function (e) {
                e.preventDefault(); e.stopPropagation();
                dropArea.classList.toggle('is-dragover',
                    ev === 'dragenter' || ev === 'dragover');
            });
        });
        dropArea.addEventListener('drop', function (e) {
            var files = e.dataTransfer && e.dataTransfer.files;
            if (files && files.length > 0) {
                input.files = files;
                if (fileLabel) fileLabel.textContent = files[0].name;
                if (uploadBtn) uploadBtn.disabled = false;
            }
        });
        dropArea.addEventListener('click', function () { input.click(); });
    }

    /**
     * Shared HTTP wrapper. Posts the request, parses JSON, then routes the
     * result through onOk with consistent toast + button-reset handling.
     */
    function postAndHandle(url, init, btn, defaultErrorMsg, onOk, isCurrent) {
        return fetch(url, init)
            .then(function (res) {
                return res.json().then(function (j) { return { status: res.status, body: j }; });
            })
            .then(function (out) {
                if (isCurrent && !isCurrent()) return;
                if (out.status !== 200) {
                    var msg = (out.body && out.body.error) || defaultErrorMsg;
                    if (window.KshToast) window.KshToast.error(msg);
                    if (btn) btn.disabled = false;
                    return;
                }
                onOk(out.body);
            })
            .catch(function (err) {
                if ((isCurrent && !isCurrent()) || (err && err.name === 'AbortError')) return;
                console.error(err);
                if (window.KshToast) window.KshToast.error('Không kết nối được tới server.');
                if (btn) btn.disabled = false;
            });
    }

    // ── Upload (step 1 → step 2) ────────────────────────────────────────
    function doUpload(file) {
        invalidateUpload();
        var requestGeneration = state.uploadGeneration;
        var controller = typeof window.AbortController === 'function'
            ? new window.AbortController()
            : null;
        state.uploadController = controller;

        function isCurrentUpload() {
            return requestGeneration === state.uploadGeneration
                && (!controller || state.uploadController === controller);
        }

        var uploadBtn = el('accountImportUpload');
        if (uploadBtn) uploadBtn.disabled = true;
        var formData = new FormData(); formData.append('file', file);
        var requestInit = {
            method: 'POST', headers: csrfHeaders(),
            body: formData, credentials: 'same-origin'
        };
        if (controller) requestInit.signal = controller.signal;

        postAndHandle(UPLOAD_URL, requestInit,
            uploadBtn, 'Tải lên thất bại.', function (body) {
                state.sessionId = body.sessionId;
                state.rows = body.rows || [];
                renderPreview();
                showStep('step2');
            }, isCurrentUpload).then(function () {
            if (isCurrentUpload()) state.uploadController = null;
        });
    }

    // ── Preview (step 2) ────────────────────────────────────────────────
    var STATUS_LABEL = { CREATE: 'Sẽ tạo', EXISTS: 'Đã tồn tại', ERROR: 'Lỗi' };
    var STATUS_BADGE_CLASS = { CREATE: 'iex-badge-ok', EXISTS: 'iex-badge-warn', ERROR: 'iex-badge-error' };
    var STATUS_ROW_CLASS = { CREATE: 'iex-row-ok', EXISTS: 'iex-row-warn', ERROR: 'iex-row-error' };

    function renderPreview() {
        var rows = state.rows;
        var createCount = rows.filter(function (r) { return r.status === 'CREATE'; }).length;
        var existingCount = rows.filter(function (r) { return r.status === 'EXISTS'; }).length;
        var errorCount = rows.filter(function (r) { return r.status === 'ERROR'; }).length;

        setText('accountImportTotal', rows.length);
        setText('accountImportCreate', createCount);
        setText('accountImportExisting', existingCount);
        setText('accountImportErrors', errorCount);

        var confirmBtn = el('accountImportConfirm');
        // Rows that already exist or have errors are skipped automatically by
        // the server on confirm — the only thing that blocks confirming is
        // having nothing left to create.
        if (confirmBtn) confirmBtn.disabled = createCount === 0;

        renderRows();
    }

    function renderRows() {
        var tbody = el('accountImportRows');
        if (!tbody) return;
        tbody.innerHTML = '';

        if (state.rows.length === 0) {
            var emptyTr = document.createElement('tr');
            var emptyTd = document.createElement('td');
            emptyTd.colSpan = 6; emptyTd.className = 'iex-empty';
            emptyTd.textContent = 'Không có dòng dữ liệu nào trong file.';
            emptyTr.appendChild(emptyTd); tbody.appendChild(emptyTr);
            return;
        }

        state.rows.forEach(function (r) {
            var tr = document.createElement('tr');
            tr.className = STATUS_ROW_CLASS[r.status] || 'iex-row-ok';
            tr.appendChild(td(String(r.row)));

            var badge = document.createElement('span');
            badge.className = 'iex-badge ' + (STATUS_BADGE_CLASS[r.status] || 'iex-badge-ok');
            badge.textContent = STATUS_LABEL[r.status] || r.status || '';
            var statusTd = document.createElement('td'); statusTd.appendChild(badge);
            tr.appendChild(statusTd);

            tr.appendChild(td(r.email || '—'));
            tr.appendChild(td(r.name || '—'));
            tr.appendChild(td(r.role || '—'));
            var noteTd = td(r.message || '');
            if (r.message) noteTd.setAttribute('title', r.message);
            tr.appendChild(noteTd);
            tbody.appendChild(tr);
        });
    }

    function td(text) {
        var c = document.createElement('td');
        c.textContent = text == null ? '' : text;
        return c;
    }

    // ── Confirm (step 2 → step 3) ───────────────────────────────────────
    function doConfirm() {
        if (!state.sessionId) return;
        var confirmBtn = el('accountImportConfirm');
        if (confirmBtn) confirmBtn.disabled = true;

        var headers = Object.assign({ 'Content-Type': 'application/json' }, csrfHeaders());

        postAndHandle(CONFIRM_URL, {
            method: 'POST', headers: headers, credentials: 'same-origin',
            body: JSON.stringify({ sessionId: state.sessionId })
        }, confirmBtn, 'Import thất bại.', function (body) {
            setText('accountImportCreated', body.created);
            setText('accountImportSkipped', body.skipped);
            showStep('step3');
        });
    }

    function setText(id, value) {
        var node = el(id);
        if (node) node.textContent = value == null ? '0' : String(value);
    }

    // ── Public API ──────────────────────────────────────────────────────
    window.openAccountImportModal = openModal;

    document.addEventListener('DOMContentLoaded', function () {
        if (!el('user-import')) return;
        bindAllClicks();
        bindModalLifecycle();
        bindFileInputAndDropZone();

        // Event delegation so any element with data-action="open-account-import"
        // (including SVG children) opens the modal — Element.closest walks the
        // DOM tree.
        document.addEventListener('click', function (event) {
            var trigger = event.target && event.target.closest
                ? event.target.closest('[data-action="open-account-import"]')
                : null;
            if (!trigger) return;
            event.preventDefault();
            openModal();
        });
    });
})();