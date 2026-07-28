/**
 * Assignments feature — client-side behaviours.
 *
 * Responsibilities:
 *   1. Confirm dialogs for destructive/irreversible actions (publish, close).
 *
 * Server flash payloads are drained centrally by notifications.js.
 */
(function () {
  'use strict';

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
    bindConfirmForms();
  });
}());
