/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Lecturer teaching dashboard behavior
   Loaded by /lecturer/dashboard. Requires app.js (KshToast).
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  // Flash → toast on page load (shared pattern with admin.js / classes.js).
  var flashData = document.getElementById('flash-data');
  if (flashData && window.KshToast) {
    var ok = flashData.dataset.flashSuccess;
    var err = flashData.dataset.flashError;
    if (ok) window.KshToast.success(ok);
    if (err) window.KshToast.error(err);
  }
})();
