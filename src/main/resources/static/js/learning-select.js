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
    // menu will be rendered into document.body to avoid ancestor overflow clipping
    menu.style.position = 'fixed';
    menu.style.display = 'none';
    menu.style.zIndex = '1200';

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

    function detachMenu() {
      if (!document.body.contains(menu)) document.body.appendChild(menu);
    }

    function close(focusTrigger) {
      wrapper.classList.remove('is-open');
      trigger.setAttribute('aria-expanded', 'false');
      menu.style.display = 'none';
      if (focusTrigger) trigger.focus();
    }

    function positionMenu() {
      const rect = trigger.getBoundingClientRect();
      const left = Math.max(8, rect.left);
      const top = rect.bottom + 7;
      menu.style.left = left + 'px';
      menu.style.top = top + 'px';
      menu.style.width = rect.width + 'px';
      menu.style.right = 'auto';
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
      detachMenu();
      positionMenu();
      menu.style.display = 'grid';

      if (search) {
        search.value = '';
        menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
          button.hidden = false;
        });
        // focus the search input after opening
        window.setTimeout(function () { search.focus(); }, 0);
      } else {
        const active = menu.querySelector('[aria-selected="true"]') || optionButtons()[0];
        if (active) active.focus();
      }
    }

    function rebuildOptions() {
      // Clear menu but keep search at top when present
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

    trigger.addEventListener('click', function (ev) {
      ev.stopPropagation();
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

    // Insert wrapper and trigger into DOM; keep native select hidden inside wrapper
    select.parentNode.insertBefore(wrapper, select);
    wrapper.appendChild(trigger);
    select.classList.add('ksh-native-select');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');
    wrapper.appendChild(select);

    // Append menu to body (detached from wrapper) so it won't be clipped
    document.body.appendChild(menu);
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

    // Reposition on window resize/scroll while open
    const reposition = function () { if (wrapper.classList.contains('is-open')) positionMenu(); };
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
  }

  document.addEventListener('click', function (event) {
    document.querySelectorAll('.ksh-select.is-open').forEach(function (wrapper) {
      if (!wrapper.contains(event.target)) {
        wrapper.classList.remove('is-open');
        const trigger = wrapper.querySelector('.ksh-select-trigger');
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
        // hide associated menu
        const menu = document.querySelector('.ksh-select-menu');
        if (menu) menu.style.display = 'none';
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
