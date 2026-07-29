/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Invite code panel behavior (Members / Settings invite tab)
   - Copy button: writes data-copy to clipboard, success toast via KshToast
   - Regenerate button: gates submit behind KshModal.confirm modal

   Uses document-level delegation so the handlers keep working after an AJAX
   tab swap (detail-tabs.js replaces #tabPanel innerHTML).
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  // ── Copy buttons ───────────────────────────────────────────────────
  document.addEventListener('click', function (event) {
    var button = event.target.closest ? event.target.closest('.invite-panel .copy-btn') : null;
    if (!button) return;

    var value = button.dataset.copy;
    var label = button.dataset.copyLabel || 'giá trị';
    if (!value || !navigator.clipboard) return;
    navigator.clipboard.writeText(value).then(function () {
      if (window.KshToast) window.KshToast.success('Đã sao chép ' + label);
    }).catch(function () {
      if (window.KshToast) window.KshToast.error('Không thể sao chép');
    });
  });

  // ── Regenerate buttons: confirm modal before submitting form ──────
  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || !form.classList || !form.classList.contains('invite-regen-form')) return;
    // If already confirmed, allow native submit through.
    if (form.dataset.confirmed === '1') return;
    event.preventDefault();
    var button = form.querySelector('.regen-btn');
    var title = (button && button.dataset.confirmTitle) || 'Tạo mã mới';
    var body = (button && button.dataset.confirmBody)
        || 'Tạo mã mới sẽ vô hiệu mã hiện tại. Tiếp tục?';
    if (!window.KshModal || !window.KshModal.confirm) {
      // Fallback if app.js failed to load: skip confirmation.
      form.dataset.confirmed = '1';
      form.submit();
      return;
    }
    window.KshModal.confirm({
      title: title,
      body: body,
      confirmLabel: 'Tạo mới',
      onConfirm: function () {
        form.dataset.confirmed = '1';
        form.submit();
      }
    });
  });

})();
