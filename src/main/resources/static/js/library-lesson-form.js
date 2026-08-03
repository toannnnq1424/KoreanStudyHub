(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    const form = document.querySelector('[data-library-lesson-form]');
    if (!form) return;
    const type = form.querySelector('[data-library-content-type]');
    if (!type) return;

    function syncContentSection() {
      form.querySelectorAll('[data-content-section]').forEach(function (section) {
        const active = section.dataset.contentSection === type.value;
        section.hidden = !active;
        section.querySelectorAll('input, select, textarea').forEach(function (control) {
          control.disabled = !active;
        });
      });
    }

    type.addEventListener('change', syncContentSection);
    syncContentSection();
  });
})();
