/**
 * Assignments feature — client-side behaviours.
 *
 * Responsibilities:
 *   1. Drain #flash-data and fire KshToast notifications.
 *   2. Confirm dialogs for destructive/irreversible actions (publish, close).
 */
(function () {
  'use strict';

  // ── Flash → KshToast ──────────────────────────────────────────────────

  /**
   * Reads data-flash-* attributes from #flash-data and fires KshToast.
   * Pattern matches admin.js and class-detail.js to stay consistent.
   */
  function drainFlash() {
    var el = document.getElementById('flash-data');
    if (!el) return;
    var success = el.getAttribute('data-flash-success');
    var error   = el.getAttribute('data-flash-error');
    if (success && success.trim()) {
      window.KshToast && window.KshToast.success(success.trim());
    }
    if (error && error.trim()) {
      window.KshToast && window.KshToast.error(error.trim());
    }
  }

  // ── Confirm dialogs ───────────────────────────────────────────────────

  /**
   * Attaches a confirm dialog to forms with data-confirm attribute.
   * Keeps confirm logic out of inline onclick handlers.
   */
  function bindConfirmForms() {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) {
          e.preventDefault();
        }
      });
    });
  }

  // ── Init ──────────────────────────────────────────────────────────────

  document.addEventListener('DOMContentLoaded', function () {
    drainFlash();
    bindConfirmForms();
  });
}());
