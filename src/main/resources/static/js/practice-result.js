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

  const writingFilters = document.querySelectorAll('[data-writing-diagnostic-filter]');

  writingFilters.forEach((filter) => {
    filter.addEventListener('click', () => {
      const panel = filter.closest('[role="tabpanel"]');
      if (!panel) return;

      const scopedFilters = Array.from(
        panel.querySelectorAll('[data-writing-diagnostic-filter]')
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-writing-feature]')
      ).filter((item) => !item.hasAttribute('data-writing-diagnostic-filter'));
      const feature = filter.dataset.writingFeature;
      const activateFilter = filter.getAttribute('aria-pressed') !== 'true';

      scopedFilters.forEach((item) => item.setAttribute('aria-pressed', 'false'));
      findings.forEach((finding) => {
        finding.hidden = activateFilter
          && finding.dataset.writingFeature !== feature;
      });

      if (!activateFilter) return;

      filter.setAttribute('aria-pressed', 'true');
      const firstMatch = findings.find(
        (finding) => finding.dataset.writingFeature === feature
      );
      if (firstMatch) {
        firstMatch.focus({ preventScroll: true });
        firstMatch.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      }
    });
  });

  const speakingFilters = document.querySelectorAll(
    '[data-speaking-diagnostic-filter]'
  );

  speakingFilters.forEach((filter) => {
    filter.addEventListener('click', () => {
      const panel = filter.closest('[role="tabpanel"]');
      if (!panel) return;

      const scopedFilters = Array.from(
        panel.querySelectorAll('[data-speaking-diagnostic-filter]')
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-speaking-feature]')
      ).filter((item) => !item.hasAttribute('data-speaking-diagnostic-filter'));
      const feature = filter.dataset.speakingFeature;
      const activateFilter = filter.getAttribute('aria-pressed') !== 'true';

      scopedFilters.forEach((item) => item.setAttribute('aria-pressed', 'false'));
      findings.forEach((finding) => {
        finding.hidden = activateFilter
          && finding.dataset.speakingFeature !== feature;
      });

      if (!activateFilter) return;

      filter.setAttribute('aria-pressed', 'true');
      const firstMatch = findings.find(
        (finding) => finding.dataset.speakingFeature === feature
      );
      if (firstMatch) {
        firstMatch.focus({ preventScroll: true });
        firstMatch.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
      }
    });
  });
})();
