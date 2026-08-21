/* ═══════════════════════════════════════════════════════════════════════════
   ULP — Import tài khoản từ Excel (/admin/users)
   3-step <dialog> modal, mirroring the student-import modal of ULP-3.4.
   Markup lives in templates/admin/users.html; the modal frame, drop zone,
   stats, filter pills and table all reuse css/import-excel.css.

   The payload here is the ROSTER payload (creatable / existing / error), not
   the student-import one (ok / warning / error) — the two are not
   interchangeable, so this file deliberately does not reuse import-excel.js.

   All user-facing feedback goes through window.UlpToast; this script never
   reads #flash-data, because notifications.js is the sole drainer
   (see .claude/rules/flash-toast-drain.md).
   ══════════════════════════════════════════════════════════════════════════ */
(function () {
    'use strict';

    var IMPORT_BASE = '/admin/users/import';

    var state = { sessionId: null, rows: [], filter: 'ALL' };

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
        document.querySelectorAll('#importExcelModal [data-step]').forEach(function (s) {
            s.style.display = s.getAttribute('data-step') === stepName ? '' : 'none';
        });
    }

    function openModal() {
        var modal = el('importExcelModal');
        if (!modal) return;
        if (typeof modal.showModal === 'function') modal.showModal();
        else modal.setAttribute('open', '');
        resetForm();
        showStep('step1');
    }

    function closeModal() {
        var modal = el('importExcelModal');
        if (!modal) return;
        if (typeof modal.close === 'function' && modal.open) modal.close();
        else modal.removeAttribute('open');
    }

    function resetForm() {
        state.sessionId = null;
        state.rows = [];
        state.filter = 'ALL';
        var input = el('userImportFile');
        if (input) input.value = '';
        var label = el('userImportFileLabel');
        if (label) label.textContent = 'Chưa chọn file';
        var uploadBtn = el('userImportUploadBtn');
        if (uploadBtn) uploadBtn.disabled = true;
        var confirmBtn = el('userImportConfirmBtn');
        if (confirmBtn) confirmBtn.disabled = true;
    }

    // ── Click wiring ────────────────────────────────────────────────────
    function onUploadClick() {
        var input = el('userImportFile');
        var file = input && input.files && input.files[0];
        if (file) doUpload(file);
    }
    function onBackClick() { resetForm(); showStep('step1'); }
    function onTemplateClick() { window.location.href = IMPORT_BASE + '/template'; }
    // Newly created accounts must show up in the list behind the modal.
    function onDoneClick() { closeModal(); window.location.reload(); }

    var CLICK_HANDLERS = {
        'userImportUploadBtn':   onUploadClick,
        'userImportCancelBtn':   closeModal,
        'userImportTemplateBtn': onTemplateClick,
        'userImportBackBtn':     onBackClick,
        'userImportConfirmBtn':  doConfirm,
        'userImportDoneBtn':     onDoneClick
    };

    function bindAllClicks() {
        Object.keys(CLICK_HANDLERS).forEach(function (id) {
            var node = el(id);
            if (node) node.addEventListener('click', CLICK_HANDLERS[id]);
        });
    }

    // ── Step 1: file input + drag-and-drop ──────────────────────────────
    function selectFile(file) {
        var label = el('userImportFileLabel');
        var uploadBtn = el('userImportUploadBtn');
        if (label) label.textContent = file ? file.name : 'Chưa chọn file';
        if (uploadBtn) uploadBtn.disabled = !file;
    }

    function bindFileInputAndDropZone() {
        var input = el('userImportFile');
        var dropArea = el('userImportDropArea');
        if (input) {
            input.addEventListener('change', function () {
                selectFile(input.files && input.files[0]);
            });
        }
        if (!dropArea || !input) return;

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (ev) {
            dropArea.addEventListener(ev, function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropArea.classList.toggle('is-dragover',
                    ev === 'dragenter' || ev === 'dragover');
            });
        });
        dropArea.addEventListener('drop', function (e) {
            var files = e.dataTransfer && e.dataTransfer.files;
            if (files && files.length > 0) {
                input.files = files;
                selectFile(files[0]);
            }
        });
        dropArea.addEventListener('click', function () { input.click(); });
        // The drop zone is role="button", so it must answer Enter/Space too.
        dropArea.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
                e.preventDefault();
                input.click();
            }
        });
    }

    // ── Step 2: filter pills ────────────────────────────────────────────
    function bindFilterButtons() {
        var filters = document.querySelectorAll('#importExcelModal [data-filter]');
        filters.forEach(function (btn) {
            btn.addEventListener('click', function () {
                state.filter = btn.getAttribute('data-filter');
                filters.forEach(function (b) { b.classList.toggle('is-active', b === btn); });
                renderRows();
            });
        });
    }

    /**
     * Shared HTTP wrapper: posts, parses JSON, then routes through onOk while
     * surfacing any error as a toast and re-enabling the triggering button.
     * `init` is forwarded to fetch() untouched.
     */
    function postAndHandle(url, init, btn, defaultErrorMsg, onOk) {
        fetch(url, init)
            .then(function (res) {
                return res.text().then(function (text) {
                    return { status: res.status, body: text ? JSON.parse(text) : {} };
                });
            })
            .then(function (out) {
                if (out.status !== 200) {
                    var msg = (out.body && out.body.error) || defaultErrorMsg;
                    if (window.UlpToast) window.UlpToast.error(msg);
                    if (btn) btn.disabled = false;
                    return;
                }
                onOk(out.body);
            })
            .catch(function (err) {
                console.error(err);
                if (window.UlpToast) window.UlpToast.error('Không kết nối được tới server.');
                if (btn) btn.disabled = false;
            });
    }

    // ── Upload (step 1 → step 2) ────────────────────────────────────────
    function doUpload(file) {
        var uploadBtn = el('userImportUploadBtn');
        if (uploadBtn) uploadBtn.disabled = true;
        var formData = new FormData();
        formData.append('file', file);

        postAndHandle(IMPORT_BASE + '/upload', {
            method: 'POST',
            headers: csrfHeaders(),
            body: formData,
            credentials: 'same-origin'
        }, uploadBtn, 'Không thể xem trước file import.', function (body) {
            state.sessionId = body.sessionId;
            state.rows = body.rows || [];
            renderPreview(body);
            showStep('step2');
        });
    }

    function renderPreview(payload) {
        setText('userImportStatTotal', payload.totalRows);
        setText('userImportStatCreatable', payload.creatableCount);
        setText('userImportStatExisting', payload.existingCount);
        setText('userImportStatError', payload.errorCount);
        setText('userImportStatRoleDefaulted', payload.roleDefaultedCount);

        var confirmBtn = el('userImportConfirmBtn');
        // Nothing to create means nothing to confirm, even when the file parsed.
        if (confirmBtn) confirmBtn.disabled = !payload.creatableCount;

        state.filter = 'ALL';
        document.querySelectorAll('#importExcelModal [data-filter]').forEach(function (b) {
            b.classList.toggle('is-active', b.getAttribute('data-filter') === 'ALL');
        });
        renderRows();
    }

    function matchesFilter(row) {
        if (state.filter === 'ALL') return true;
        if (state.filter === 'ERROR') return !!row.isError;
        if (state.filter === 'CREATABLE') return !!row.isCreatable;
        if (state.filter === 'EXISTING') return !!row.isSkipped;
        // Cuts across CREATABLE rather than excluding it: these rows will be created.
        if (state.filter === 'ROLE_DEFAULTED') return !!row.isRoleDefaulted;
        return true;
    }

    function renderRows() {
        var tbody = el('userImportTableBody');
        if (!tbody) return;
        tbody.innerHTML = '';

        var rows = state.rows.filter(matchesFilter);
        if (rows.length === 0) {
            var emptyTr = document.createElement('tr');
            var emptyTd = document.createElement('td');
            emptyTd.colSpan = 7;
            emptyTd.className = 'iex-empty';
            emptyTd.textContent = 'Không có dòng nào khớp với bộ lọc hiện tại.';
            emptyTr.appendChild(emptyTd);
            tbody.appendChild(emptyTr);
            return;
        }

        rows.forEach(function (r) {
            // A defaulted role is a caveat on a creatable row, not an error: amber,
            // never red, and the row still counts toward "Sẽ tạo mới".
            var warned = !r.isError && !r.isSkipped && r.isRoleDefaulted;

            var tr = document.createElement('tr');
            tr.className = r.isError ? 'iex-row-error'
                : ((r.isSkipped || warned) ? 'iex-row-warn' : 'iex-row-ok');
            tr.appendChild(td(String(r.rowNumber)));

            var badge = document.createElement('span');
            badge.className = 'iex-badge ' + (r.isError ? 'iex-badge-error'
                : (r.isSkipped ? 'iex-badge-warn' : 'iex-badge-ok'));
            badge.textContent = r.statusMessage || r.status || '';
            var statusTd = document.createElement('td');
            statusTd.appendChild(badge);
            if (warned) {
                var warnBadge = document.createElement('span');
                warnBadge.className = 'iex-badge iex-badge-warn';
                warnBadge.style.marginLeft = '4px';
                warnBadge.textContent = 'Vai trò mặc định';
                statusTd.appendChild(warnBadge);
            }
            tr.appendChild(statusTd);

            tr.appendChild(td(r.email || '—'));
            tr.appendChild(td(r.fullName || '—'));
            // Spell out that the role was inferred, so the cell is not mistaken for
            // something the file actually said.
            tr.appendChild(td(warned ? (r.role || 'STUDENT') + ' (mặc định)' : (r.role || '—')));
            tr.appendChild(td(r.department || '—'));

            // Preview reports the pre-existing account state; confirm reports why a
            // row failed. Either way there is at most one note worth showing.
            var note = r.detail || (r.existingStatusLabel
                ? 'Trạng thái hiện tại: ' + r.existingStatusLabel : '');
            var noteTd = td(note);
            if (note) noteTd.setAttribute('title', note);
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
        var confirmBtn = el('userImportConfirmBtn');
        if (confirmBtn) confirmBtn.disabled = true;

        var headers = Object.assign({ 'Content-Type': 'application/json' }, csrfHeaders());
        postAndHandle(IMPORT_BASE + '/confirm', {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin',
            body: JSON.stringify({ sessionId: state.sessionId })
        }, confirmBtn, 'Không thể xác nhận import.', function (body) {
            // Re-render first: the server may have changed a row status between
            // preview and confirm, so step 2 stays truthful if it is revisited.
            state.rows = (body && body.rows) || state.rows;
            renderRows();
            renderSummary(body);
            showStep('step3');
            if (window.UlpToast) {
                window.UlpToast.success('Đã tạo ' + (body.created || 0) + ' tài khoản');
            }
        });
    }

    function renderSummary(payload) {
        setText('userImportSumProcessed', payload.totalProcessed);
        setText('userImportSumCreated', payload.created);
        setText('userImportSumExisting', payload.alreadyExisted);
        setText('userImportSumErrors', payload.errors);
        setText('userImportSumRoleDefaulted', payload.roleDefaulted);

        // Hide the tile entirely when nothing defaulted, so a clean import is not
        // cluttered by a zero the admin has to read and dismiss.
        var tile = el('userImportSumRoleDefaultedTile');
        if (tile) tile.style.display = payload.roleDefaulted ? '' : 'none';
    }

    function setText(id, value) {
        var node = el(id);
        if (node) node.textContent = value == null ? '0' : String(value);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!el('importExcelModal')) return;
        bindAllClicks();
        bindFileInputAndDropZone();
        bindFilterButtons();

        // Event delegation so any element with data-action="open-user-import" /
        // "close-user-import" (including nested SVG children) works.
        document.addEventListener('click', function (event) {
            if (!event.target || !event.target.closest) return;
            if (event.target.closest('[data-action="open-user-import"]')) {
                event.preventDefault();
                openModal();
            } else if (event.target.closest('[data-action="close-user-import"]')) {
                event.preventDefault();
                closeModal();
            }
        });
    });
})();