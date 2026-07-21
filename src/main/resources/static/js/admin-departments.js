/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Admin Departments page behaviour
   Flash → toast drain and row toggle (Hiện/Ẩn). Dropdown menus come from app.js.
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  var flashData = document.getElementById('flash-data');
  if (flashData && window.KshToast) {
    if (flashData.dataset.flashSuccess) window.KshToast.success(flashData.dataset.flashSuccess);
    if (flashData.dataset.flashError) window.KshToast.error(flashData.dataset.flashError);
  }

  document.addEventListener('click', function (ev) {
    var btn = ev.target.closest('button[data-action="toggle"]');
    if (!btn) return;
    var id = btn.getAttribute('data-dept-id');
    if (!id) return;
    var form = document.getElementById('form-toggle-' + id);
    if (form) form.submit();
  });
})();
