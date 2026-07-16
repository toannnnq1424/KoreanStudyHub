(() => {
  'use strict';

  const tabLists = document.querySelectorAll('[data-result-tabs]');

  function activate(tabList, nextTab, focus) {
    const tabs = Array.from(tabList.querySelectorAll(':scope > [role="tab"]'));
    if (!tabs.includes(nextTab)) return;

    tabs.forEach((tab) => {
      const selected = tab === nextTab;
      const panelId = tab.dataset.resultTarget;
      const panel = panelId ? document.getElementById(panelId) : null;

      tab.classList.toggle('is-active', selected);
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (panel) panel.hidden = !selected;
    });

    if (focus) nextTab.focus();
  }

  tabLists.forEach((tabList) => {
    const tabs = Array.from(tabList.querySelectorAll(':scope > [role="tab"]'));
    if (tabs.length === 0) return;

    const initial = tabs.find((tab) => tab.getAttribute('aria-selected') === 'true') || tabs[0];
    activate(tabList, initial, false);

    tabs.forEach((tab) => {
      tab.addEventListener('click', () => activate(tabList, tab, false));
      tab.addEventListener('keydown', (event) => {
        const currentIndex = tabs.indexOf(tab);
        let nextIndex = currentIndex;

        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
          nextIndex = (currentIndex + 1) % tabs.length;
        } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
          nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
        } else if (event.key === 'Home') {
          nextIndex = 0;
        } else if (event.key === 'End') {
          nextIndex = tabs.length - 1;
        } else {
          return;
        }

        event.preventDefault();
        activate(tabList, tabs[nextIndex], true);
      });
    });
  });
})();
