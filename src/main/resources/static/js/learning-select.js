(function () {
  'use strict';

  let activeInstance = null;
  let positionFrame = null;

  function scheduleActivePosition() {
    if (!activeInstance || positionFrame !== null) return;
    positionFrame = window.requestAnimationFrame(function () {
      positionFrame = null;
      if (!activeInstance) return;
      if (!activeInstance.wrapper.isConnected || !activeInstance.menu.isConnected) {
        activeInstance.close(false);
        return;
      }
      activeInstance.positionMenu();
    });
  }

  function enhance(select, index) {
    if (select.multiple || select.dataset.kshSelectReady === 'true') return;
    select.dataset.kshSelectReady = 'true';

    const wrapper = document.createElement('div');
    wrapper.className = 'ksh-select';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'ksh-select-trigger';
    trigger.id = 'ksh-select-trigger-' + index;
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    const label = select.labels && select.labels[0];
    trigger.setAttribute('aria-label', label ? label.textContent.trim() : 'Chọn giá trị');
    trigger.disabled = select.disabled;

    const menu = document.createElement('div');
    menu.className = 'ksh-select-menu ksh-select-menu--portal';
    menu.id = 'ksh-select-menu-' + index;
    menu.setAttribute('role', 'listbox');
    menu.setAttribute('aria-labelledby', trigger.id);
    menu.setAttribute('aria-hidden', 'true');
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
      return Array.from(menu.querySelectorAll('.ksh-select-option:not(:disabled)'))
        .filter(function (button) { return !button.hidden; });
    }

    function isOpen() {
      return activeInstance === instance;
    }

    function sync() {
      const selected = select.options[select.selectedIndex] || select.options[0];
      trigger.textContent = selected ? selected.textContent : 'Chọn';
      menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
        const active = selected && button.dataset.value === selected.value;
        button.setAttribute('aria-selected', active ? 'true' : 'false');
      });
    }

    function positionMenu() {
      if (!isOpen()) return;

      const rect = trigger.getBoundingClientRect();
      const viewportWidth = document.documentElement.clientWidth || window.innerWidth;
      const viewportHeight = document.documentElement.clientHeight || window.innerHeight;
      const viewportMargin = 8;
      const menuGap = 7;
      const availableWidth = Math.max(1, viewportWidth - (viewportMargin * 2));
      const menuWidth = Math.min(rect.width, availableWidth);
      const left = Math.min(
        Math.max(viewportMargin, rect.left),
        Math.max(viewportMargin, viewportWidth - menuWidth - viewportMargin)
      );

      menu.style.width = menuWidth + 'px';
      menu.style.left = left + 'px';
      menu.style.right = 'auto';
      menu.style.maxHeight = '240px';

      const spaceBelow = Math.max(0, viewportHeight - rect.bottom - menuGap - viewportMargin);
      const spaceAbove = Math.max(0, rect.top - menuGap - viewportMargin);
      const naturalHeight = menu.getBoundingClientRect().height;
      const openAbove = spaceBelow < Math.min(naturalHeight, 160) && spaceAbove > spaceBelow;
      const chosenSpace = openAbove ? spaceAbove : spaceBelow;
      menu.style.maxHeight = Math.max(72, Math.min(240, chosenSpace)) + 'px';

      const menuHeight = menu.getBoundingClientRect().height;
      const proposedTop = openAbove
        ? rect.top - menuGap - menuHeight
        : rect.bottom + menuGap;
      const top = Math.min(
        Math.max(viewportMargin, proposedTop),
        Math.max(viewportMargin, viewportHeight - menuHeight - viewportMargin)
      );
      menu.style.top = top + 'px';
      menu.dataset.placement = openAbove ? 'top' : 'bottom';
    }

    function close(focusTrigger) {
      wrapper.classList.remove('is-open');
      menu.classList.remove('is-open');
      menu.setAttribute('aria-hidden', 'true');
      trigger.setAttribute('aria-expanded', 'false');
      if (activeInstance === instance) activeInstance = null;
      if (focusTrigger && trigger.isConnected) trigger.focus();
    }

    function focusOption(preference) {
      const buttons = optionButtons();
      if (buttons.length === 0) return;
      if (preference === 'last') {
        buttons[buttons.length - 1].focus();
        return;
      }
      const selected = buttons.find(function (button) {
        return button.getAttribute('aria-selected') === 'true';
      });
      (selected || buttons[0]).focus();
    }

    function open(preference) {
      if (select.disabled) return;
      if (activeInstance && activeInstance !== instance) activeInstance.close(false);

      activeInstance = instance;
      wrapper.classList.add('is-open');
      menu.classList.add('is-open');
      menu.setAttribute('aria-hidden', 'false');
      trigger.setAttribute('aria-expanded', 'true');
      if (search) {
        search.value = '';
        menu.querySelectorAll('.ksh-select-option').forEach(function (button) {
          button.hidden = false;
        });
      }
      positionMenu();

      if (search) {
        search.focus();
      } else {
        focusOption(preference);
      }
    }

    function moveOptionFocus(delta) {
      const buttons = optionButtons();
      if (buttons.length === 0) return;
      const current = buttons.indexOf(document.activeElement);
      const next = current < 0
        ? (delta > 0 ? 0 : buttons.length - 1)
        : (current + delta + buttons.length) % buttons.length;
      buttons[next].focus();
    }

    function focusAdjacentControl(backwards) {
      const selector = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled]):not([type="hidden"])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[contenteditable="true"]',
        '[tabindex]:not([tabindex="-1"])'
      ].join(',');
      const controls = Array.from(document.querySelectorAll(selector)).filter(function (control) {
        return control !== select
          && !menu.contains(control)
          && control.getClientRects().length > 0
          && control.getAttribute('aria-hidden') !== 'true';
      });
      const triggerIndex = controls.indexOf(trigger);
      const targetIndex = triggerIndex + (backwards ? -1 : 1);
      const target = triggerIndex >= 0 ? controls[targetIndex] : null;
      (target || trigger).focus();
    }

    function handleKeyboard(event) {
      if (event.key === 'Tab' && isOpen() && menu.contains(event.target)) {
        event.preventDefault();
        const backwards = event.shiftKey;
        close(false);
        focusAdjacentControl(backwards);
        return;
      }

      if (event.key === 'Escape' && isOpen()) {
        event.preventDefault();
        close(true);
        return;
      }

      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
      event.preventDefault();
      const delta = event.key === 'ArrowDown' ? 1 : -1;
      if (!isOpen()) {
        open(delta < 0 ? 'last' : 'selected');
        return;
      }
      moveOptionFocus(delta);
    }

    function rebuildOptions() {
      menu.replaceChildren();
      if (search) menu.appendChild(search);
      Array.from(select.options).forEach(function (option, optionIndex) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'ksh-select-option';
        button.id = menu.id + '-option-' + optionIndex;
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
      if (select.disabled && isOpen()) close(false);
      sync();
      if (isOpen()) positionMenu();
    }

    const instance = {
      wrapper: wrapper,
      trigger: trigger,
      menu: menu,
      close: close,
      positionMenu: positionMenu,
      contains: function (target) {
        return wrapper.contains(target) || menu.contains(target);
      }
    };

    trigger.addEventListener('click', function () {
      isOpen() ? close(false) : open('selected');
    });
    wrapper.addEventListener('keydown', handleKeyboard);
    menu.addEventListener('keydown', handleKeyboard);
    select.addEventListener('change', sync);
    if (select.form) {
      select.form.addEventListener('reset', function () {
        window.setTimeout(sync, 0);
      });
    }

    select.parentNode.insertBefore(wrapper, select);
    wrapper.appendChild(trigger);
    select.classList.add('ksh-native-select');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');
    wrapper.appendChild(select);
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
  }

  document.addEventListener('pointerdown', function (event) {
    if (activeInstance && !activeInstance.contains(event.target)) {
      activeInstance.close(false);
    }
  });

  document.addEventListener('focusin', function (event) {
    if (activeInstance && !activeInstance.contains(event.target)) {
      activeInstance.close(false);
    }
  });

  window.addEventListener('resize', scheduleActivePosition);
  window.addEventListener('scroll', scheduleActivePosition, true);

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
