(function () {
  'use strict';

  function initQuill(root) {
    var targetId = root.getAttribute('data-target-id');
    var textarea = document.getElementById(targetId);
    if (!textarea || typeof Quill === 'undefined') {
      return null;
    }
    var quill = new Quill(root, {
      theme: 'snow',
      modules: {
        toolbar: [
          [{ header: [2, 3, false] }],
          ['bold', 'italic', 'underline'],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['link', 'clean']
        ]
      }
    });
    var initial = root.getAttribute('data-initial');
    if (initial) {
      quill.root.innerHTML = initial;
      textarea.value = initial;
    }
    quill.on('text-change', function () {
      textarea.value = quill.root.innerHTML;
    });
    return quill;
  }

  function initOptionEditor(root) {
    var textarea = root.parentElement && root.parentElement.querySelector('.qb-option-text');
    if (!textarea || typeof Quill === 'undefined') return null;
    var quill = new Quill(root, { theme: 'snow', modules: { toolbar: false } });
    var initial = root.getAttribute('data-initial');
    if (initial) quill.root.innerHTML = initial;
    textarea.value = initial || '';
    quill.on('text-change', function () {
      textarea.value = quill.getText().trim() ? quill.root.innerHTML : '';
    });
    return quill;
  }

  // Reflect each answer's correct-checkbox state onto its .lf-option card so
  // the letter badge turns green (checkbox itself is visually hidden by CSS).
  function syncOption(option) {
    var checkbox = option.querySelector('.lf-o-correct');
    if (!checkbox) {
      return;
    }
    option.classList.toggle('is-correct', checkbox.checked);
  }

  function initOptions(root) {
    var typeSelect = root.querySelector('#questionType');
    var options = Array.prototype.slice.call(root.querySelectorAll('.lf-option'));
    if (!options.length) {
      return;
    }

    options.forEach(function (option) {
      var checkbox = option.querySelector('.lf-o-correct');
      if (!checkbox) {
        return;
      }
      syncOption(option);
      checkbox.addEventListener('change', function () {
        // MCQ allows a single correct answer: clear siblings when one is picked.
        if (checkbox.checked && typeSelect && typeSelect.value === 'MCQ') {
          options.forEach(function (other) {
            if (other === option) {
              return;
            }
            var otherBox = other.querySelector('.lf-o-correct');
            if (otherBox && otherBox.checked) {
              otherBox.checked = false;
              syncOption(other);
            }
          });
        }
        syncOption(option);
      });
    });
  }

  function initLessonHierarchy(root) {
    var subject = root.querySelector('#subjectId');
    var lesson = root.querySelector('#lessonTemplateId');
    if (!subject || !lesson) return;

    function syncLessons() {
      var selectedSubject = subject.value;
      Array.prototype.forEach.call(
        lesson.querySelectorAll('option[data-subject-id]'), function (option) {
          option.disabled = option.dataset.subjectId !== selectedSubject;
        });
      var current = lesson.options[lesson.selectedIndex];
      if (current && current.disabled) lesson.value = '';
      lesson.dispatchEvent(new Event('change', { bubbles: true }));
    }

    subject.addEventListener('change', syncLessons);
    syncLessons();
  }

  function initProgressiveOptions(root) {
    var rows = Array.prototype.slice.call(root.querySelectorAll('[data-qb-option-row]'));
    var add = root.querySelector('[data-qb-option-add]');
    if (!add) return;
    function syncButton() {
      add.hidden = !rows.some(function (row) { return row.hidden; });
    }
    add.addEventListener('click', function () {
      var next = rows.find(function (row) { return row.hidden; });
      if (!next) return;
      next.hidden = false;
      var editor = next.querySelector('.ql-editor');
      if (editor) editor.focus();
      syncButton();
    });
    syncButton();
  }

  function initQuestionBankForm(root) {
    root = root || document;
    if (root.dataset && root.dataset.qbFormReady === 'true') return;
    if (root.dataset) root.dataset.qbFormReady = 'true';
    var editors = root.querySelectorAll('[data-qb-editor]');
    Array.prototype.forEach.call(editors, initQuill);
    var optionEditors = root.querySelectorAll('[data-qb-option-editor]');
    Array.prototype.forEach.call(optionEditors, initOptionEditor);
    initOptions(root);
    initLessonHierarchy(root);
    initProgressiveOptions(root);
  }

  window.initQuestionBankForm = initQuestionBankForm;
  document.addEventListener('DOMContentLoaded', function () {
    initQuestionBankForm(document);
  });
})();
