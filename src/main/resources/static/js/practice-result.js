(() => {
  'use strict';
  const reducedMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)'
  );

  const tabLists = document.querySelectorAll('[data-result-tabs]');

  function resetDiagnosticState(review) {
    if (!review) return;

    review.querySelectorAll(
      '[data-writing-diagnostic-filter], [data-speaking-diagnostic-filter]'
    ).forEach((filter) => filter.setAttribute('aria-pressed', 'false'));

    review.querySelectorAll(
      '[data-writing-feature], [data-speaking-feature]'
    ).forEach((item) => {
      if (!item.hasAttribute('data-writing-diagnostic-filter')
          && !item.hasAttribute('data-speaking-diagnostic-filter')) {
        item.hidden = false;
      }
    });

    review.querySelectorAll(
      '.prd-writing-inline-annotation, .prd-speaking-inline-annotation'
    ).forEach((annotation) => {
      annotation.classList.remove('is-selected', 'is-muted');
    });

    review.querySelectorAll(
      '[data-writing-filter-status], [data-speaking-filter-status]'
    ).forEach((status) => {
      status.textContent = '';
    });
  }

  function activate(tabList, nextTab, focus) {
    const tabs = Array.from(tabList.querySelectorAll(':scope > [role="tab"]'));
    if (!tabs.includes(nextTab)) return;

    const currentTab = tabs.find(
      (tab) => tab.getAttribute('aria-selected') === 'true'
    );
    if (currentTab && currentTab !== nextTab) {
      resetDiagnosticState(
        tabList.closest(
          '[data-writing-active-question], [data-speaking-active-question]'
        )
      );
    }

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
      const review = filter.closest('[data-writing-active-question]');

      const scopedFilters = Array.from(
        panel.querySelectorAll('[data-writing-diagnostic-filter]')
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-writing-feature]')
      ).filter((item) => !item.hasAttribute('data-writing-diagnostic-filter'));
      const annotations = review
        ? Array.from(
          review.querySelectorAll(
            '.prd-writing-inline-annotation[data-writing-feature]'
          )
        )
        : [];
      const feature = filter.dataset.writingFeature;
      const activateFilter = filter.getAttribute('aria-pressed') !== 'true';

      scopedFilters.forEach((item) => item.setAttribute('aria-pressed', 'false'));
      findings.forEach((finding) => {
        finding.hidden = activateFilter
          && finding.dataset.writingFeature !== feature;
      });
      annotations.forEach((annotation) => {
        const selected = activateFilter
          && annotation.dataset.writingFeature === feature;
        annotation.classList.toggle('is-selected', selected);
        annotation.classList.toggle('is-muted', activateFilter && !selected);
      });

      const status = panel.querySelector('[data-writing-filter-status]');
      if (!activateFilter) {
        if (status) status.textContent = '';
        return;
      }

      filter.setAttribute('aria-pressed', 'true');
      const visibleCount = findings.filter(
        (finding) => finding.dataset.writingFeature === feature
      ).length;
      if (status) {
        status.textContent =
          `Đang hiển thị ${visibleCount} phản hồi phù hợp.`;
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
      const review = filter.closest('[data-speaking-active-question]');

      const scopedFilters = Array.from(
        panel.querySelectorAll('[data-speaking-diagnostic-filter]')
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-speaking-feature]')
      ).filter((item) => !item.hasAttribute('data-speaking-diagnostic-filter'));
      const annotations = review
        ? Array.from(
          review.querySelectorAll(
            '.prd-speaking-inline-annotation[data-speaking-feature]'
          )
        )
        : [];
      const feature = filter.dataset.speakingFeature;
      const activateFilter = filter.getAttribute('aria-pressed') !== 'true';

      scopedFilters.forEach((item) => item.setAttribute('aria-pressed', 'false'));
      findings.forEach((finding) => {
        finding.hidden = activateFilter
          && finding.dataset.speakingFeature !== feature;
      });
      annotations.forEach((annotation) => {
        const selected = activateFilter
          && annotation.dataset.speakingFeature === feature;
        annotation.classList.toggle('is-selected', selected);
        annotation.classList.toggle('is-muted', activateFilter && !selected);
      });

      const status = panel.querySelector('[data-speaking-filter-status]');
      if (!activateFilter) {
        if (status) status.textContent = '';
        return;
      }

      filter.setAttribute('aria-pressed', 'true');
      const visibleCount = findings.filter(
        (finding) => finding.dataset.speakingFeature === feature
      ).length;
      if (status) {
        status.textContent =
          `Đang hiển thị ${visibleCount} phản hồi phù hợp.`;
      }
    });
  });

  const annotationSelectors = [
    '.prd-writing-inline-annotation',
    '.prd-speaking-inline-annotation'
  ].join(', ');
  let floatingTooltip = null;
  let floatingAnchor = null;

  function hideAnnotationTooltip() {
    if (floatingTooltip) floatingTooltip.remove();
    floatingTooltip = null;
    floatingAnchor = null;
  }

  function positionAnnotationTooltip() {
    if (!floatingTooltip || !floatingAnchor) return;

    const anchor = floatingAnchor.getBoundingClientRect();
    const tooltip = floatingTooltip.getBoundingClientRect();
    const gutter = 12;
    const gap = 9;
    const left = Math.min(
      Math.max(anchor.left, gutter),
      Math.max(gutter, window.innerWidth - tooltip.width - gutter)
    );
    let top = anchor.top - tooltip.height - gap;
    if (top < gutter) top = anchor.bottom + gap;
    top = Math.min(
      Math.max(top, gutter),
      Math.max(gutter, window.innerHeight - tooltip.height - gutter)
    );

    floatingTooltip.style.left = `${Math.round(left)}px`;
    floatingTooltip.style.top = `${Math.round(top)}px`;
  }

  function showAnnotationTooltip(annotation) {
    const source = annotation.querySelector(
      '.prd-writing-inline-tooltip, .prd-speaking-inline-tooltip'
    );
    if (!source) return;

    hideAnnotationTooltip();
    const tooltip = document.createElement('div');
    tooltip.className = 'prd-inline-floating-tooltip';
    tooltip.setAttribute('aria-hidden', 'true');
    Array.from(source.childNodes).forEach((node) => {
      tooltip.appendChild(node.cloneNode(true));
    });
    document.body.appendChild(tooltip);
    floatingTooltip = tooltip;
    floatingAnchor = annotation;
    positionAnnotationTooltip();
  }

  document.querySelectorAll(annotationSelectors).forEach((annotation) => {
    annotation.addEventListener(
      'pointerenter',
      () => showAnnotationTooltip(annotation)
    );
    annotation.addEventListener('pointerleave', hideAnnotationTooltip);
    annotation.addEventListener(
      'focusin',
      () => showAnnotationTooltip(annotation)
    );
    annotation.addEventListener('focusout', hideAnnotationTooltip);
  });

  window.addEventListener('resize', positionAnnotationTooltip);
  window.addEventListener('scroll', positionAnnotationTooltip, true);

  const objectiveShell = document.querySelector(
    '[data-result-detail-kind="OBJECTIVE_DETAIL"]'
  );

  if (objectiveShell) {
    const groupPanels = Array.from(
      objectiveShell.querySelectorAll('[data-objective-group-panel]')
    );
    const questionPanels = Array.from(
      objectiveShell.querySelectorAll('[data-objective-question-panel]')
    );
    const sourcePanels = Array.from(
      objectiveShell.querySelectorAll('[data-objective-source-panel]')
    );
    const questionLinks = Array.from(
      objectiveShell.querySelectorAll('[data-objective-question-link]')
    );
    const sourceLinks = Array.from(
      objectiveShell.querySelectorAll('[data-objective-source-link]')
    );
    const groupNavItems = Array.from(
      objectiveShell.querySelectorAll('[data-objective-group-nav-item]')
    );

    function activeQuestionFromHash() {
      const anchor = window.location.hash.replace(/^#/, '');
      return questionPanels.find(
        (panel) => panel.dataset.objectiveQuestionId === anchor
      ) || null;
    }

    function setObjectiveQuestion(questionPanel, focusPanel) {
      if (!questionPanel || !questionPanels.includes(questionPanel)) return;

      const questionId = questionPanel.dataset.objectiveQuestionId;
      const sourceId = questionPanel.dataset.objectiveSourceId;
      const groupKey = questionPanel.dataset.objectiveGroupKey;
      const sourcePanel = sourcePanels.find(
        (panel) => panel.dataset.objectiveSourceId === sourceId
      );
      const groupPanel = groupPanels.find(
        (panel) => panel.dataset.objectiveGroupKey === groupKey
      );

      groupPanels.forEach((panel) => {
        const active = panel === groupPanel;
        panel.hidden = !active;
        panel.classList.toggle('is-active', active);
      });

      questionPanels.forEach((panel) => {
        const active = panel === questionPanel;
        panel.hidden = !active;
        panel.classList.toggle('is-active', active);
      });

      sourcePanels.forEach((panel) => {
        const active = panel === sourcePanel;
        panel.hidden = !active;
        panel.classList.toggle('is-active', active);
      });

      questionLinks.forEach((link) => {
        const active = link.dataset.objectiveQuestionId === questionId;
        link.classList.toggle('is-active', active);
        if (active) {
          link.setAttribute('aria-current', 'page');
        } else {
          link.removeAttribute('aria-current');
        }
      });

      sourceLinks.forEach((link) => {
        const active = link.dataset.objectiveSourceId === sourceId;
        link.classList.toggle('is-active', active);
        if (active) {
          link.setAttribute('aria-current', 'location');
        } else {
          link.removeAttribute('aria-current');
        }
      });

      groupNavItems.forEach((item) => {
        item.classList.toggle(
          'is-active',
          item.dataset.objectiveGroupKey === groupKey
        );
      });

      objectiveShell.classList.toggle(
        'is-source-empty',
        sourcePanel?.dataset.objectiveSourceEmpty === 'true'
      );

      if (focusPanel) {
        questionPanel.focus({ preventScroll: true });
        questionPanel.scrollIntoView({
          block: 'start',
          behavior: reducedMotion.matches ? 'auto' : 'smooth'
        });
      }
    }

    function openObjectiveQuestion(questionPanel, focusPanel) {
      setObjectiveQuestion(questionPanel, focusPanel);
      const nextHash = `#${questionPanel.dataset.objectiveQuestionId}`;
      if (window.location.hash !== nextHash) {
        window.history.pushState(null, '', nextHash);
      }
    }

    function activateObjectiveLinkFromKeyboard(event, questionPanel) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      if (!questionPanel) return;
      event.preventDefault();
      openObjectiveQuestion(questionPanel, true);
    }

    questionLinks.forEach((link) => {
      link.addEventListener('click', (event) => {
        const questionPanel = questionPanels.find(
          (panel) => panel.dataset.objectiveQuestionId
            === link.dataset.objectiveQuestionId
        );
        if (!questionPanel) return;
        event.preventDefault();
        openObjectiveQuestion(questionPanel, true);
      });
      link.addEventListener('keydown', (event) => {
        const questionPanel = questionPanels.find(
          (panel) => panel.dataset.objectiveQuestionId
            === link.dataset.objectiveQuestionId
        );
        activateObjectiveLinkFromKeyboard(event, questionPanel);
      });
    });

    sourceLinks.forEach((link) => {
      link.addEventListener('click', (event) => {
        const questionPanel = questionPanels.find(
          (panel) => panel.dataset.objectiveSourceId
            === link.dataset.objectiveSourceId
        );
        if (!questionPanel) return;
        event.preventDefault();
        openObjectiveQuestion(questionPanel, true);
      });
      link.addEventListener('keydown', (event) => {
        const questionPanel = questionPanels.find(
          (panel) => panel.dataset.objectiveSourceId
            === link.dataset.objectiveSourceId
        );
        activateObjectiveLinkFromKeyboard(event, questionPanel);
      });
    });

    window.addEventListener('hashchange', () => {
      setObjectiveQuestion(
        activeQuestionFromHash() || questionPanels[0],
        false
      );
    });

    setObjectiveQuestion(
      activeQuestionFromHash() || questionPanels[0],
      false
    );
  }
})();
