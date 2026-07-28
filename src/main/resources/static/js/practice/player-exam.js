(function () {
  'use strict';

  const player = document.querySelector('.exam-player');
  if (!player) return;
  const reducedMotion = window.matchMedia(
    '(prefers-reduced-motion: reduce)'
  );

  const attemptId = player.dataset.attemptId || 'unknown';
  const skill = player.dataset.skill || '';
  const workspace = document.querySelector('.exam-workspace');
  const groupTabs = Array.from(player.querySelectorAll('[data-group-tab]'));
  const groupCount = groupTabs.length;
  let activeGroup = 0;
  let allowNavigation = false;

  const sessionStore = {
    get(key) {
      try {
        return window.sessionStorage.getItem(key);
      } catch (error) {
        return null;
      }
    },
    set(key, value) {
      try {
        window.sessionStorage.setItem(key, value);
      } catch (error) {
        // The player remains usable when browser storage is unavailable.
      }
    },
    remove(key) {
      try {
        window.sessionStorage.removeItem(key);
      } catch (error) {
        // Nothing to clean up when browser storage is unavailable.
      }
    }
  };

  const stageSelector = (index) => `[data-group-stage="${index}"]`;

  const updateAdaptiveLayout = (index) => {
    if (!workspace || skill !== 'READING') return;
    const source = workspace.querySelector(`.exam-source-stage${stageSelector(index)}`);
    const hasSource = source && source.dataset.hasSource === 'true';
    const longSource = source && source.dataset.longSource === 'true';
    workspace.classList.remove('layout-focus', 'layout-stacked', 'layout-split');
    workspace.classList.add(!hasSource ? 'layout-focus' : (longSource ? 'layout-split' : 'layout-stacked'));
  };

  const groupQuestions = (index) => Array.from(
    player.querySelectorAll(`[data-question-stage]${stageSelector(index)} .exam-question`)
  );

  const questionAnswered = (question) => {
    const radios = Array.from(question.querySelectorAll('input[type="radio"]'));
    if (radios.length) return radios.some((input) => input.checked);
    const blankInputs = Array.from(question.querySelectorAll('.exam-blank-input'));
    if (blankInputs.length) return blankInputs.every((input) => input.value.trim().length > 0);
    const response = question.querySelector('textarea[name^="answer_"], input[type="text"][name^="answer_"]');
    return Boolean(response && response.value.trim().length > 0);
  };

  const updateProgress = () => {
    const questions = Array.from(player.querySelectorAll('.exam-question'));
    const answered = questions.filter(questionAnswered).length;
    player.querySelectorAll('[data-progress-done]').forEach((node) => {
      node.textContent = String(answered);
    });

    groupTabs.forEach((tab, index) => {
      const questionsInGroup = groupQuestions(index);
      const complete = questionsInGroup.length > 0 && questionsInGroup.every(questionAnswered);
      const flagged = questionsInGroup.some((question) => {
        const flag = question.querySelector('[data-review-flag]');
        return Boolean(flag && flag.checked);
      });
      tab.classList.toggle('is-complete', complete);
      tab.classList.toggle('is-flagged', flagged);
    });
  };

  const showGroup = (nextIndex, shouldFocus) => {
    if (!groupCount) return;
    activeGroup = Math.max(0, Math.min(groupCount - 1, Number(nextIndex) || 0));
    player.querySelectorAll('[data-group-stage]').forEach((stage) => {
      stage.hidden = Number(stage.dataset.groupStage) !== activeGroup;
    });
    groupTabs.forEach((tab, index) => tab.classList.toggle('is-active', index === activeGroup));
    const questionStage = player.querySelector(`[data-question-stage]${stageSelector(activeGroup)}`);
    const activeTitle = player.querySelector('[data-active-group-title]');
    if (activeTitle) {
      const label = questionStage && questionStage.dataset.groupLabel;
      activeTitle.textContent = label || `Phần ${activeGroup + 1}`;
    }
    player.querySelectorAll('[data-group-prev]').forEach((button) => {
      button.disabled = activeGroup === 0;
    });
    player.querySelectorAll('[data-group-next]').forEach((button) => {
      button.disabled = activeGroup === groupCount - 1;
    });
    updateAdaptiveLayout(activeGroup);
    updateProgress();
    if (shouldFocus) {
      player.querySelectorAll('.exam-source-pane, .exam-question-pane, .writing-source-pane, .writing-answer-pane')
        .forEach((pane) => pane.scrollTo({
          top: 0,
          behavior: reducedMotion.matches ? 'auto' : 'smooth'
        }));
      const firstAnswer = questionStage && questionStage.querySelector('input:not([type="hidden"]), textarea');
      if (firstAnswer && window.matchMedia('(max-width: 900px)').matches) firstAnswer.focus({ preventScroll: true });
    }
  };

  groupTabs.forEach((tab, index) => tab.addEventListener('click', () => showGroup(index, true)));
  player.querySelectorAll('[data-group-prev]').forEach((button) => {
    button.addEventListener('click', () => showGroup(activeGroup - 1, true));
  });
  player.querySelectorAll('[data-group-next]').forEach((button) => {
    button.addEventListener('click', () => showGroup(activeGroup + 1, true));
  });

  const renderFillBlankTemplates = () => {
    player.querySelectorAll('[data-fill-question]').forEach((container) => {
      const template = container.querySelector('.exam-fill-template');
      const bank = container.querySelector('.exam-fill-bank');
      if (!template || !bank || template.dataset.rendered === 'true') return;

      if (!bank.querySelector('.exam-blank-input')) {
        const fallback = document.createElement('label');
        fallback.className = 'exam-fill-row';
        fallback.dataset.blankRow = 'blank_1';
        fallback.dataset.blankNumber = '1';
        const label = document.createElement('span');
        label.textContent = 'Điền vào chỗ trống';
        const input = document.createElement('input');
        input.type = 'text';
        input.className = 'exam-blank-input';
        input.dataset.blankId = 'blank_1';
        input.autocomplete = 'off';
        fallback.append(label, input);
        bank.appendChild(fallback);
      }

      const source = template.textContent || '';
      const tokenPattern = /\{\{blank:([^}]+)\}\}/g;
      const fragment = document.createDocumentFragment();
      let cursor = 0;
      let match;
      let tokenCount = 0;
      while ((match = tokenPattern.exec(source)) !== null) {
        fragment.appendChild(document.createTextNode(source.slice(cursor, match.index)));
        const blankId = match[1].trim();
        const row = Array.from(bank.querySelectorAll('[data-blank-row]'))
          .find((candidate) => candidate.dataset.blankRow === blankId);
        const input = row && row.querySelector('.exam-blank-input');
        if (input) {
          const inline = document.createElement('span');
          inline.className = 'exam-inline-blank';
          const number = document.createElement('b');
          number.className = 'exam-inline-blank-number';
          number.textContent = row.dataset.blankNumber || String(tokenCount + 1);
          inline.append(number, input);
          fragment.appendChild(inline);
          row.hidden = true;
          tokenCount += 1;
        } else {
          fragment.appendChild(document.createTextNode('_____'));
        }
        cursor = tokenPattern.lastIndex;
      }
      if (tokenCount > 0) {
        fragment.appendChild(document.createTextNode(source.slice(cursor)));
        template.replaceChildren(fragment);
      }
      bank.hidden = !bank.querySelector('.exam-fill-row:not([hidden])');
      template.dataset.rendered = 'true';
    });
  };

  const syncFillAnswer = (container) => {
    const blankAnswers = {};
    container.querySelectorAll('.exam-blank-input').forEach((input) => {
      blankAnswers[input.dataset.blankId || 'blank_1'] = input.value;
    });
    const hidden = container.querySelector('[data-fill-answer]');
    if (hidden) {
      hidden.value = JSON.stringify({
        schemaVersion: 'learner-answer-v1',
        questionType: 'FILL_BLANK',
        blankAnswers: blankAnswers
      });
    }
  };

  const hydrateFillAnswer = (container) => {
    const hidden = container.querySelector('[data-fill-answer]');
    if (!hidden || !hidden.value) return;
    try {
      const stored = JSON.parse(hidden.value);
      const blankAnswers = stored && stored.blankAnswers;
      if (!blankAnswers || typeof blankAnswers !== 'object') return;
      container.querySelectorAll('.exam-blank-input').forEach((input) => {
        const value = blankAnswers[input.dataset.blankId || 'blank_1'];
        if (typeof value === 'string') input.value = value;
      });
    } catch (error) {
      // Malformed retained drafts stay fail-closed and are replaced on input.
    }
  };

  renderFillBlankTemplates();
  player.querySelectorAll('[data-fill-question]').forEach(hydrateFillAnswer);
  player.querySelectorAll('[data-fill-question]').forEach(syncFillAnswer);

  let autosaveTimer = null;
  let autosaveInFlight = null;
  let autosaveBlocked = false;
  let autosaveGeneration = 0;
  let autosavePersistedGeneration = 0;
  let autosavePending = false;
  let autosaveRetryCount = 0;
  let autosaveRetryDelay = 0;
  let autosaveRetryExhausted = false;
  let autosaveSubmitDrain = false;
  let submissionPending = false;
  let exitPending = false;
  let deadlineSubmission = false;
  const maxAutosaveRetries = 3;
  const lockVersionInput = player.querySelector('[data-attempt-lock-version]');
  const autosaveStatus = player.querySelector('[data-autosave-status]');
  const csrfInput = player.querySelector('input[name^="_csrf"]');

  const announceAutosave = (message) => {
    if (autosaveStatus) autosaveStatus.textContent = message;
  };

  const scheduleAutosave = (delay = 800) => {
    if (autosaveBlocked) return;
    window.clearTimeout(autosaveTimer);
    autosaveTimer = window.setTimeout(() => {
      autosaveTimer = null;
      saveAnswers();
    }, Math.max(0, delay));
  };

  const markAutosaveDirty = () => {
    autosaveGeneration += 1;
    autosavePending = true;
    autosaveRetryCount = 0;
    autosaveRetryDelay = 0;
    autosaveRetryExhausted = false;
    scheduleAutosave();
  };

  const saveAnswers = () => {
    if (autosaveBlocked || !lockVersionInput) return Promise.resolve(false);
    if (autosaveInFlight) {
      autosavePending = true;
      return autosaveInFlight;
    }
    player.querySelectorAll('[data-fill-question]').forEach(syncFillAnswer);
    const requestedGeneration = autosaveGeneration;
    autosavePending = false;
    const body = new FormData(player);
    body.set('expectedLockVersion', lockVersionInput.value);
    const headers = { 'Accept': 'application/json' };
    if (csrfInput && csrfInput.value) headers['X-CSRF-TOKEN'] = csrfInput.value;
    announceAutosave('Đang lưu bài làm…');
    autosaveInFlight = fetch(`/practice/attempts/${encodeURIComponent(attemptId)}/answers`, {
      method: 'PUT',
      headers: headers,
      body: body,
      credentials: 'same-origin'
    }).then(async (response) => {
      const payload = await response.json().catch(() => ({}));
      if (response.ok && payload.status === 'SAVED') {
        lockVersionInput.value = String(payload.lockVersion);
        autosavePersistedGeneration = Math.max(
          autosavePersistedGeneration,
          requestedGeneration
        );
        autosaveRetryCount = 0;
        autosaveRetryDelay = 0;
        autosaveRetryExhausted = false;
        announceAutosave('Đã lưu bài làm.');
        return true;
      }
      if (response.status === 409) {
        autosaveBlocked = true;
        announceAutosave('Bài làm đã thay đổi ở phiên khác. Hãy tải lại trang.');
        return false;
      }
      if (response.status === 410) {
        autosaveBlocked = true;
        deadlineSubmission = true;
        announceAutosave('Đã hết giờ. Hệ thống dùng đáp án đã lưu trước hạn.');
        return false;
      }
      if (response.status === 429 || response.status >= 500) {
        autosaveRetryCount += 1;
        if (autosaveRetryCount <= maxAutosaveRetries) {
          autosavePending = true;
          autosaveRetryDelay = 1000 * (2 ** (autosaveRetryCount - 1));
          announceAutosave(payload.message || 'Chưa thể lưu bài. Hệ thống sẽ thử lại.');
        } else {
          autosavePending = false;
          autosaveRetryExhausted = true;
          announceAutosave('Chưa thể lưu bài sau nhiều lần thử. Hãy kiểm tra kết nối và sửa đáp án để thử lại.');
        }
        return false;
      }
      autosaveBlocked = true;
      announceAutosave(payload.message || 'Không thể lưu bài làm. Hãy tải lại trang.');
      return false;
    }).catch(() => {
      autosaveRetryCount += 1;
      if (autosaveRetryCount <= maxAutosaveRetries) {
        autosavePending = true;
        autosaveRetryDelay = 1000 * (2 ** (autosaveRetryCount - 1));
        announceAutosave('Mất kết nối. Hệ thống sẽ thử lưu lại.');
      } else {
        autosavePending = false;
        autosaveRetryExhausted = true;
        announceAutosave('Mất kết nối và chưa thể lưu bài. Hãy kiểm tra mạng và sửa đáp án để thử lại.');
      }
      return false;
    }).finally(() => {
      autosaveInFlight = null;
      if (!autosaveBlocked
          && !autosaveRetryExhausted
          && !autosaveSubmitDrain
          && (autosavePending
            || autosavePersistedGeneration < autosaveGeneration)) {
        const delay = autosaveRetryDelay;
        autosaveRetryDelay = 0;
        scheduleAutosave(delay);
      }
    });
    return autosaveInFlight;
  };

  const waitForAutosaveRetry = (delay) => new Promise((resolve) => {
    window.setTimeout(resolve, Math.max(0, delay));
  });

  const drainLatestAnswers = () => {
    window.clearTimeout(autosaveTimer);
    autosaveTimer = null;
    return saveAnswers().then((saved) => {
      if (autosaveBlocked || deadlineSubmission) return saved;
      if (saved
          && autosavePersistedGeneration >= autosaveGeneration) {
        return true;
      }
      if (autosaveRetryExhausted) return false;
      const delay = autosaveRetryDelay;
      autosaveRetryDelay = 0;
      return waitForAutosaveRetry(delay)
        .then(drainLatestAnswers);
    });
  };

  const flushLatestAnswers = () => {
    autosaveSubmitDrain = true;
    autosaveRetryCount = 0;
    autosaveRetryDelay = 0;
    autosaveRetryExhausted = false;
    return drainLatestAnswers()
      .finally(() => {
        autosaveSubmitDrain = false;
      });
  };

  player.addEventListener('input', (event) => {
    const fill = event.target.closest && event.target.closest('[data-fill-question]');
    if (fill) syncFillAnswer(fill);
    const counter = event.target.matches && event.target.matches('[data-writing-answer]')
      ? event.target.parentElement.querySelector('[data-answer-count]')
      : null;
    if (counter) counter.textContent = `${Array.from(event.target.value).length} ký tự`;
    updateProgress();
    markAutosaveDirty();
  });
  player.addEventListener('change', () => {
    updateProgress();
    markAutosaveDirty();
  });

  const formatTime = (seconds) => {
    const safe = Number.isFinite(seconds) && seconds >= 0 ? Math.floor(seconds) : 0;
    return `${String(Math.floor(safe / 60)).padStart(2, '0')}:${String(safe % 60).padStart(2, '0')}`;
  };

  const pauseOtherAudio = (current) => {
    player.querySelectorAll('[data-audio-source]').forEach((audio) => {
      if (audio !== current && !audio.paused) audio.pause();
    });
  };

  player.querySelectorAll('[data-exam-audio]').forEach((control) => {
    const audio = control.querySelector('[data-audio-source]');
    const toggle = control.querySelector('[data-audio-toggle]');
    const range = control.querySelector('[data-audio-range]');
    const time = control.querySelector('[data-audio-time]');
    if (!audio || !toggle || !range || !time) return;

    const paint = () => {
      const duration = Number.isFinite(audio.duration) ? audio.duration : 0;
      range.value = duration > 0 ? String(Math.round((audio.currentTime / duration) * 1000)) : '0';
      time.textContent = `${formatTime(audio.currentTime)} / ${formatTime(duration)}`;
      const playIcon = toggle.querySelector('[data-audio-icon-play]');
      const pauseIcon = toggle.querySelector('[data-audio-icon-pause]');
      if (playIcon) playIcon.hidden = !audio.paused;
      if (pauseIcon) pauseIcon.hidden = audio.paused;
      toggle.setAttribute('aria-label', audio.paused ? 'Phát âm thanh' : 'Tạm dừng âm thanh');
    };
    toggle.addEventListener('click', () => {
      if (audio.paused) {
        pauseOtherAudio(audio);
        audio.play().catch(() => {});
      } else {
        audio.pause();
      }
    });
    range.addEventListener('input', () => {
      if (Number.isFinite(audio.duration) && audio.duration > 0) {
        audio.currentTime = (Number(range.value) / 1000) * audio.duration;
      }
    });
    audio.addEventListener('loadedmetadata', paint);
    audio.addEventListener('timeupdate', paint);
    audio.addEventListener('play', paint);
    audio.addEventListener('pause', paint);
    audio.addEventListener('ended', paint);
    audio.addEventListener('error', () => {
      toggle.disabled = true;
      time.textContent = 'Không thể phát';
    });
    paint();
  });

  const initializeSplitResize = () => {
    player.querySelectorAll('[data-split-handle]').forEach((handle) => {
      const container = handle.closest('.exam-workspace, .writing-workspace');
      if (!container) return;
      let dragging = false;
      let currentRatio = Number(handle.getAttribute('aria-valuenow')) || 50;
      const setRatio = (ratio) => {
        currentRatio = Math.max(32, Math.min(68, ratio));
        document.documentElement.style.setProperty('--exam-source-width', `${currentRatio}%`);
        handle.setAttribute('aria-valuenow', String(Math.round(currentRatio)));
      };
      const move = (event) => {
        if (!dragging) return;
        const rect = container.getBoundingClientRect();
        const ratio = ((event.clientX - rect.left) / rect.width) * 100;
        setRatio(ratio);
      };
      handle.addEventListener('pointerdown', (event) => {
        dragging = true;
        handle.setPointerCapture(event.pointerId);
      });
      handle.addEventListener('pointermove', move);
      handle.addEventListener('pointerup', (event) => {
        dragging = false;
        if (handle.hasPointerCapture(event.pointerId)) handle.releasePointerCapture(event.pointerId);
      });
      handle.addEventListener('pointercancel', () => { dragging = false; });
      handle.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        if (event.key === 'Home') setRatio(32);
        else if (event.key === 'End') setRatio(68);
        else setRatio(currentRatio + (event.key === 'ArrowLeft' ? -2 : 2));
      });
    });
  };
  initializeSplitResize();

  ['contextmenu', 'copy', 'cut', 'paste', 'drop', 'dragstart'].forEach((eventName) => {
    player.addEventListener(eventName, (event) => event.preventDefault());
  });
  player.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && ['c', 'x', 'v'].includes(event.key.toLowerCase())) {
      event.preventDefault();
    }
  });

  const selectionTools = player.querySelector('[data-selection-tools]');
  const noteComposer = player.querySelector('[data-note-composer]');
  const noteInput = player.querySelector('[data-note-input]');
  let savedRange = null;
  let selectedQuote = '';

  const hideSelectionTools = () => {
    if (!selectionTools) return;
    selectionTools.hidden = true;
    if (noteComposer) noteComposer.hidden = true;
    savedRange = null;
    selectedQuote = '';
  };

  const closestSelectable = (node) => {
    const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    return element && element.closest('.exam-selectable');
  };

  const selectionInsideAllowedRegion = (range) => {
    const startRegion = closestSelectable(range.startContainer);
    const endRegion = closestSelectable(range.endContainer);
    return Boolean(startRegion && startRegion === endRegion);
  };

  const showSelectionTools = () => {
    if (!selectionTools) return;
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
      hideSelectionTools();
      return;
    }
    const quote = selection.toString().trim();
    const range = selection.getRangeAt(0);
    if (!quote || !selectionInsideAllowedRegion(range)) {
      hideSelectionTools();
      return;
    }
    savedRange = range.cloneRange();
    selectedQuote = quote.slice(0, 500);
    const rect = range.getBoundingClientRect();
    selectionTools.hidden = false;
    if (noteComposer) noteComposer.hidden = true;
    const width = selectionTools.offsetWidth || 230;
    const left = Math.max(10, Math.min(window.innerWidth - width - 10, rect.left + (rect.width - width) / 2));
    const top = Math.max(10, rect.top - (selectionTools.offsetHeight || 48) - 8);
    selectionTools.style.left = `${left}px`;
    selectionTools.style.top = `${top}px`;
  };

  document.addEventListener('mouseup', (event) => {
    if (selectionTools && selectionTools.contains(event.target)) return;
    window.setTimeout(showSelectionTools, 0);
  });

  if (selectionTools) {
    selectionTools.addEventListener('mousedown', (event) => {
      if (!event.target.matches('input')) event.preventDefault();
    });
  }

  const wrapSavedRange = (className, noteId) => {
    if (!savedRange) return null;
    const mark = document.createElement('mark');
    mark.className = className;
    if (noteId) mark.dataset.noteId = noteId;
    try {
      mark.appendChild(savedRange.extractContents());
      savedRange.insertNode(mark);
      return mark;
    } catch (error) {
      return null;
    }
  };

  const notesKey = `ksh-exam-notes:${attemptId}`;
  let notes = [];
  try {
    const stored = JSON.parse(sessionStore.get(notesKey) || '[]');
    notes = Array.isArray(stored) ? stored : [];
  } catch (error) {
    notes = [];
  }

  const persistNotes = () => {
    sessionStore.set(notesKey, JSON.stringify(notes));
  };

  const notesDrawer = player.querySelector('[data-notes-drawer]');
  const notesBackdrop = player.querySelector('[data-note-backdrop]');
  const notesList = player.querySelector('[data-notes-list]');
  const notesEmpty = player.querySelector('[data-notes-empty]');
  const noteCount = player.querySelector('[data-note-count]');
  let notesReturnFocus = null;

  const renderNotes = () => {
    if (!notesList) return;
    notesList.replaceChildren();
    notes.forEach((note) => {
      const item = document.createElement('article');
      item.className = 'exam-note-item';
      const quote = document.createElement('blockquote');
      quote.textContent = `“${note.quote}”`;
      const body = document.createElement('p');
      body.textContent = note.text;
      const footer = document.createElement('footer');
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = 'Xóa';
      remove.addEventListener('click', () => {
        notes = notes.filter((candidate) => candidate.id !== note.id);
        player.querySelectorAll(`mark[data-note-id="${note.id}"]`).forEach((mark) => {
          mark.replaceWith(...mark.childNodes);
        });
        persistNotes();
        renderNotes();
      });
      footer.appendChild(remove);
      item.append(quote, body, footer);
      notesList.appendChild(item);
    });
    if (notesEmpty) notesEmpty.hidden = notes.length > 0;
    if (noteCount) {
      noteCount.hidden = notes.length === 0;
      noteCount.textContent = String(notes.length);
    }
  };

  const openNotes = (event) => {
    notesReturnFocus = event?.currentTarget || document.activeElement;
    if (notesDrawer) {
      notesDrawer.classList.add('is-open');
      notesDrawer.setAttribute('aria-hidden', 'false');
      notesDrawer.removeAttribute('inert');
      notesDrawer.querySelector('[data-note-close]')?.focus();
    }
    player.querySelectorAll('[data-note-toggle]').forEach((button) => {
      button.setAttribute('aria-expanded', 'true');
    });
    if (notesBackdrop) notesBackdrop.classList.add('is-open');
  };
  const closeNotes = () => {
    if (notesDrawer) {
      notesDrawer.classList.remove('is-open');
      notesDrawer.setAttribute('aria-hidden', 'true');
      notesDrawer.setAttribute('inert', '');
    }
    player.querySelectorAll('[data-note-toggle]').forEach((button) => {
      button.setAttribute('aria-expanded', 'false');
    });
    if (notesBackdrop) notesBackdrop.classList.remove('is-open');
    if (notesReturnFocus instanceof HTMLElement
        && document.contains(notesReturnFocus)) {
      notesReturnFocus.focus();
    }
    notesReturnFocus = null;
  };
  player.querySelectorAll('[data-note-toggle]').forEach((button) => button.addEventListener('click', openNotes));
  player.querySelectorAll('[data-note-close]').forEach((button) => button.addEventListener('click', closeNotes));
  if (notesBackdrop) notesBackdrop.addEventListener('click', closeNotes);
  document.addEventListener('keydown', (event) => {
    if (!notesDrawer?.classList.contains('is-open')) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeNotes();
      return;
    }
    if (event.key !== 'Tab') return;
    const controls = Array.from(notesDrawer.querySelectorAll(
      'a[href], button:not([disabled]), input:not([disabled]), '
      + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )).filter(element => element.getClientRects().length > 0);
    if (controls.length === 0) {
      event.preventDefault();
      notesDrawer.focus();
      return;
    }
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  const highlightButton = player.querySelector('[data-selection-highlight]');
  if (highlightButton) {
    highlightButton.addEventListener('click', () => {
      wrapSavedRange('exam-highlight-green');
      window.getSelection().removeAllRanges();
      hideSelectionTools();
    });
  }

  const noteButton = player.querySelector('[data-selection-note]');
  if (noteButton) {
    noteButton.addEventListener('click', () => {
      if (!noteComposer || !noteInput) return;
      noteComposer.hidden = false;
      noteInput.value = '';
      noteInput.focus();
    });
  }

  const saveNote = () => {
    if (!noteInput || !savedRange) return;
    const text = noteInput.value.trim();
    if (!text) {
      noteInput.focus();
      return;
    }
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const mark = wrapSavedRange('exam-note-yellow', id);
    if (!mark) return;
    notes.unshift({ id: id, quote: selectedQuote, text: text, groupIndex: activeGroup });
    persistNotes();
    renderNotes();
    window.getSelection().removeAllRanges();
    hideSelectionTools();
  };
  const noteSave = player.querySelector('[data-note-save]');
  if (noteSave) noteSave.addEventListener('click', saveNote);
  if (noteInput) {
    noteInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        saveNote();
      }
    });
  }

  document.addEventListener('mousedown', (event) => {
    if (selectionTools && !selectionTools.hidden && !selectionTools.contains(event.target)
        && !event.target.closest('.exam-selectable')) {
      hideSelectionTools();
    }
  });

  const timer = player.querySelector('[data-room-timer]');
  const timerAnnouncement = player.querySelector('[data-timer-announcement]');
  if (timer) {
    const deadlineEpochMs = Number(player.dataset.deadlineEpochMs);
    const serverNowEpochMs = Number(player.dataset.serverNowEpochMs);
    if (!Number.isFinite(deadlineEpochMs) || !Number.isFinite(serverNowEpochMs)) {
      timer.textContent = '--:--';
      timer.classList.add('is-unconfigured');
    } else {
      const browserStartedAt = Date.now();
      let lastAnnounced = null;
      let expired = false;
      const remainingSeconds = () => Math.max(
        0,
        Math.ceil((deadlineEpochMs
          - (serverNowEpochMs + (Date.now() - browserStartedAt))) / 1000)
      );
      const paintTimer = () => {
        const remaining = remainingSeconds();
        timer.textContent = formatTime(remaining);
        timer.classList.toggle('is-danger', remaining <= 300);
        if (timerAnnouncement && remaining <= 300 && lastAnnounced !== 300) {
          lastAnnounced = 300;
          timerAnnouncement.textContent = 'Còn 5 phút làm bài.';
        }
        if (timerAnnouncement && remaining <= 60 && lastAnnounced !== 60) {
          lastAnnounced = 60;
          timerAnnouncement.textContent = 'Còn 1 phút làm bài.';
        }
        if (timerAnnouncement && remaining === 0 && !expired) {
          expired = true;
          timerAnnouncement.textContent = 'Đã hết giờ. Bài đang được nộp.';
          deadlineSubmission = true;
          window.clearInterval(interval);
          allowNavigation = true;
          player.requestSubmit();
        }
      };
      const interval = window.setInterval(() => {
        paintTimer();
      }, 250);
      paintTimer();
    }
  }

  let nativeSubmitAuthorized = false;
  player.addEventListener('submit', (event) => {
    player.querySelectorAll('[data-fill-question]').forEach(syncFillAnswer);
    if (!nativeSubmitAuthorized) {
      event.preventDefault();
      if (submissionPending) return;
      submissionPending = true;
      const submitControls = Array.from(
        player.querySelectorAll('button[type="submit"], input[type="submit"]')
      );
      submitControls.forEach((control) => {
        control.disabled = true;
      });
      if (deadlineSubmission) {
        nativeSubmitAuthorized = true;
        allowNavigation = true;
        player.submit();
        return;
      }
      window.clearTimeout(autosaveTimer);
      flushLatestAnswers().then((saved) => {
        if (!saved) {
          if (deadlineSubmission && !nativeSubmitAuthorized) {
            nativeSubmitAuthorized = true;
            allowNavigation = true;
            player.submit();
          }
          return;
        }
        if (autosaveBlocked) return;
        nativeSubmitAuthorized = true;
        allowNavigation = true;
        player.submit();
      }).finally(() => {
        if (!nativeSubmitAuthorized && !deadlineSubmission) {
          submissionPending = false;
          submitControls.forEach((control) => {
            control.disabled = false;
          });
        }
      });
      return;
    }
    allowNavigation = true;
  });
  player.querySelectorAll('[data-exit-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
      event.preventDefault();
      if (exitPending || submissionPending) return;
      exitPending = true;
      link.setAttribute('aria-disabled', 'true');
      window.clearTimeout(autosaveTimer);
      flushLatestAnswers().then((saved) => {
        if (saved && !autosaveBlocked) {
          allowNavigation = true;
          window.location.assign(link.href);
          return;
        }
        if (deadlineSubmission) {
          player.requestSubmit();
        }
      }).finally(() => {
        if (!allowNavigation) {
          exitPending = false;
          link.removeAttribute('aria-disabled');
        }
      });
    });
  });
  window.addEventListener('beforeunload', (event) => {
    if (!allowNavigation) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  player.querySelectorAll('[data-writing-answer]').forEach((textarea) => {
    const counter = textarea.parentElement.querySelector('[data-answer-count]');
    if (counter) counter.textContent = `${Array.from(textarea.value).length} ký tự`;
  });

  renderNotes();
  showGroup(0, false);
})();
