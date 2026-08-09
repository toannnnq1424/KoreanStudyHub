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
document.addEventListener('DOMContentLoaded', () => {
  const search = document.querySelector('[data-assignment-search]');
  const status = document.querySelector('[data-assignment-status]');
  const sort = document.querySelector('[data-assignment-sort]');
  const list = document.querySelector('[data-assignment-list]');
  const empty = document.querySelector('[data-assignment-empty]');
  const rows = [...document.querySelectorAll('[data-assignment-row]')];
  const originalOrder = new Map(rows.map((row, index) => [row, index]));
  const apply = () => {
    const query = (search?.value || '').trim().toLocaleLowerCase('vi');
    const state = status?.value || 'ALL';
    let visible = 0;
    rows.forEach((row) => {
      const matchesText = !query || row.dataset.assignmentRow.includes(query);
      const matchesState = state === 'ALL' || row.dataset.assignmentStatus === state;
      row.hidden = !(matchesText && matchesState);
      if (!row.hidden) visible += 1;
    });
    if (empty) empty.hidden = visible !== 0;
  };
  const applySort = () => {
    if (!list) return;
    const mode = sort?.value || 'NEWEST';
    rows.sort((a, b) => {
      if (mode === 'TITLE') return (a.dataset.assignmentTitle || '').localeCompare(b.dataset.assignmentTitle || '', 'vi');
      if (mode === 'DUE') return (a.dataset.assignmentDue || '9999').localeCompare(b.dataset.assignmentDue || '9999');
      return originalOrder.get(a) - originalOrder.get(b);
    }).forEach((row) => list.insertBefore(row, empty || null));
  };
  search?.addEventListener('input', apply);
  status?.addEventListener('change', apply);
  sort?.addEventListener('change', applySort);
  document.querySelectorAll('[data-assignment-status-shortcut]').forEach((shortcut) => {
    shortcut.addEventListener('click', () => {
      document.querySelectorAll('[data-assignment-status-shortcut]').forEach((item) => item.classList.remove('is-active'));
      shortcut.classList.add('is-active');
      if (status) status.value = shortcut.dataset.assignmentStatusShortcut;
      apply();
    });
  });

  const detail = document.querySelector('[data-assignment-detail]');
  const setDetail = (row) => {
    if (!detail || !row) return;
    rows.forEach((item) => item.classList.toggle('is-selected', item === row));
    detail.querySelector('[data-detail-title]').textContent = row.dataset.assignmentTitle || '';
    detail.querySelector('[data-detail-status]').textContent = {DRAFT: 'Nháp', PUBLISHED: 'Đang giao', CLOSED: 'Đã đóng'}[row.dataset.assignmentStatus] || '—';
    detail.querySelector('[data-detail-due]').textContent = row.dataset.assignmentDue ? new Date(row.dataset.assignmentDue).toLocaleString('vi-VN') : 'Không giới hạn';
    detail.querySelector('[data-detail-max]').textContent = (row.dataset.assignmentMax || '0') + ' điểm';
    detail.querySelector('[data-detail-submissions]').textContent = (row.dataset.assignmentSubmissions || '0') + '/' + (row.dataset.assignmentStudents || '0') + ' học sinh';
    const base = window.assignmentRoutes?.base || '';
    const id = row.dataset.assignmentId;
    const submissions = detail.querySelector('[data-detail-submissions-link]');
    const edit = detail.querySelector('[data-detail-edit-link]');
    const publish = detail.querySelector('[data-detail-publish-form]');
    const close = detail.querySelector('[data-detail-close-form]');
    submissions.href = `${base}/${id}/submissions`;
    edit.href = `${base}/${id}/edit`;
    edit.hidden = row.dataset.assignmentStatus !== 'DRAFT';
    publish.action = `${base}/${id}/publish`;
    publish.hidden = row.dataset.assignmentStatus !== 'DRAFT';
    close.action = `${base}/${id}/close`;
    close.hidden = row.dataset.assignmentStatus !== 'PUBLISHED';
  };
  rows.forEach((row) => {
    row.addEventListener('click', () => setDetail(row));
    row.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setDetail(row); } });
  });
  setDetail(rows.find((row) => row.dataset.assignmentSelected === 'true') || rows[0]);
});
