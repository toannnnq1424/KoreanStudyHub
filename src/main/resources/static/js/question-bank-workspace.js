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
  });
})();
