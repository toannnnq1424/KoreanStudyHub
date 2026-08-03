(function () {
  'use strict';

  function enhance(select, index) {
    if (select.multiple || select.dataset.kshSelectReady === 'true') return;
    select.dataset.kshSelectReady = 'true';

    const wrapper = document.createElement('div');
    wrapper.className = 'ksh-select';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'ksh-select-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    const label = select.labels && select.labels[0];
    trigger.setAttribute('aria-label', label ? label.textContent.trim() : 'Chọn giá trị');
    trigger.disabled = select.disabled;

    const menu = document.createElement('div');
    menu.className = 'ksh-select-menu';
    menu.id = 'ksh-select-menu-' + index;
    menu.setAttribute('role', 'listbox');
    trigger.setAttribute('aria-controls', menu.id);

    const searchable = select.hasAttribute('data-ksh-searchable') || select.options.length > 8;
    const search = searchable ? document.createElement('input') : null;
    if (search) {
      search.type = 'search';
      search.className = 'ksh-select-search';
      search.placeholder = select.dataset.kshSearchPlaceholder || 'Gõ để tìm…';
      search.setAttribute('aria-label', search.placeholder);
      search.autocomplete = 'off';
      search.addEventListener('input', function () {
        const needle = search.value.trim().toLocaleLowerCase('vi');
        menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
          button.hidden = Boolean(needle) && !button.textContent
            .toLocaleLowerCase('vi').includes(needle);
        });
      });
    }

    function optionButtons() {
      return Array.from(menu.querySelectorAll('.ksh-select-option:not(:disabled)'));
    }

    function sync() {
      const selected = select.options[select.selectedIndex] || select.options[0];
      trigger.textContent = selected ? selected.textContent : 'Chọn';
      menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
        const active = selected && button.dataset.value === selected.value;
        button.setAttribute('aria-selected', active ? 'true' : 'false');
      });
    }

    function close(focusTrigger) {
      wrapper.classList.remove('is-open');
      trigger.setAttribute('aria-expanded', 'false');
      if (focusTrigger) trigger.focus();
    }

    function open() {
      document.querySelectorAll('.ksh-select.is-open').forEach(function (other) {
        if (other !== wrapper) {
          other.classList.remove('is-open');
          const otherTrigger = other.querySelector('.ksh-select-trigger');
          if (otherTrigger) otherTrigger.setAttribute('aria-expanded', 'false');
        }
      });
      wrapper.classList.add('is-open');
      trigger.setAttribute('aria-expanded', 'true');
      if (search) {
        search.value = '';
        menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
          button.hidden = false;
        });
        search.focus();
      } else {
        const active = menu.querySelector('[aria-selected="true"]') || optionButtons()[0];
        if (active) active.focus();
      }
    }

    function rebuildOptions() {
      menu.replaceChildren();
      if (search) menu.appendChild(search);
      Array.from(select.options).forEach(function (option) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'ksh-select-option';
        button.dataset.value = option.value;
        button.textContent = option.textContent;
        button.disabled = option.disabled;
        button.setAttribute('role', 'option');
        button.addEventListener('click', function () {
          select.value = option.value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          sync();
          close(true);
        });
        menu.appendChild(button);
      });
      trigger.disabled = select.disabled;
      sync();
    }

    trigger.addEventListener('click', function () {
      wrapper.classList.contains('is-open') ? close(false) : open();
    });
    wrapper.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        close(true);
        return;
      }
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
      event.preventDefault();
      if (!wrapper.classList.contains('is-open')) {
        open();
        return;
      }
      const buttons = optionButtons();
      const current = buttons.indexOf(document.activeElement);
      const delta = event.key === 'ArrowDown' ? 1 : -1;
      const next = current < 0 ? 0 : (current + delta + buttons.length) % buttons.length;
      if (buttons[next]) buttons[next].focus();
    });
    select.addEventListener('change', sync);
    if (select.form) {
      select.form.addEventListener('reset', function () {
        window.setTimeout(sync, 0);
      });
    }

    select.parentNode.insertBefore(wrapper, select);
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);
    select.classList.add('ksh-native-select');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');
    wrapper.appendChild(select);
    rebuildOptions();

    if (typeof MutationObserver === 'function') {
      new MutationObserver(rebuildOptions).observe(select, {
        childList: true,
        subtree: true,
        characterData: true,
        attributes: true,
        attributeFilter: ['disabled', 'selected', 'label']
      });
    }
  }

  document.addEventListener('click', function (event) {
    document.querySelectorAll('.ksh-select.is-open').forEach(function (wrapper) {
      if (!wrapper.contains(event.target)) {
        wrapper.classList.remove('is-open');
        const trigger = wrapper.querySelector('.ksh-select-trigger');
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
      }
    });
  });

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('select[data-ksh-select]').forEach(enhance);

    document.querySelectorAll('[data-search-checklist]').forEach(function (picker) {
      const search = picker.querySelector('.ksh-checklist-search');
      const options = Array.from(picker.querySelectorAll('.ksh-checklist-options > label'));
      if (!search) return;
      function filter() {
        const needle = search.value.trim().toLocaleLowerCase('vi');
        options.forEach(function (option) {
          const haystack = (option.dataset.searchText || option.textContent || '')
            .toLocaleLowerCase('vi');
          option.hidden = Boolean(needle) && !haystack.includes(needle);
        });
      }
      search.addEventListener('input', filter);
      search.addEventListener('focus', function () { picker.classList.add('is-open'); });
      picker.addEventListener('focusout', function () {
        window.setTimeout(function () {
          if (!picker.contains(document.activeElement)) picker.classList.remove('is-open');
        }, 0);
      });
    });

    document.querySelectorAll('input[data-link-filter]').forEach(function (search) {
      const container = document.querySelector(search.dataset.linkFilter);
      if (!container) return;
      const links = Array.from(container.querySelectorAll('a'));
      search.addEventListener('input', function () {
        const needle = search.value.trim().toLocaleLowerCase('vi');
        links.forEach(function (link) {
          const haystack = (link.dataset.searchText || link.textContent || '')
            .toLocaleLowerCase('vi');
          link.hidden = Boolean(needle) && !haystack.includes(needle);
        });
      });
    });

    document.querySelectorAll('input[data-item-filter]').forEach(function (search) {
      const container = document.querySelector(search.dataset.itemFilter);
      if (!container) return;
      const items = Array.from(container.querySelectorAll('[data-filter-item]'));
      search.addEventListener('input', function () {
        const needle = search.value.trim().toLocaleLowerCase('vi');
        items.forEach(function (item) {
          const haystack = (item.dataset.searchText || item.textContent || '')
            .toLocaleLowerCase('vi');
          item.hidden = Boolean(needle) && !haystack.includes(needle);
        });
      });
    });
  });
})();
