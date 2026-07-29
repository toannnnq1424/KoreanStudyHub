/* Progressive enhancement for native KSH select controls. */

(function () {
  'use strict';

  var ACTIVE = 'is-active';

  function fold(text) {
    return text
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/đ/g, 'd');
  }

  function enhance(select) {
    if (select.dataset.comboboxReady === 'true') return;
    select.dataset.comboboxReady = 'true';

    var options = Array.prototype.slice.call(select.options)
      .filter(function (opt) { return opt.value !== ''; })
      .map(function (opt) {
        return { value: opt.value, label: opt.textContent.trim(), needle: fold(opt.textContent) };
      });

    var placeholder = select.dataset.comboboxPlaceholder || 'Gõ để tìm…';
    var emptyText = select.dataset.comboboxEmpty || 'Không tìm thấy kết quả';

    var root = document.createElement('div');
    root.className = 'ksh-combo';

    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'ksh-combo-input';
    input.placeholder = placeholder;
    input.autocomplete = 'off';
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-expanded', 'false');
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-required', select.required ? 'true' : 'false');
    if (select.id) {
      var label = document.querySelector('label[for="' + select.id + '"]');
      input.id = select.id + '-combobox';
      if (label) label.setAttribute('for', input.id);
    }

    var list = document.createElement('ul');
    list.className = 'ksh-combo-list';
    list.setAttribute('role', 'listbox');
    list.hidden = true;

    var listId = (select.id || 'combo') + '-list';
    list.id = listId;
    input.setAttribute('aria-controls', listId);

    var clear = document.createElement('button');
    clear.type = 'button';
    clear.className = 'ksh-combo-clear';
    clear.setAttribute('aria-label', 'Xoá lựa chọn');
    clear.textContent = '×';
    clear.hidden = true;

    select.parentNode.insertBefore(root, select);
    root.appendChild(input);
    root.appendChild(clear);
    root.appendChild(list);
    root.appendChild(select);
    select.classList.add('ksh-combo-native');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');

    var filtered = options.slice();
    var activeIndex = -1;

    function labelFor(value) {
      for (var i = 0; i < options.length; i++) {
        if (options[i].value === value) return options[i].label;
      }
      return '';
    }

    function close() {
      list.hidden = true;
      input.setAttribute('aria-expanded', 'false');
      input.removeAttribute('aria-activedescendant');
      activeIndex = -1;
    }

    function render(items) {
      list.textContent = '';
      input.removeAttribute('aria-activedescendant');
      if (!items.length) {
        var empty = document.createElement('li');
        empty.className = 'ksh-combo-empty';
        empty.textContent = emptyText;
        list.appendChild(empty);
        return;
      }
      items.forEach(function (item, i) {
        var li = document.createElement('li');
        li.className = 'ksh-combo-option';
        li.id = listId + '-option-' + i;
        li.setAttribute('role', 'option');
        li.dataset.value = item.value;
        li.textContent = item.label;
        if (item.value === select.value) li.setAttribute('aria-selected', 'true');
        if (i === activeIndex) {
          li.classList.add(ACTIVE);
          input.setAttribute('aria-activedescendant', li.id);
        }
        list.appendChild(li);
      });
    }

    function open(query) {
      var needle = fold(query || '');
      filtered = needle
        ? options.filter(function (option) { return option.needle.indexOf(needle) !== -1; })
        : options.slice();
      activeIndex = -1;
      render(filtered);
      list.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    }

    function choose(value) {
      select.value = value;
      input.value = labelFor(value);
      clear.hidden = !value;
      input.removeAttribute('aria-invalid');
      select.dispatchEvent(new Event('change', { bubbles: true }));
      close();
    }

    function moveActive(delta) {
      if (list.hidden) {
        open(input.value === labelFor(select.value) ? '' : input.value);
        return;
      }
      if (!filtered.length) return;
      if (activeIndex < 0) {
        activeIndex = delta > 0 ? 0 : filtered.length - 1;
      } else {
        activeIndex = (activeIndex + delta + filtered.length) % filtered.length;
      }
      render(filtered);
      var element = list.children[activeIndex];
      if (element && element.scrollIntoView) element.scrollIntoView({ block: 'nearest' });
    }

    if (select.value) {
      input.value = labelFor(select.value);
      clear.hidden = false;
    }

    input.addEventListener('focus', function () {
      input.select();
      open('');
    });
    input.addEventListener('click', function () {
      if (list.hidden) {
        open(input.value === labelFor(select.value) ? '' : input.value);
      }
    });
    input.addEventListener('input', function () {
      select.value = '';
      input.removeAttribute('aria-invalid');
      clear.hidden = !input.value;
      open(input.value);
    });
    input.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        moveActive(1);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        moveActive(-1);
      } else if (event.key === 'Enter') {
        if (!list.hidden && activeIndex >= 0 && filtered[activeIndex]) {
          event.preventDefault();
          choose(filtered[activeIndex].value);
        }
      } else if (event.key === 'Escape') {
        close();
      }
    });
    list.addEventListener('mousedown', function (event) {
      var option = event.target.closest('.ksh-combo-option');
      if (!option) return;
      event.preventDefault();
      choose(option.dataset.value);
    });
    clear.addEventListener('click', function () {
      select.value = '';
      input.value = '';
      clear.hidden = true;
      input.removeAttribute('aria-invalid');
      select.dispatchEvent(new Event('change', { bubbles: true }));
      input.focus();
      open('');
    });
    input.addEventListener('blur', function () {
      window.setTimeout(function () {
        input.value = select.value ? labelFor(select.value) : '';
        clear.hidden = !select.value;
        close();
      }, 120);
    });
    select.addEventListener('invalid', function (event) {
      event.preventDefault();
      input.setAttribute('aria-invalid', 'true');
      input.focus();
    });
    select.addEventListener('change', function () {
      input.value = select.value ? labelFor(select.value) : '';
      clear.hidden = !select.value;
      if (select.value) input.removeAttribute('aria-invalid');
    });
  }

  function enhanceAll(scope) {
    var root = scope || document;
    Array.prototype.slice.call(root.querySelectorAll('select[data-combobox]')).forEach(enhance);
  }

  window.KshCombobox = { enhance: enhance, enhanceAll: enhanceAll };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { enhanceAll(); });
  } else {
    enhanceAll();
  }
})();
