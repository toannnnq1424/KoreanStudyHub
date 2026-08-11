/* Owner-scoped asset picker for canonical Library lesson authoring. */
(function () {
  'use strict';

  var API = '/lecturer/library/assets/api';
  var state = {
    kind: '',
    page: 0,
    q: '',
    totalPages: 0,
    onSelect: null,
    selectedIds: new Set(),
    opener: null
  };

  function byId(id) {
    return document.getElementById(id);
  }

  function button(label, className) {
    var node = document.createElement('button');
    node.type = 'button';
    node.className = className || 'library-picker-button';
    node.textContent = label;
    return node;
  }

  function ensureModal() {
    var modal = byId('libraryPickerModal');
    if (modal) return modal;

    modal = document.createElement('dialog');
    modal.id = 'libraryPickerModal';
    modal.className = 'library-picker-modal';
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-labelledby', 'libraryPickerTitle');
    modal.innerHTML =
      '<section class="library-picker-dialog">' +
      '  <header class="library-picker-head">' +
      '    <div><h3 id="libraryPickerTitle">Chọn từ Kho tài liệu cá nhân</h3></div>' +
      '    <button type="button" class="library-picker-button" data-picker-close aria-label="Đóng kho tài liệu">Đóng</button>' +
      '  </header>' +
      '  <form class="library-picker-tools" role="search" data-picker-search-form>' +
      '    <label class="sr-only" for="libraryPickerQuery">Tìm tài liệu cá nhân</label>' +
      '    <input type="search" id="libraryPickerQuery" placeholder="Tên hiển thị hoặc tên tệp…" autocomplete="off">' +
      '    <button type="submit" class="library-picker-button">Tìm</button>' +
      '  </form>' +
      '  <div class="library-picker-body" id="libraryPickerBody" aria-live="polite"></div>' +
      '  <footer class="library-picker-foot">' +
      '    <button type="button" class="library-picker-button" id="libraryPickerPrev">Trang trước</button>' +
      '    <span id="libraryPickerPageLabel" aria-live="polite"></span>' +
      '    <button type="button" class="library-picker-button" id="libraryPickerNext">Trang sau</button>' +
      '  </footer>' +
      '</section>';
    document.body.appendChild(modal);

    modal.addEventListener('click', function (event) {
      if (event.target === modal || (event.target && event.target.hasAttribute('data-picker-close'))) close();
    });
    modal.addEventListener('cancel', function (event) {
      event.preventDefault();
      close();
    });
    modal.querySelector('[data-picker-search-form]').addEventListener('submit', function (event) {
      event.preventDefault();
      state.q = byId('libraryPickerQuery').value.trim();
      state.page = 0;
      load();
    });
    byId('libraryPickerPrev').addEventListener('click', function () {
      if (state.page <= 0) return;
      state.page -= 1;
      load();
    });
    byId('libraryPickerNext').addEventListener('click', function () {
      if (state.page + 1 >= state.totalPages) return;
      state.page += 1;
      load();
    });
    return modal;
  }

  function close() {
    var modal = byId('libraryPickerModal');
    if (!modal || !modal.open) return;
    modal.close();
    document.body.classList.remove('library-picker-open');
    if (state.opener && typeof state.opener.focus === 'function') state.opener.focus();
    state.onSelect = null;
    state.opener = null;
  }

  function formatSize(value) {
    var bytes = Number(value);
    if (!Number.isFinite(bytes) || bytes < 0) return 'Chưa rõ dung lượng';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  function render(payload) {
    var body = byId('libraryPickerBody');
    var items = payload && Array.isArray(payload.items) ? payload.items : [];
    state.totalPages = Math.max(0, Number(payload && payload.totalPages) || 0);

    byId('libraryPickerPageLabel').textContent = state.totalPages
      ? 'Trang ' + (state.page + 1) + ' / ' + state.totalPages : '';
    byId('libraryPickerPrev').disabled = state.page <= 0;
    byId('libraryPickerNext').disabled = state.page + 1 >= state.totalPages;
    body.replaceChildren();

    if (!items.length) {
      var empty = document.createElement('div');
      empty.className = 'library-picker-empty';
      empty.textContent = 'Không có tài liệu cá nhân phù hợp. Bạn có thể tải tệp mới từ Kho tài liệu cá nhân.';
      body.appendChild(empty);
      return;
    }

    items.forEach(function (item) {
      var selected = state.selectedIds.has(String(item.id));
      var itemButton = button('', 'library-picker-item');
      itemButton.disabled = selected;
      itemButton.setAttribute('aria-label', (selected ? 'Đã chọn ' : 'Chọn ') + (item.title || item.originalFilename || 'tài liệu'));

      var copy = document.createElement('span');
      var title = document.createElement('span');
      title.className = 'library-picker-item-title';
      title.textContent = item.title || item.originalFilename || 'Tài liệu chưa đặt tên';
      var meta = document.createElement('span');
      meta.className = 'library-picker-item-meta';
      meta.textContent = (item.originalFilename || '') + ' · ' + formatSize(item.sizeBytes) +
        ' · ' + (item.kind === 'VIDEO' ? 'Video' : 'Tài liệu');
      copy.append(title, document.createElement('br'), meta);

      var action = document.createElement('strong');
      action.textContent = selected ? 'Đã chọn' : 'Chọn';
      itemButton.append(copy, action);
      itemButton.addEventListener('click', function () {
        if (selected) return;
        state.selectedIds.add(String(item.id));
        var callback = state.onSelect;
        close();
        if (typeof callback === 'function') callback(item);
      });
      body.appendChild(itemButton);
    });
  }

  function showLoadError() {
    var body = byId('libraryPickerBody');
    body.replaceChildren();
    var error = document.createElement('div');
    error.className = 'library-picker-error';
    error.textContent = 'Chưa thể tải Kho tài liệu cá nhân. ';
    var retry = button('Thử lại', 'library-picker-button');
    retry.addEventListener('click', load);
    error.appendChild(retry);
    body.appendChild(error);
  }

  function load() {
    var body = byId('libraryPickerBody');
    body.innerHTML = '<div class="library-picker-loading">Đang tải tài liệu của bạn…</div>';
    var query = new URLSearchParams({
      page: String(state.page),
      size: '12',
      q: state.q,
      kind: state.kind
    });

    window.fetch(API + '?' + query.toString(), {
      method: 'GET',
      credentials: 'same-origin',
      headers: {'Accept': 'application/json'}
    }).then(function (response) {
      if (!response.ok) throw new Error('Personal library returned HTTP ' + response.status);
      return response.json();
    }).then(render).catch(showLoadError);
  }

  function open(options) {
    options = options || {};
    var modal = ensureModal();
    state.kind = options.kind || '';
    state.page = 0;
    state.q = '';
    state.onSelect = options.onSelect || null;
    state.selectedIds = new Set((options.selectedIds || []).map(String));
    state.opener = options.opener || document.activeElement;
    byId('libraryPickerQuery').value = '';
    if (typeof modal.showModal === 'function') modal.showModal();
    else modal.setAttribute('open', '');
    document.body.classList.add('library-picker-open');
    byId('libraryPickerQuery').focus();
    load();
  }

  window.KshLibraryPicker = {open: open, close: close};
})();
