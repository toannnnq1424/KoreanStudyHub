/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Shared client-side behavior (vanilla JS, no framework)
   - Dropdown toggle (click trigger → open/close menu, close-on-outside-click)
   - Tab switching
   - Confirm modal helper (window.KshModal.confirm)
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  if (window.__KSH_SHARED_APP_INITIALIZED__) return;
  window.__KSH_SHARED_APP_INITIALIZED__ = true;

  // ── Dropdown toggle ────────────────────────────────────────────────
  document.addEventListener('click', function (e) {
    // Close any open dropdown when clicking outside
    document.querySelectorAll('.open').forEach(function (el) {
      if (!el.contains(e.target)) {
        el.classList.remove('open');
      }
    });
  });

  document.querySelectorAll('[data-toggle="dropdown"]').forEach(function (trigger) {
    trigger.addEventListener('click', function (e) {
      e.stopPropagation();
      var parent = this.closest('.dropdown') || this.parentElement;
      if (parent) {
        // Close all other dropdowns
        document.querySelectorAll('.dropdown.open').forEach(function (d) {
          if (d !== parent) d.classList.remove('open');
        });
        parent.classList.toggle('open');
      }
    });
  });

  // ── Tab switching ──────────────────────────────────────────────────
  document.querySelectorAll('[data-tab]').forEach(function (tab) {
    tab.addEventListener('click', function () {
      var group = this.closest('[data-tab-group]');
      if (!group) return;

      var tabName = this.getAttribute('data-tab');

      // Deactivate all tabs in group
      group.querySelectorAll('[data-tab].active').forEach(function (t) {
        t.classList.remove('active');
      });

      // Activate clicked tab
      this.classList.add('active');

      // Show matching panel, hide others
      group.querySelectorAll('[data-tab-panel]').forEach(function (panel) {
        if (panel.getAttribute('data-tab-panel') === tabName) {
          panel.style.display = '';
        } else {
          panel.style.display = 'none';
        }
      });
    });
  });

  // ── Confirm modal helper (window.KshModal.confirm) ─────────────────
  // Reusable across all pages. Uses native <dialog> when available.
  // Usage:
  //   KshModal.confirm({
  //     title: 'Xác nhận xoá',
  //     body: 'Bạn có chắc muốn xoá lớp này?',
  //     confirmLabel: 'Xoá',
  //     onConfirm: function () { ... }
  //   });
  function buildDialog() {
    var dlg = document.getElementById('kshConfirmDialog');
    if (dlg) return dlg;
    dlg = document.createElement('dialog');
    dlg.id = 'kshConfirmDialog';
    dlg.className = 'ksh-modal';
    dlg.innerHTML =
      '<form method="dialog" class="ksh-modal-form">' +
      '  <h3 class="ksh-modal-title" data-role="title"></h3>' +
      '  <p class="ksh-modal-body" data-role="body"></p>' +
      '  <div class="ksh-modal-actions">' +
      '    <button type="button" class="btn-ghost" data-role="cancel">Huỷ</button>' +
      '    <button type="button" class="btn-danger" data-role="confirm">OK</button>' +
      '  </div>' +
      '</form>';
    document.body.appendChild(dlg);
    return dlg;
  }

  window.KshModal = {
    confirm: function (opts) {
      opts = opts || {};
      var dlg = buildDialog();
      dlg.querySelector('[data-role="title"]').textContent = opts.title || 'Xác nhận';
      dlg.querySelector('[data-role="body"]').textContent = opts.body || '';
      var confirmBtn = dlg.querySelector('[data-role="confirm"]');
      var cancelBtn = dlg.querySelector('[data-role="cancel"]');
      confirmBtn.textContent = opts.confirmLabel || 'Xác nhận';

      var settled = false;
      var onConfirm = function () {
        if (settled) return;
        settled = true;
        cleanup();
        if (typeof opts.onConfirm === 'function') opts.onConfirm();
      };
      var onCancel = function () {
        if (settled) return;
        settled = true;
        cleanup();
        if (typeof opts.onCancel === 'function') opts.onCancel();
      };
      // ESC key triggers `cancel` event on native <dialog> — handle it.
      var onDialogCancel = function () { onCancel(); };

      function cleanup() {
        confirmBtn.removeEventListener('click', onConfirm);
        cancelBtn.removeEventListener('click', onCancel);
        dlg.removeEventListener('cancel', onDialogCancel);
        if (typeof dlg.close === 'function' && dlg.open) dlg.close();
        else dlg.removeAttribute('open');
      }

      confirmBtn.addEventListener('click', onConfirm);
      cancelBtn.addEventListener('click', onCancel);
      dlg.addEventListener('cancel', onDialogCancel);

      if (typeof dlg.showModal === 'function') {
        dlg.showModal();
      } else {
        // Fallback for browsers without <dialog> support
        dlg.setAttribute('open', '');
      }
    }
  };

  // ── Toast helper ────────────────────────────────────────────
  // Usage:
  //   KshToast.success('Đã tạo lớp NILXM');
  //   KshToast.error('Có lỗi xảy ra');
  //   KshToast.info('Đang xử lý...');
  function toastStack() {
    var stack = document.getElementById('kshToastStack');
    if (stack) return stack;
    stack = document.createElement('div');
    stack.id = 'kshToastStack';
    stack.className = 'ksh-toast-stack';
    stack.setAttribute('aria-live', 'polite');
    stack.setAttribute('aria-atomic', 'false');
    document.body.appendChild(stack);
    return stack;
  }

  function showToast(type, message, title) {
    if (!message) return;
    var toast = document.createElement('section');
    toast.className = 'ksh-toast is-' + type;
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');

    var content = document.createElement('div');
    content.className = 'ksh-toast-content';
    if (title) {
      var heading = document.createElement('p');
      heading.className = 'ksh-toast-title';
      heading.textContent = title;
      content.appendChild(heading);
    }
    var body = document.createElement('p');
    body.className = 'ksh-toast-message';
    body.textContent = message;
    content.appendChild(body);

    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'ksh-toast-close';
    close.setAttribute('aria-label', 'Đóng thông báo');
    close.innerHTML = '<svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none">' +
      '<path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>' +
      '</svg>';

    var timer;
    var dismissed = false;
    function dismiss() {
      if (dismissed) return;
      dismissed = true;
      window.clearTimeout(timer);
      toast.classList.add('is-leaving');
      window.setTimeout(function () { toast.remove(); }, 160);
    }
    close.addEventListener('click', dismiss);
    toast.addEventListener('mouseenter', function () { window.clearTimeout(timer); });
    toast.addEventListener('mouseleave', function () {
      timer = window.setTimeout(dismiss, type === 'error' ? 5000 : 3500);
    });

    toast.appendChild(content);
    toast.appendChild(close);
    toastStack().appendChild(toast);
    timer = window.setTimeout(dismiss, type === 'error' ? 5000 : 3500);
  }

  window.KshToast = {
    success: function (msg, title) { showToast('success', msg, title); },
    error:   function (msg, title) { showToast('error', msg, title); },
    warning: function (msg, title) { showToast('warning', msg, title); },
    info:    function (msg, title) { showToast('info', msg, title); }
  };

})();
