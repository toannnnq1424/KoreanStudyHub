(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-qb-scope-picker]').forEach(function (picker) {
      const form = picker.closest('form');
      if (!form) return;
      const radios = Array.from(picker.querySelectorAll('input[name="scope"]'));
      const fields = Array.from(form.querySelectorAll('[data-scope-field]'));

      function syncScope() {
        const selected = radios.find(function (radio) { return radio.checked; });
        const scope = selected ? selected.value : 'SUBJECT';
        fields.forEach(function (field) {
          const active = field.dataset.scopeField === scope;
          field.hidden = !active;
          field.querySelectorAll('select, input').forEach(function (control) {
            control.disabled = !active;
          });
        });
      }

      radios.forEach(function (radio) { radio.addEventListener('change', syncScope); });
      syncScope();
    });

    const drawer = document.querySelector('[data-qb-detail-drawer]');
    const drawerContent = drawer && drawer.querySelector('[data-qb-drawer-content]');
    const drawerLoading = drawer && drawer.querySelector('[data-qb-drawer-loading]');
    const drawerActions = drawer && drawer.querySelector('[data-qb-drawer-actions]');
    const drawerEdit = drawer && drawer.querySelector('[data-qb-drawer-edit]');
    const drawerTitle = drawer && drawer.querySelector('[data-qb-drawer-title]');
    const drawerKicker = drawer && drawer.querySelector('[data-qb-drawer-kicker]');
    const drawerPanel = drawer && drawer.querySelector('.qb-drawer-panel');
    let returnFocus = null;

    function closeDrawer() {
      if (!drawer) return;
      drawer.hidden = true;
      drawer.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('qb-drawer-open');
      if (drawerContent) drawerContent.replaceChildren();
      if (drawerActions) drawerActions.hidden = true;
      if (drawerEdit) drawerEdit.removeAttribute('href');
      if (drawerPanel) drawerPanel.classList.remove('is-editor');
      if (returnFocus) returnFocus.focus();
    }

    function openDetail(button) {
      if (!drawer) return;
      const row = button.closest('[data-qb-question-row]');
      const url = row && row.dataset.detailUrl;
      if (!url) return;
      returnFocus = button;
      if (drawerPanel) drawerPanel.classList.remove('is-editor');
      if (drawerKicker) drawerKicker.textContent = 'XEM NHANH';
      if (drawerTitle) drawerTitle.textContent = 'Câu hỏi';
      drawer.hidden = false;
      drawer.setAttribute('aria-hidden', 'false');
      document.body.classList.add('qb-drawer-open');
      if (drawerLoading) drawerLoading.hidden = false;
      if (drawerContent) drawerContent.replaceChildren();
      const editUrl = row.dataset.editUrl;
      if (drawerEdit && editUrl) drawerEdit.href = editUrl;
      if (drawerActions) drawerActions.hidden = !editUrl;
      fetch(url, { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(function (response) {
          if (!response.ok) throw new Error('Không tải được câu hỏi');
          return response.text();
        })
        .then(function (html) {
          const doc = new DOMParser().parseFromString(html, 'text/html');
          const detail = doc.querySelector('.qb-detail-card');
          if (!detail) throw new Error('Dữ liệu chi tiết không hợp lệ');
          if (drawerContent) drawerContent.append(detail);
        })
        .catch(function () {
          if (drawerContent) drawerContent.textContent = 'Không thể tải chi tiết. Hãy mở trang chi tiết và thử lại.';
        })
        .finally(function () { if (drawerLoading) drawerLoading.hidden = true; });
    }

    function openEditor(link) {
      if (!drawer) return;
      const url = link.getAttribute('href');
      if (!url) return;
      returnFocus = link;
      drawer.hidden = false;
      drawer.setAttribute('aria-hidden', 'false');
      document.body.classList.add('qb-drawer-open');
      if (drawerPanel) drawerPanel.classList.add('is-editor');
      if (drawerKicker) drawerKicker.textContent = 'SOẠN CÂU HỎI';
      if (drawerTitle) drawerTitle.textContent = url.includes('/edit') ? 'Chỉnh sửa câu hỏi' : 'Thêm câu hỏi';
      if (drawerLoading) {
        drawerLoading.textContent = 'Đang mở trình soạn câu hỏi…';
        drawerLoading.hidden = false;
      }
      if (drawerContent) drawerContent.replaceChildren();
      if (drawerActions) drawerActions.hidden = true;
      fetch(url, { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(function (response) {
          if (!response.ok) throw new Error('Không tải được biểu mẫu');
          return response.text();
        })
        .then(function (html) {
          const doc = new DOMParser().parseFromString(html, 'text/html');
          const form = doc.querySelector('.qb-form');
          if (!form) throw new Error('Biểu mẫu không hợp lệ');
          form.classList.add('qb-drawer-form');
          form.querySelectorAll('[data-ksh-select]').forEach(function (select) {
            select.removeAttribute('data-ksh-select');
            select.removeAttribute('data-ksh-searchable');
          });
          if (drawerContent) {
            drawerContent.append(form);
            if (window.initQuestionBankForm) window.initQuestionBankForm(form);
          }
        })
        .catch(function () {
          if (drawerContent) drawerContent.textContent = 'Không thể mở trình soạn câu hỏi. Hãy thử lại hoặc mở trang riêng.';
        })
        .finally(function () { if (drawerLoading) drawerLoading.hidden = true; });
    }

    document.querySelectorAll('[data-qb-open-detail]').forEach(function (button) {
      button.addEventListener('click', function () { openDetail(button); });
    });
    document.querySelectorAll('[data-qb-open-editor]').forEach(function (link) {
      link.addEventListener('click', function (event) {
        event.preventDefault();
        openEditor(link);
      });
    });
    if (drawer) {
      drawer.querySelectorAll('[data-qb-close-drawer]').forEach(function (button) {
        button.addEventListener('click', closeDrawer);
      });
      document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && !drawer.hidden) closeDrawer();
      });
    }
  });
})();
