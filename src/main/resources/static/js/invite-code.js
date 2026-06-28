/* ═══════════════════════════════════════════════════════════════════════════
   Ksh — Invite code panel behavior (Members tab)
   - Copy button: writes data-copy to clipboard, success toast via KshToast
   - Regenerate button: gates submit behind KshModal.confirm modal
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  // ── Copy buttons ───────────────────────────────────────────────────
  document.querySelectorAll('.invite-panel .copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var value = btn.dataset.copy;
      var label = btn.dataset.copyLabel || 'giá trị';
      if (!value || !navigator.clipboard) return;
      navigator.clipboard.writeText(value).then(function () {
        if (window.KshToast) window.KshToast.success('Đã sao chép ' + label);
      }).catch(function () {
        if (window.KshToast) window.KshToast.error('Không thể sao chép');
      });
    });
  });

  // ── Regenerate buttons: confirm modal before submitting form ──────
  document.querySelectorAll('.invite-panel .invite-regen-form').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      // If already confirmed, allow native submit through.
      if (form.dataset.confirmed === '1') return;
      e.preventDefault();
      var btn = form.querySelector('.regen-btn');
      var title = (btn && btn.dataset.confirmTitle) || 'Tạo mã mới';
      var body = (btn && btn.dataset.confirmBody)
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
  });

})();
