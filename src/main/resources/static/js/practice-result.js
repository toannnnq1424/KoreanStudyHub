(() => {
  'use strict';
  const reducedMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)'
  );

  function runResultCelebration() {
    const page = document.body;
    const celebration = document.querySelector('[data-result-celebration]');
    if (!celebration
        || page.dataset.resultCelebrationEligible !== 'true') return;

    const stateKey = page.dataset.resultCelebrationKey;
    if (!stateKey || celebration.dataset.played === 'true') return;
    // One celebration per rendered document. A real navigation or F5 creates
    // a new document and intentionally replays the short welcome animation.
    celebration.dataset.played = 'true';

    const deterministicMotionOff =
      window.__KSH_DISABLE_RESULT_MOTION__ === true
      || document.documentElement.dataset.practiceMotion === 'off';
    if (reducedMotion.matches || deterministicMotionOff) return;

    const pieces = [
      ['dot', 10, 70, -10, -52, -25, 0],
      ['star', 17, 62, -18, -76, 32, 45],
      ['dash', 25, 74, -12, -46, 65, 90],
      ['dot', 34, 66, -5, -68, -35, 120],
      ['star', 43, 76, -4, -54, 24, 165],
      ['dash', 53, 70, 4, -78, -62, 30],
      ['dot', 62, 76, 8, -58, 30, 105],
      ['star', 71, 63, 13, -72, 55, 145],
      ['dash', 80, 72, 18, -48, -40, 70],
      ['dot', 89, 65, 11, -62, 28, 190],
      ['star', 21, 82, -8, -44, -52, 215],
      ['dot', 76, 84, 7, -50, 40, 235],
      ['star', 7, 48, -18, -64, -72, 125],
      ['dash', 14, 86, -20, -42, 85, 255],
      ['dot', 28, 54, -8, -82, 20, 310],
      ['star', 38, 88, -5, -58, -42, 345],
      ['dash', 48, 58, 2, -86, 76, 205],
      ['dot', 58, 88, 5, -52, -28, 365],
      ['star', 67, 50, 12, -84, 68, 285],
      ['dash', 75, 91, 16, -46, -74, 395],
      ['dot', 84, 51, 18, -78, 42, 330],
      ['star', 94, 80, 20, -56, -58, 420],
      ['dash', 31, 91, -9, -38, 95, 455],
      ['star', 69, 92, 10, -40, -90, 485]
    ];
    const colors = ['#ffd23f', '#ff6b6b', '#4d7cff', '#35b86b'];

    pieces.forEach(([shape, x, y, dx, dy, rotation, delay], index) => {
      const piece = document.createElement('i');
      piece.className = `pr-result-celebration-piece is-${shape}`;
      piece.style.setProperty('--celebration-x', `${x}%`);
      piece.style.setProperty('--celebration-y', `${y}%`);
      piece.style.setProperty('--celebration-dx', `${dx}px`);
      piece.style.setProperty('--celebration-dy', `${dy}px`);
      piece.style.setProperty('--celebration-rotation', `${rotation}deg`);
      piece.style.setProperty('--celebration-dx-mid', `${dx * 0.45}px`);
      piece.style.setProperty('--celebration-dy-mid', `${dy * 0.58}px`);
      piece.style.setProperty('--celebration-rotation-mid', `${rotation * 0.55}deg`);
      piece.style.setProperty('--celebration-dx-late', `${dx * 0.75}px`);
      piece.style.setProperty('--celebration-dy-late', `${dy * 0.86}px`);
      piece.style.setProperty('--celebration-rotation-late', `${rotation * 0.82}deg`);
      piece.style.setProperty('--celebration-delay', `${delay}ms`);
      piece.style.setProperty('--celebration-color', colors[index % colors.length]);
      celebration.append(piece);
    });

    celebration.hidden = false;
    window.requestAnimationFrame(() => celebration.classList.add('is-active'));
    window.setTimeout(() => {
      celebration.classList.remove('is-active');
      celebration.replaceChildren();
      celebration.hidden = true;
    }, 2850);
  }

  runResultCelebration();

  const tabLists = document.querySelectorAll('[data-result-tabs]');
  const hasToken = (value, token) => String(value || '')
    .split(/\s+/)
    .filter(Boolean)
    .includes(token);

  function resetDiagnosticState(review) {
    if (!review) return;

    review.querySelectorAll(
      '[data-writing-diagnostic-filter], [data-writing-upgrade-filter], '
      + '[data-speaking-diagnostic-filter], [data-speaking-upgrade-filter]'
    ).forEach((filter) => filter.setAttribute('aria-pressed', 'false'));

    review.querySelectorAll(
      '[data-writing-feature], [data-speaking-feature]'
    ).forEach((item) => {
      if (!item.hasAttribute('data-writing-diagnostic-filter')
          && !item.hasAttribute('data-writing-upgrade-filter')
          && !item.hasAttribute('data-speaking-diagnostic-filter')) {
        item.hidden = false;
      }
    });

    review.querySelectorAll(
      '[data-writing-occurrence-trigger], [data-speaking-occurrence-trigger]'
    ).forEach((trigger) => trigger.setAttribute('aria-expanded', 'false'));
    review.querySelectorAll('.prd-occurrence-detail').forEach((detail) => {
      detail.hidden = true;
    });
    review.querySelectorAll(
      '[data-writing-zero-chip-empty], [data-speaking-zero-chip-empty]'
    ).forEach((empty) => { empty.hidden = true; });
    review.querySelectorAll(
      '[data-writing-number-feature], [data-speaking-number-feature]'
    ).forEach((number) => { number.hidden = false; });

    review.querySelectorAll(
      '.prd-writing-inline-annotation, .prd-speaking-inline-annotation'
    ).forEach((annotation) => {
      annotation.classList.remove(
        'is-selected', 'is-muted', 'is-upgrade', 'is-occurrence-selected'
      );
    });

    review.querySelectorAll(
      '[data-writing-filter-status], [data-writing-upgrade-filter-status], '
      + '[data-speaking-filter-status], [data-speaking-upgrade-filter-status]'
    ).forEach((status) => {
      status.textContent = 'Đang hiển thị toàn bộ occurrence có bằng chứng.';
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

  const writingFilters = document.querySelectorAll(
    '[data-writing-diagnostic-filter], [data-writing-upgrade-filter]'
  );

  writingFilters.forEach((filter) => {
    filter.addEventListener('click', () => {
      const panel = filter.closest('[role="tabpanel"]');
      if (!panel) return;
      const review = filter.closest('[data-writing-active-question]');
      const upgradeFilter = filter.hasAttribute('data-writing-upgrade-filter');
      panel.querySelectorAll('[data-writing-occurrence-trigger]')
        .forEach((trigger) => trigger.setAttribute('aria-expanded', 'false'));
      panel.querySelectorAll('.prd-occurrence-detail')
        .forEach((detail) => { detail.hidden = true; });

      const scopedFilters = Array.from(
        panel.querySelectorAll(
          '[data-writing-diagnostic-filter], [data-writing-upgrade-filter]'
        )
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-writing-feature]')
      ).filter((item) =>
        !item.hasAttribute('data-writing-diagnostic-filter')
        && !item.hasAttribute('data-writing-upgrade-filter')
      );
      const annotations = review
        ? Array.from(
          review.querySelectorAll(
            '.prd-writing-inline-annotation[data-writing-features]'
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
          && hasToken(annotation.dataset.writingFeatures, feature);
        annotation.classList.toggle('is-selected', selected);
        annotation.classList.toggle('is-muted', activateFilter && !selected);
        annotation.classList.toggle('is-upgrade', selected && upgradeFilter);
      });
      review?.querySelectorAll('[data-writing-number-feature]').forEach((number) => {
        number.hidden = activateFilter
          && number.dataset.writingNumberFeature !== feature;
      });

      const zeroState = panel.querySelector('[data-writing-zero-chip-empty]');
      if (zeroState) {
        zeroState.hidden = !(activateFilter
          && Number(filter.dataset.writingChipCount || 0) === 0);
      }

      const status = panel.querySelector(
        '[data-writing-filter-status], [data-writing-upgrade-filter-status]'
      );
      if (!activateFilter) {
        if (status) status.textContent =
          'Đang hiển thị toàn bộ occurrence có bằng chứng.';
        return;
      }

      filter.setAttribute('aria-pressed', 'true');
      const visibleCount = findings.filter(
        (finding) => finding.dataset.writingFeature === feature
      ).length;
      if (status) {
        status.textContent = upgradeFilter
          ? `Đang đánh dấu ${annotations.filter((annotation) =>
            hasToken(annotation.dataset.writingFeatures, feature)).length} đoạn được nâng cấp.`
          : `Đang hiển thị ${visibleCount} phản hồi phù hợp.`;
      }
    });
  });

  document.querySelectorAll('[data-writing-splitter]').forEach((splitter) => {
    const shell = splitter.closest('.prd-writing-shell');
    if (!shell) return;

    const min = Number(splitter.getAttribute('aria-valuemin')) || 35;
    const max = Number(splitter.getAttribute('aria-valuemax')) || 65;
    const clamp = (value) => Math.min(max, Math.max(min, value));
    const setSplit = (value) => {
      const next = Math.round(clamp(value) * 10) / 10;
      shell.style.setProperty('--writing-split', `${next}%`);
      splitter.setAttribute('aria-valuenow', String(Math.round(next)));
    };
    const setFromPointer = (clientX) => {
      const bounds = shell.getBoundingClientRect();
      if (!bounds.width) return;
      setSplit(((clientX - bounds.left) / bounds.width) * 100);
    };

    splitter.addEventListener('pointerdown', (event) => {
      splitter.classList.add('is-dragging');
      splitter.setPointerCapture(event.pointerId);
      setFromPointer(event.clientX);
    });
    splitter.addEventListener('pointermove', (event) => {
      if (!splitter.classList.contains('is-dragging')) return;
      setFromPointer(event.clientX);
    });
    splitter.addEventListener('pointerup', (event) => {
      splitter.classList.remove('is-dragging');
      if (splitter.hasPointerCapture(event.pointerId)) {
        splitter.releasePointerCapture(event.pointerId);
      }
    });
    splitter.addEventListener('pointercancel', () => {
      splitter.classList.remove('is-dragging');
    });
    splitter.addEventListener('keydown', (event) => {
      const current = Number(splitter.getAttribute('aria-valuenow')) || 52;
      let next = null;
      if (event.key === 'ArrowLeft') next = current - 2;
      if (event.key === 'ArrowRight') next = current + 2;
      if (event.key === 'Home') next = min;
      if (event.key === 'End') next = max;
      if (next == null) return;
      event.preventDefault();
      setSplit(next);
    });
  });

  const speakingFilters = document.querySelectorAll(
    '[data-speaking-diagnostic-filter], [data-speaking-upgrade-filter]'
  );

  document.querySelectorAll('[data-speaking-filter-status]').forEach((status) => {
    status.textContent = 'Đang hiển thị toàn bộ occurrence có bằng chứng.';
  });

  speakingFilters.forEach((filter) => {
    filter.addEventListener('click', () => {
      const panel = filter.closest('[role="tabpanel"]');
      if (!panel) return;
      const review = filter.closest('[data-speaking-active-question]');
      const upgradeFilter = filter.hasAttribute('data-speaking-upgrade-filter');
      panel.querySelectorAll('[data-speaking-occurrence-trigger]')
        .forEach((trigger) => trigger.setAttribute('aria-expanded', 'false'));
      panel.querySelectorAll('.prd-occurrence-detail')
        .forEach((detail) => { detail.hidden = true; });

      const scopedFilters = Array.from(
        panel.querySelectorAll(
          '[data-speaking-diagnostic-filter], [data-speaking-upgrade-filter]'
        )
      );
      const findings = Array.from(
        panel.querySelectorAll('[data-speaking-feature]')
      ).filter((item) =>
        !item.hasAttribute('data-speaking-diagnostic-filter')
        && !item.hasAttribute('data-speaking-upgrade-filter')
      );
      const annotations = review
        ? Array.from(
          review.querySelectorAll(
            '.prd-speaking-inline-annotation[data-speaking-features]'
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
          && hasToken(annotation.dataset.speakingFeatures, feature);
        annotation.classList.toggle('is-selected', selected);
        annotation.classList.toggle('is-muted', activateFilter && !selected);
        annotation.classList.toggle('is-upgrade', selected && upgradeFilter);
      });
      review?.querySelectorAll('[data-speaking-number-feature]').forEach((number) => {
        number.hidden = activateFilter
          && number.dataset.speakingNumberFeature !== feature;
      });

      const zeroState = panel.querySelector('[data-speaking-zero-chip-empty]');
      if (zeroState) {
        zeroState.hidden = !(activateFilter
          && Number(filter.dataset.speakingChipCount || 0) === 0);
      }

      const status = panel.querySelector(
        '[data-speaking-filter-status], [data-speaking-upgrade-filter-status]'
      );
      if (!activateFilter) {
        if (status) {
          status.textContent = upgradeFilter
            ? ''
            : 'Đang hiển thị toàn bộ occurrence có bằng chứng.';
        }
        return;
      }

      filter.setAttribute('aria-pressed', 'true');
      const visibleCount = findings.filter(
        (finding) => finding.dataset.speakingFeature === feature
      ).length;
      if (status) {
        status.textContent =
          upgradeFilter
            ? `Đang đánh dấu ${annotations.filter((annotation) =>
              hasToken(annotation.dataset.speakingFeatures, feature)).length} đoạn được nâng cấp.`
            : `Đang hiển thị ${visibleCount} phản hồi phù hợp.`;
      }
    });
  });

  function activateOccurrence(trigger, kind) {
    const panel = trigger.closest('[role="tabpanel"]');
    if (!panel) return;
    const card = trigger.closest(
      kind === 'writing'
        ? '[data-writing-occurrence]'
        : '[data-speaking-occurrence]'
    );
    if (!card) return;
    const identity = kind === 'writing'
      ? card.dataset.writingOccurrence
      : card.dataset.speakingOccurrence;
    const expanded = trigger.getAttribute('aria-expanded') === 'true';

    panel.querySelectorAll(
      '[data-writing-occurrence-trigger], [data-speaking-occurrence-trigger]'
    ).forEach((candidate) => candidate.setAttribute('aria-expanded', 'false'));
    panel.querySelectorAll('.prd-occurrence-detail').forEach((detail) => {
      detail.hidden = true;
    });
    const review = trigger.closest(
      '[data-writing-active-question], [data-speaking-active-question]'
    );
    review?.querySelectorAll(
      '.prd-writing-inline-annotation, .prd-speaking-inline-annotation'
    ).forEach((annotation) => {
      const ids = kind === 'writing'
        ? annotation.dataset.writingFindingIds
        : annotation.dataset.speakingFindingIds;
      annotation.classList.toggle(
        'is-occurrence-selected', !expanded && hasToken(ids, identity)
      );
    });
    if (expanded) return;

    trigger.setAttribute('aria-expanded', 'true');
    const detailId = trigger.getAttribute('aria-controls');
    const detail = detailId ? document.getElementById(detailId) : null;
    if (detail) detail.hidden = false;
  }

  document.querySelectorAll('[data-writing-occurrence-trigger]').forEach((trigger) => {
    trigger.addEventListener('click', () => activateOccurrence(trigger, 'writing'));
  });
  document.querySelectorAll('[data-speaking-occurrence-trigger]').forEach((trigger) => {
    trigger.addEventListener('click', () => activateOccurrence(trigger, 'speaking'));
  });

  document.querySelectorAll(
    '.prd-writing-inline-annotation[data-writing-finding-ids], '
    + '.prd-speaking-inline-annotation[data-speaking-finding-ids]'
  ).forEach((annotation) => {
    annotation.addEventListener('click', () => {
      const writing = annotation.classList.contains('prd-writing-inline-annotation');
      const ids = String(writing
        ? annotation.dataset.writingFindingIds
        : annotation.dataset.speakingFindingIds).split(/\s+/).filter(Boolean);
      const prefix = writing ? 'writing-finding-' : 'speaking-finding-';
      const card = ids.map((id) => document.getElementById(`${prefix}${id}`))
        .find(Boolean);
      const trigger = card?.querySelector(
        writing
          ? '[data-writing-occurrence-trigger]'
          : '[data-speaking-occurrence-trigger]'
      );
      if (!trigger) return;
      card.hidden = false;
      activateOccurrence(trigger, writing ? 'writing' : 'speaking');
      card.scrollIntoView({
        behavior: reducedMotion.matches ? 'auto' : 'smooth',
        block: 'center'
      });
    });
  });

  document.querySelectorAll('[data-speaking-splitter]').forEach((splitter) => {
    const shell = splitter.closest('.prd-speaking-shell');
    if (!shell) return;

    const min = Number(splitter.getAttribute('aria-valuemin')) || 35;
    const max = Number(splitter.getAttribute('aria-valuemax')) || 65;
    const clamp = (value) => Math.min(max, Math.max(min, value));
    const setSplit = (value) => {
      const next = Math.round(clamp(value) * 10) / 10;
      shell.style.setProperty('--speaking-split', `${next}%`);
      splitter.setAttribute('aria-valuenow', String(Math.round(next)));
    };
    const setFromPointer = (clientX) => {
      const bounds = shell.getBoundingClientRect();
      if (!bounds.width) return;
      setSplit(((clientX - bounds.left) / bounds.width) * 100);
    };

    splitter.addEventListener('pointerdown', (event) => {
      splitter.classList.add('is-dragging');
      splitter.setPointerCapture(event.pointerId);
      setFromPointer(event.clientX);
    });
    splitter.addEventListener('pointermove', (event) => {
      if (!splitter.classList.contains('is-dragging')) return;
      setFromPointer(event.clientX);
    });
    splitter.addEventListener('pointerup', (event) => {
      splitter.classList.remove('is-dragging');
      if (splitter.hasPointerCapture(event.pointerId)) {
        splitter.releasePointerCapture(event.pointerId);
      }
    });
    splitter.addEventListener('pointercancel', () => {
      splitter.classList.remove('is-dragging');
    });
    splitter.addEventListener('keydown', (event) => {
      const current = Number(splitter.getAttribute('aria-valuenow')) || 52;
      let next = null;
      if (event.key === 'ArrowLeft') next = current - 2;
      if (event.key === 'ArrowRight') next = current + 2;
      if (event.key === 'Home') next = min;
      if (event.key === 'End') next = max;
      if (next == null) return;
      event.preventDefault();
      setSplit(next);
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
    if (annotation.classList.contains('prd-speaking-inline-annotation')
        && !annotation.classList.contains('is-selected')) {
      return;
    }
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
    const materialPinButtons = Array.from(
      objectiveShell.querySelectorAll('[data-objective-material-pin]')
    );
    const objectiveSplitters = Array.from(
      objectiveShell.querySelectorAll('[data-objective-splitter]')
    );
    const objectiveAttemptId = objectiveShell.dataset.objectiveAttemptId || 'unknown';
    const materialPinStorageKey =
      `ksh:practice-result-detail:${objectiveAttemptId}:pinned-material-groups`;
    const objectiveSplitStorageKey =
      `ksh:practice-result-detail:${objectiveAttemptId}:split-ratio`;

    function setObjectiveSplitRatio(rawRatio) {
      const ratio = Math.max(32, Math.min(68, Number(rawRatio) || 50));
      groupPanels.forEach((panel) => {
        panel.style.setProperty('--prd-objective-split', `${ratio}%`);
      });
      objectiveSplitters.forEach((splitter) => {
        splitter.setAttribute('aria-valuenow', String(Math.round(ratio)));
      });
      return ratio;
    }

    let objectiveSplitRatio = 50;
    try {
      objectiveSplitRatio = setObjectiveSplitRatio(
        window.localStorage.getItem(objectiveSplitStorageKey) || 50
      );
    } catch (error) {
      objectiveSplitRatio = setObjectiveSplitRatio(50);
    }

    function persistObjectiveSplitRatio() {
      try {
        window.localStorage.setItem(
          objectiveSplitStorageKey,
          String(Math.round(objectiveSplitRatio * 100) / 100)
        );
      } catch (error) {
        // The splitter remains functional when browser storage is unavailable.
      }
    }

    objectiveSplitters.forEach((splitter) => {
      const panel = splitter.closest('[data-objective-group-panel]');
      if (!panel) return;
      const moveToPointer = (event) => {
        const bounds = panel.getBoundingClientRect();
        if (!bounds.width) return;
        objectiveSplitRatio = setObjectiveSplitRatio(
          ((event.clientX - bounds.left) / bounds.width) * 100
        );
      };
      splitter.addEventListener('pointerdown', (event) => {
        event.preventDefault();
        splitter.classList.add('is-dragging');
        splitter.setPointerCapture(event.pointerId);
        moveToPointer(event);
      });
      splitter.addEventListener('pointermove', (event) => {
        if (!splitter.classList.contains('is-dragging')) return;
        moveToPointer(event);
      });
      splitter.addEventListener('pointerup', (event) => {
        splitter.classList.remove('is-dragging');
        if (splitter.hasPointerCapture(event.pointerId)) {
          splitter.releasePointerCapture(event.pointerId);
        }
        persistObjectiveSplitRatio();
      });
      splitter.addEventListener('pointercancel', () => {
        splitter.classList.remove('is-dragging');
      });
      splitter.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        if (event.key === 'Home') objectiveSplitRatio = setObjectiveSplitRatio(32);
        else if (event.key === 'End') objectiveSplitRatio = setObjectiveSplitRatio(68);
        else objectiveSplitRatio = setObjectiveSplitRatio(
          objectiveSplitRatio + (event.key === 'ArrowLeft' ? -2 : 2)
        );
        persistObjectiveSplitRatio();
      });
    });

    let pinnedMaterialGroups = new Set();
    try {
      const stored = window.localStorage.getItem(materialPinStorageKey) || '';
      pinnedMaterialGroups = new Set(stored.split('\n')
        .filter(Boolean)
        .map((value) => decodeURIComponent(value)));
    } catch (error) {
      pinnedMaterialGroups = new Set();
    }

    function persistMaterialPins() {
      try {
        window.localStorage.setItem(
          materialPinStorageKey,
          Array.from(pinnedMaterialGroups)
            .map((value) => encodeURIComponent(value))
            .join('\n')
        );
      } catch (error) {
        // Result review stays functional when persistent browser storage is unavailable.
      }
    }

    function paintMaterialPins() {
      materialPinButtons.forEach((button) => {
        const groupKey = String(button.dataset.objectiveGroupKey || '');
        const pinned = pinnedMaterialGroups.has(groupKey);
        button.setAttribute('aria-pressed', String(pinned));
        button.title = pinned
          ? 'Bỏ ghim học liệu dùng chung'
          : 'Ghim học liệu dùng chung';
        const label = button.querySelector('[data-objective-material-pin-label]');
        if (label) label.textContent = pinned ? 'Bỏ ghim' : 'Ghim học liệu';
      });
      groupPanels.forEach((panel) => {
        panel.classList.toggle(
          'is-material-pinned',
          pinnedMaterialGroups.has(String(panel.dataset.objectiveGroupKey || ''))
        );
      });
    }

    materialPinButtons.forEach((button) => {
      button.addEventListener('click', () => {
        const groupKey = String(button.dataset.objectiveGroupKey || '');
        if (pinnedMaterialGroups.has(groupKey)) pinnedMaterialGroups.delete(groupKey);
        else pinnedMaterialGroups.add(groupKey);
        persistMaterialPins();
        paintMaterialPins();
        button.focus();
      });
    });

    const helper = document.querySelector('[data-objective-helper]');
    const helperToggle = document.querySelector('[data-objective-helper-toggle]');
    const helperClose = helper?.querySelector('[data-objective-helper-close]');
    const helperBackdrop = document.querySelector('[data-objective-helper-backdrop]');
    const helperPageRegions = [
      document.querySelector('.prd-header'),
      objectiveShell
    ].filter(Boolean);
    let helperReturnFocus = null;

    function closeObjectiveHelper() {
      if (!helper || helper.getAttribute('aria-hidden') === 'true') return;
      helper.classList.remove('is-open');
      helper.setAttribute('aria-hidden', 'true');
      helper.setAttribute('inert', '');
      if (helperBackdrop) helperBackdrop.hidden = true;
      helperPageRegions.forEach((region) => region.removeAttribute('inert'));
      if (helperToggle) helperToggle.setAttribute('aria-expanded', 'false');
      document.body.classList.remove('is-objective-helper-open');
      if (helperReturnFocus && typeof helperReturnFocus.focus === 'function') {
        helperReturnFocus.focus();
      }
      helperReturnFocus = null;
    }

    function openObjectiveHelper() {
      if (!helper || !helperToggle) return;
      helperReturnFocus = document.activeElement;
      helper.removeAttribute('inert');
      helper.setAttribute('aria-hidden', 'false');
      helper.classList.add('is-open');
      if (helperBackdrop) helperBackdrop.hidden = false;
      helperPageRegions.forEach((region) => region.setAttribute('inert', ''));
      helperToggle.setAttribute('aria-expanded', 'true');
      document.body.classList.add('is-objective-helper-open');
      helperClose?.focus();
    }

    helperToggle?.addEventListener('click', openObjectiveHelper);
    helperClose?.addEventListener('click', closeObjectiveHelper);
    helperBackdrop?.addEventListener('click', closeObjectiveHelper);
    helper?.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeObjectiveHelper();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = Array.from(helper.querySelectorAll(
        'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'
      ));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });

    paintMaterialPins();

    function activeQuestionFromHash() {
      const anchor = window.location.hash
        .replace(/^#/, '')
        .replace(/-explanation$/, '');
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
        panel.hidden = panel.dataset.objectiveGroupKey !== groupKey;
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

      const activeGroupNavItem = groupNavItems.find(
        (item) => item.dataset.objectiveGroupKey === groupKey
      );
      if (activeGroupNavItem) {
        activeGroupNavItem.scrollIntoView({
          behavior: reducedMotion.matches ? 'auto' : 'smooth',
          block: 'nearest',
          inline: 'nearest'
        });
      }

      const activeQuestionLink = questionLinks.find(
        (link) => link.dataset.objectiveQuestionId === questionId
      );
      if (activeQuestionLink) {
        activeQuestionLink.scrollIntoView({
          behavior: reducedMotion.matches ? 'auto' : 'smooth',
          block: 'nearest',
          inline: 'center'
        });
      }

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
        true
      );
    });

    const initialQuestion = activeQuestionFromHash() || questionPanels[0];
    setObjectiveQuestion(
      initialQuestion,
      Boolean(window.location.hash)
    );
  }
})();
