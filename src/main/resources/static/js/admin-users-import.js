(function () {
  'use strict';

  var BASE = '/admin/users/import';
  var state = { file: null, sessionId: null, rows: [], filter: 'ALL' };
  function byId(id) { return document.getElementById(id); }

  function csrfHeaders(extra) {
    var headers = extra || {};
    var token = document.querySelector('meta[name="_csrf"]');
    var name = document.querySelector('meta[name="_csrf_header"]');
    if (token && name) headers[name.content] = token.content;
    return headers;
  }

  function toast(kind, message) {
    if (window.KshToast && typeof window.KshToast[kind] === 'function') {
      window.KshToast[kind](message);
    }
  }

  function showStep(name) {
    document.querySelectorAll('#importExcelModal [data-step]').forEach(function (node) {
      node.style.display = node.getAttribute('data-step') === name ? 'flex' : 'none';
      if (node.tagName === 'SECTION' && node.getAttribute('data-step') === name) {
        node.style.display = '';
      }
    });
  }

  function reset() {
    state.file = null;
    state.sessionId = null;
    state.rows = [];
    state.filter = 'ALL';
    var input = byId('userImportFile');
    if (input) input.value = '';
    setText('userImportFileLabel', 'Chưa chọn file');
    byId('userImportUploadBtn').disabled = true;
    byId('userImportConfirmBtn').disabled = true;
  }

  function open() {
    var dialog = byId('importExcelModal');
    if (!dialog) return;
    reset();
    showStep('step1');
    if (dialog.showModal) dialog.showModal(); else dialog.setAttribute('open', '');
  }

  function close() {
    var dialog = byId('importExcelModal');
    if (!dialog) return;
    if (dialog.close && dialog.open) dialog.close(); else dialog.removeAttribute('open');
  }

  function selectFile(file) {
    state.file = file || null;
    setText('userImportFileLabel', file ? file.name : 'Chưa chọn file');
    byId('userImportUploadBtn').disabled = !file;
  }

  function request(url, options, button, fallback, success) {
    fetch(url, options).then(function (response) {
      return response.text().then(function (text) {
        var body = {};
        try { body = text ? JSON.parse(text) : {}; } catch (ignored) { body = {}; }
        if (!response.ok) throw new Error(body.error || fallback);
        return body;
      });
    }).then(success).catch(function (error) {
      toast('error', error.message || fallback);
      if (button) button.disabled = false;
    });
  }

  function upload() {
    if (!state.file) return;
    var button = byId('userImportUploadBtn');
    button.disabled = true;
    var data = new FormData();
    data.append('file', state.file);
    request(BASE + '/upload', {
      method: 'POST', headers: csrfHeaders({}), body: data, credentials: 'same-origin'
    }, button, 'Không thể xem trước file import.', function (payload) {
      state.sessionId = payload.sessionId;
      state.rows = payload.rows || [];
      setText('userImportStatTotal', payload.totalRows);
      setText('userImportStatCreatable', payload.creatableCount);
      setText('userImportStatExisting', payload.existingCount);
      setText('userImportStatError', payload.errorCount);
      setText('userImportStatDefaulted', payload.roleDefaultedCount);
      byId('userImportConfirmBtn').disabled = !(payload.creatableCount > 0);
      state.filter = 'ALL';
      syncFilters();
      renderRows();
      showStep('step2');
    });
  }

  function confirmImport() {
    if (!state.sessionId) return;
    var button = byId('userImportConfirmBtn');
    button.disabled = true;
    request(BASE + '/confirm', {
      method: 'POST',
      headers: csrfHeaders({ 'Content-Type': 'application/json' }),
      credentials: 'same-origin',
      body: JSON.stringify({ sessionId: state.sessionId })
    }, button, 'Không thể hoàn tất import.', function (payload) {
      state.rows = payload.rows || state.rows;
      setText('userImportSumTotal', payload.totalProcessed);
      setText('userImportSumCreated', payload.created);
      setText('userImportSumExisting', payload.alreadyExisted);
      setText('userImportSumErrors', payload.errors);
      showStep('step3');
      toast('success', 'Đã tạo ' + (payload.created || 0) + ' tài khoản mới.');
    });
  }

  function renderRows() {
    var body = byId('userImportTableBody');
    body.innerHTML = '';
    var filtered = state.rows.filter(function (row) {
      if (state.filter === 'CREATABLE') return row.creatable;
      if (state.filter === 'EXISTING') return row.skipped;
      if (state.filter === 'ERROR') return row.error;
      return true;
    });
    if (!filtered.length) {
      var emptyRow = document.createElement('tr');
      var emptyCell = cell('Không có dòng nào khớp bộ lọc.');
      emptyCell.colSpan = 7;
      emptyCell.className = 'iex-empty';
      emptyRow.appendChild(emptyCell);
      body.appendChild(emptyRow);
      return;
    }
    filtered.forEach(function (row) {
      var tr = document.createElement('tr');
      tr.className = row.error ? 'iex-row-error'
        : (row.skipped || row.roleDefaulted ? 'iex-row-warn' : 'iex-row-ok');
      tr.appendChild(cell(row.rowNumber));
      var status = cell('');
      var badge = document.createElement('span');
      badge.className = 'iex-badge ' + (row.error ? 'iex-badge-error'
        : (row.skipped || row.roleDefaulted ? 'iex-badge-warn' : 'iex-badge-ok'));
      badge.textContent = row.statusMessage || row.status || '';
      status.appendChild(badge);
      tr.appendChild(status);
      tr.appendChild(cell(row.email || '—'));
      tr.appendChild(cell(row.fullName || '—'));
      tr.appendChild(cell((row.role || '—') + (row.roleDefaulted ? ' (mặc định)' : '')));
      tr.appendChild(cell(row.subject || '—'));
      tr.appendChild(cell(row.detail || (row.existingStatusLabel
        ? 'Trạng thái hiện tại: ' + row.existingStatusLabel : '')));
      body.appendChild(tr);
    });
  }

  function cell(value) {
    var td = document.createElement('td');
    td.textContent = value == null ? '' : String(value);
    return td;
  }

  function setText(id, value) {
    var node = byId(id);
    if (node) node.textContent = value == null ? '0' : String(value);
  }

  function syncFilters() {
    document.querySelectorAll('#importExcelModal [data-filter]').forEach(function (button) {
      button.classList.toggle('is-active', button.getAttribute('data-filter') === state.filter);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!byId('importExcelModal')) return;
    byId('userImportFile').addEventListener('change', function (event) {
      selectFile(event.target.files && event.target.files[0]);
    });
    var drop = byId('userImportDropArea');
    drop.addEventListener('click', function () { byId('userImportFile').click(); });
    drop.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault(); byId('userImportFile').click();
      }
    });
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (name) {
      drop.addEventListener(name, function (event) {
        event.preventDefault(); event.stopPropagation();
        drop.classList.toggle('is-dragover', name === 'dragenter' || name === 'dragover');
        if (name === 'drop') selectFile(event.dataTransfer.files[0]);
      });
    });
    document.querySelectorAll('#importExcelModal [data-filter]').forEach(function (button) {
      button.addEventListener('click', function () {
        state.filter = button.getAttribute('data-filter'); syncFilters(); renderRows();
      });
    });
    byId('userImportUploadBtn').addEventListener('click', upload);
    byId('userImportConfirmBtn').addEventListener('click', confirmImport);
    byId('userImportTemplateBtn').addEventListener('click', function () {
      window.location.href = BASE + '/template';
    });
    byId('userImportBackBtn').addEventListener('click', function () {
      reset(); showStep('step1');
    });
    byId('userImportCancelBtn').addEventListener('click', close);
    byId('userImportDoneBtn').addEventListener('click', function () {
      close(); window.location.reload();
    });
    document.addEventListener('click', function (event) {
      if (event.target.closest('[data-action="open-user-import"]')) open();
      if (event.target.closest('[data-action="close-user-import"]')) close();
    });
  });
})();
