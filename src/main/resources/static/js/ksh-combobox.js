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
    if (select.id) {
      var label = document.querySelector('label[for="' + select.id + '"]');
      if (label) input.setAttribute('aria-label', label.textContent.replace('*', '').trim());
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
      activeIndex = -1;
    }

    function render(items) {
      list.textContent = '';
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
        li.setAttribute('role', 'option');
        li.dataset.value = item.value;
        li.textContent = item.label;
        if (item.value === select.value) li.setAttribute('aria-selected', 'true');
        if (i === activeIndex) li.classList.add(ACTIVE);
        list.appendChild(li);
      });
    }

    function open(query) {
      var needle = fold(query || '');
      filtered = needle
        ? options.filter(function (option) { return option.needle.indexOf(needle) !== -1; })
        : options.slice();
      activeIndex = filtered.length ? 0 : -1;
      render(filtered);
      list.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    }

    function choose(value) {
      select.value = value;
      input.value = labelFor(value);
      clear.hidden = !value;
      select.dispatchEvent(new Event('change', { bubbles: true }));
      close();
    }

    function moveActive(delta) {
      if (list.hidden) {
        open(input.value === labelFor(select.value) ? '' : input.value);
        return;
      }
      if (!filtered.length) return;
      activeIndex = (activeIndex + delta + filtered.length) % filtered.length;
      render(filtered);
      var element = list.children[activeIndex];
      if (element && element.scrollIntoView) element.scrollIntoView({ block: 'nearest' });
    }

    if (select.value) {
      input.value = labelFor(select.value);
      clear.hidden = false;
    }

    input.addEventListener('focus', function () { open(''); });
    input.addEventListener('input', function () {
      select.value = '';
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
    select.addEventListener('invalid', function () {
      input.setAttribute('aria-invalid', 'true');
      input.focus();
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
