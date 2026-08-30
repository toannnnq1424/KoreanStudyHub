/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Student class lesson workspace
   ----------------------------------------------------------------------------
   Owns the accessible lesson tabs, quick-resource shortcuts, previous/next
   lesson links, server-timed engagement heartbeats and the leave-class flow.
   Progress is never estimated locally: the UI only renders persisted values
   returned by the checkpoint endpoint.
   ══════════════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  var TAB_CHANGE_EVENT = 'ksh:lesson-tab-change';
  var ENGAGEMENT_KEYS = ['CONTENT', 'VIDEO', 'ATTACHMENTS'];

  function selectedTab(tabList) {
    return tabList && tabList.querySelector(
      '.student-lesson-tab[role="tab"][aria-selected="true"]'
    );
  }

  function engagementKey(tab) {
    return tab ? tab.getAttribute('data-engagement-tab') : null;
  }

  function activateLessonTab(tabList, nextTab, focus) {
    if (!tabList || !nextTab) return;

    var previousTab = selectedTab(tabList);
    var tabs = Array.prototype.slice.call(
      tabList.querySelectorAll('.student-lesson-tab[role="tab"]')
    );

    tabs.forEach(function (tab) {
      var selected = tab === nextTab;
      var panelId = tab.getAttribute('aria-controls');
      var panel = panelId ? document.getElementById(panelId) : null;

      tab.setAttribute('aria-selected', selected ? 'true' : 'false');
      tab.setAttribute('tabindex', selected ? '0' : '-1');
      if (panel) panel.hidden = !selected;
    });

    if (focus) nextTab.focus();
    if (previousTab === nextTab) return;

    tabList.dispatchEvent(new CustomEvent(TAB_CHANGE_EVENT, {
      bubbles: true,
      detail: {
        previousTab: previousTab,
        nextTab: nextTab,
        previousKey: engagementKey(previousTab),
        nextKey: engagementKey(nextTab)
      }
    }));
  }

  function setupLessonTabs() {
    document.querySelectorAll('.student-lesson-tabs[role="tablist"]')
      .forEach(function (tabList) {
        var tabs = Array.prototype.slice.call(
          tabList.querySelectorAll('.student-lesson-tab[role="tab"]')
        );
        if (!tabs.length) return;

        tabs.forEach(function (tab, index) {
          tab.addEventListener('click', function () {
            activateLessonTab(tabList, tab, false);
          });

          tab.addEventListener('keydown', function (event) {
            var nextIndex = index;
            if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length;
            else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + tabs.length) % tabs.length;
            else if (event.key === 'Home') nextIndex = 0;
            else if (event.key === 'End') nextIndex = tabs.length - 1;
            else return;

            event.preventDefault();
            activateLessonTab(tabList, tabs[nextIndex], true);
          });
        });
      });
  }

  function setupAdaptiveClassMenu() {
    var menu = document.querySelector('.student-class-menu[data-auto-collapse="true"]');
    if (!menu) return;

    var chapterCount = document.querySelectorAll(
      '.student-lessons-outline .student-lessons-outline-section'
    ).length;

    // Keep the normal class navigation visible for short courses. Only a
    // genuinely long outline needs the extra vertical room gained by folding
    // the menu; the learner can always reopen it from the native summary.
    menu.open = chapterCount < 5;
  }

  function setupQuickResources() {
    document.querySelectorAll('[data-lesson-tab-target]').forEach(function (trigger) {
      trigger.addEventListener('click', function () {
        var tab = document.getElementById(trigger.getAttribute('data-lesson-tab-target'));
        var tabList = tab && tab.closest('.student-lesson-tabs[role="tablist"]');
        if (!tab || !tabList) return;

        activateLessonTab(tabList, tab, true);
        var panelId = tab.getAttribute('aria-controls');
        var panel = panelId ? document.getElementById(panelId) : null;
        (panel || tabList).scrollIntoView({behavior: 'smooth', block: 'start'});
      });
    });
  }

  function setupLessonNeighbours() {
    var workspace = document.querySelector('[data-lesson-workspace]');
    if (!workspace) return;

    var outlineLinks = Array.prototype.slice.call(
      document.querySelectorAll('.student-lessons-outline-link')
    );
    var currentIndex = outlineLinks.findIndex(function (link) {
      return link.getAttribute('aria-current') === 'page';
    });
    if (currentIndex < 0) return;

    var previous = workspace.querySelector('[data-lesson-previous]');
    var next = workspace.querySelector('[data-lesson-next]');
    var empty = workspace.querySelector('[data-lesson-neighbours-empty]');

    function configure(target, source, label) {
      if (!target || !source) return false;
      var titleNode = source.querySelector('.student-lessons-outline-title');
      var title = titleNode ? titleNode.textContent.trim() : source.textContent.trim();
      var targetTitle = target.querySelector('[data-neighbour-title]');

      target.href = source.href;
      target.hidden = false;
      target.setAttribute('aria-label', label + ': ' + title);
      if (targetTitle) targetTitle.textContent = title;
      return true;
    }

    var hasPrevious = configure(previous, outlineLinks[currentIndex - 1], 'Bài trước');
    var hasNext = configure(next, outlineLinks[currentIndex + 1], 'Bài tiếp theo');
    if (empty) empty.hidden = hasPrevious || hasNext;
  }

  function metaContent(name) {
    var meta = document.querySelector('meta[name="' + name + '"]');
    return meta ? meta.getAttribute('content') || '' : '';
  }

  function toBoolean(value) {
    return value === true || value === 'true';
  }

  function toNumber(value, fallback) {
    var parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function itemFromElement(item) {
    return {
      applicable: toBoolean(item && item.dataset.applicable),
      seconds: toNumber(item && item.dataset.seconds, 0),
      requiredSeconds: toNumber(item && item.dataset.requiredSeconds, 60),
      complete: toBoolean(item && item.dataset.complete),
      satisfied: toBoolean(item && item.dataset.satisfied)
    };
  }

  function initialEngagementState(workspace) {
    var state = {
      eligible: toBoolean(workspace.dataset.eligible),
      overallCompleted: toBoolean(workspace.dataset.overallCompleted),
      overallPercent: toNumber(workspace.dataset.overallPercent, 0)
    };

    ENGAGEMENT_KEYS.forEach(function (key) {
      var element = workspace.querySelector('[data-lesson-checkpoint="' + key + '"]');
      state[key.toLowerCase()] = itemFromElement(element);
    });
    return state;
  }

  function normalizedItem(value, fallback) {
    value = value && typeof value === 'object' ? value : {};
    fallback = fallback || {};
    var required = toNumber(value.requiredSeconds, toNumber(fallback.requiredSeconds, 60));
    var seconds = clamp(toNumber(value.seconds, toNumber(fallback.seconds, 0)), 0, required);
    var applicable = typeof value.applicable === 'boolean'
      ? value.applicable : toBoolean(fallback.applicable);
    var complete = typeof value.complete === 'boolean'
      ? value.complete : (applicable && seconds >= required);
    var satisfied = typeof value.satisfied === 'boolean'
      ? value.satisfied : (!applicable || complete);

    return {
      applicable: applicable,
      seconds: seconds,
      requiredSeconds: required,
      complete: complete,
      satisfied: satisfied
    };
  }

  function normalizeEngagementState(payload, fallback) {
    payload = payload && typeof payload === 'object' ? payload : {};
    fallback = fallback || {};
    var state = {
      eligible: typeof payload.eligible === 'boolean'
        ? payload.eligible : toBoolean(fallback.eligible),
      overallCompleted: typeof payload.overallCompleted === 'boolean'
        ? payload.overallCompleted : toBoolean(fallback.overallCompleted),
      overallPercent: clamp(
        toNumber(payload.overallPercent, toNumber(fallback.overallPercent, 0)), 0, 100)
    };

    ENGAGEMENT_KEYS.forEach(function (key) {
      var property = key.toLowerCase();
      state[property] = normalizedItem(payload[property], fallback[property]);
    });
    return state;
  }

  function setProgressState(workspace, label, modifier) {
    var stateNode = workspace.querySelector('[data-lesson-progress-state]');
    if (!stateNode) return;
    stateNode.textContent = label;
    stateNode.classList.remove('is-paused', 'is-synced', 'is-error');
    if (modifier) stateNode.classList.add(modifier);
  }

  function announceEngagementTransition(workspace, previous, current) {
    if (!previous || !current) return;
    var announcement = workspace.querySelector('[data-lesson-engagement-announcement]');
    if (!announcement) return;

    var labels = {
      CONTENT: 'Nội dung bài học',
      VIDEO: 'Video',
      ATTACHMENTS: 'Tài liệu đính kèm'
    };
    var messages = [];
    ENGAGEMENT_KEYS.forEach(function (key) {
      var property = key.toLowerCase();
      if (!previous[property].complete && current[property].complete) {
        messages.push(labels[key] + ' đã xem đủ thời gian yêu cầu.');
      }
    });
    if (!previous.eligible && current.eligible && !current.overallCompleted) {
      messages.push('Checklist đã hoàn tất. Nút Đánh dấu hoàn thành đã được mở khóa.');
    }
    if (!previous.overallCompleted && current.overallCompleted) {
      messages.push('Bài học đã được đánh dấu hoàn thành.');
    }
    if (messages.length) announcement.textContent = messages.join(' ');
  }

  function renderProgressActivityState(workspace, state) {
    if (state.overallCompleted) {
      setProgressState(workspace, 'Đã hoàn thành', 'is-synced');
      return;
    }
    if (state.eligible) {
      setProgressState(workspace, 'Đủ điều kiện', 'is-synced');
      return;
    }
    if (document.visibilityState === 'hidden') {
      setProgressState(workspace, 'Tạm dừng', 'is-paused');
      return;
    }

    var tabList = document.querySelector('.student-lesson-tabs[role="tablist"]');
    var key = engagementKey(selectedTab(tabList));
    var item = key && state[key.toLowerCase()];
    if (!item || !item.applicable) {
      setProgressState(workspace, 'Tab không áp dụng', 'is-paused');
    } else if (item.complete) {
      setProgressState(workspace, 'Mục này đã đủ', 'is-synced');
    } else {
      setProgressState(workspace, 'Đang ghi nhận', '');
    }
  }

  function renderEngagementState(workspace, state) {
    var completedApplicable = 0;
    var applicableCount = 0;

    ENGAGEMENT_KEYS.forEach(function (key) {
      var property = key.toLowerCase();
      var value = normalizedItem(state[property]);
      var item = workspace.querySelector('[data-lesson-checkpoint="' + key + '"]');
      if (!item) return;

      item.dataset.applicable = String(value.applicable);
      item.dataset.seconds = String(value.seconds);
      item.dataset.requiredSeconds = String(value.requiredSeconds);
      item.dataset.complete = String(value.complete);
      item.dataset.satisfied = String(value.satisfied);
      item.classList.toggle('is-complete', value.complete);
      item.classList.toggle('is-not-applicable', !value.applicable);

      var status = item.querySelector('[data-checkpoint-status]');
      if (!value.applicable) {
        if (status) status.textContent = 'Không áp dụng cho bài này';
      } else {
        applicableCount += 1;
        if (value.complete) completedApplicable += 1;
        if (status) {
          status.textContent = value.complete
            ? 'Đã xem đủ ' + value.requiredSeconds + ' giây'
            : 'Còn ' + Math.max(0, value.requiredSeconds - value.seconds) + ' giây';
        }
      }
    });

    var percent = clamp(toNumber(state.overallPercent, 0), 0, 100);
    var ring = workspace.querySelector('[data-lesson-progress-ring]');
    var percentNode = workspace.querySelector('[data-lesson-progress-percent]');
    var countNode = workspace.querySelector('[data-lesson-progress-count]');
    if (ring) {
      ring.style.setProperty('--lesson-progress', String(percent));
      ring.setAttribute('aria-valuenow', String(percent));
      ring.classList.toggle('is-complete', percent >= 100);
    }
    if (percentNode) percentNode.textContent = Math.round(percent) + '%';
    if (countNode) {
      countNode.textContent = applicableCount
        ? completedApplicable + '/' + applicableCount + ' mục hoàn tất'
        : 'Không có mục bắt buộc';
    }

    var completion = document.querySelector('[data-lesson-completion-button]');
    if (completion) {
      var title = completion.querySelector('.student-lesson-completion-copy strong');
      var hint = completion.querySelector('[data-lesson-completion-hint]');
      completion.classList.toggle('is-complete', state.overallCompleted);
      completion.classList.toggle('is-locked', !state.overallCompleted && !state.eligible);
      completion.disabled = state.overallCompleted || !state.eligible;

      if (state.overallCompleted) {
        completion.setAttribute('aria-label', 'Bài học đã hoàn thành');
        if (title) title.textContent = 'Đã hoàn thành';
        if (hint) hint.textContent = 'Tiến độ đã được lưu';
      } else if (state.eligible) {
        completion.setAttribute('aria-label', 'Đánh dấu bài học hoàn thành');
        if (title) title.textContent = 'Đánh dấu hoàn thành';
        if (hint) hint.textContent = 'Checklist đã đủ điều kiện';
      } else {
        completion.setAttribute('aria-label', 'Hoàn tất checklist trước khi đánh dấu bài học');
        if (title) title.textContent = 'Chưa đủ điều kiện';
        if (hint) hint.textContent = 'Hoàn tất checklist để mở khóa';
      }
    }

    renderProgressActivityState(workspace, state);
  }

  function setupEngagementTracker() {
    var workspace = document.querySelector('[data-lesson-workspace]');
    if (!workspace) return;

    var currentState = initialEngagementState(workspace);
    renderEngagementState(workspace, currentState);

    var enabled = workspace.dataset.trackingEnabled === 'true';
    var endpoint = workspace.dataset.progressEndpoint || '';
    var tabList = document.querySelector('.student-lesson-tabs[role="tablist"]');
    if (!enabled || !endpoint || !tabList || typeof window.fetch !== 'function') return;
    if (workspace.dataset.engagementTrackerInitialized === 'true') return;
    workspace.dataset.engagementTrackerInitialized = 'true';

    var interval = clamp(toNumber(workspace.dataset.checkpointIntervalMs, 5000), 4000, 10000);
    var activeKey = engagementKey(selectedTab(tabList));
    var trackingSuspended = false;
    var activeCheckpointPendingKey = null;
    var requestQueue = Promise.resolve();
    var timer = null;

    function isApplicable(key) {
      var item = key && currentState[key.toLowerCase()];
      return Boolean(item && item.applicable && !item.complete);
    }

    function requestCheckpoint(key, active, keepalive) {
      if (!key || ENGAGEMENT_KEYS.indexOf(key) < 0) return Promise.resolve(null);

      var body = new URLSearchParams();
      body.set('tab', key);
      body.set('active', String(Boolean(active)));

      var csrfToken = metaContent('_csrf');
      var csrfHeader = metaContent('_csrf_header');
      if (csrfToken) body.set('_csrf', csrfToken);

      var headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
      };
      if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

      return window.fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers,
        body: body.toString(),
        keepalive: Boolean(keepalive)
      }).then(function (response) {
        if (!response.ok) throw new Error('Checkpoint failed with HTTP ' + response.status);
        return response.json();
      }).then(function (payload) {
        var previousState = currentState;
        currentState = normalizeEngagementState(payload, currentState);
        announceEngagementTransition(workspace, previousState, currentState);
        renderEngagementState(workspace, currentState);
        if (currentState.overallCompleted) stopHeartbeatTimer();
        return currentState;
      });
    }

    function enqueueCheckpoint(key, active, keepalive) {
      requestQueue = requestQueue.catch(function () {
        return null;
      }).then(function () {
        if (trackingSuspended && active) return null;
        return requestCheckpoint(key, active, keepalive);
      }).catch(function () {
        setProgressState(workspace, 'Chưa thể đồng bộ', 'is-error');
        return null;
      });
      return requestQueue;
    }

    function enqueueActiveCheckpoint(key) {
      if (!key || activeCheckpointPendingKey === key) return requestQueue;
      activeCheckpointPendingKey = key;
      return enqueueCheckpoint(key, true, false).finally(function () {
        if (activeCheckpointPendingKey === key) activeCheckpointPendingKey = null;
      });
    }

    function pauseWithBeacon(key) {
      if (!key || ENGAGEMENT_KEYS.indexOf(key) < 0 || !navigator.sendBeacon) return false;
      var body = new URLSearchParams();
      body.set('tab', key);
      body.set('active', 'false');
      var csrfToken = metaContent('_csrf');
      if (csrfToken) body.set('_csrf', csrfToken);
      return navigator.sendBeacon(endpoint, new Blob([body.toString()], {
        type: 'application/x-www-form-urlencoded;charset=UTF-8'
      }));
    }

    function startSelectedTab() {
      if (trackingSuspended || document.visibilityState === 'hidden'
          || currentState.overallCompleted || !isApplicable(activeKey)) return;
      enqueueActiveCheckpoint(activeKey);
    }

    function heartbeat() {
      if (activeCheckpointPendingKey === activeKey || trackingSuspended
          || document.visibilityState === 'hidden'
          || currentState.overallCompleted || !isApplicable(activeKey)) return;
      enqueueActiveCheckpoint(activeKey);
    }

    function stopHeartbeatTimer() {
      if (timer === null) return;
      window.clearInterval(timer);
      timer = null;
    }

    function startHeartbeatTimer() {
      if (timer !== null || trackingSuspended || currentState.overallCompleted) return;
      timer = window.setInterval(heartbeat, interval);
    }

    tabList.addEventListener(TAB_CHANGE_EVENT, function (event) {
      var previousKey = event.detail && event.detail.previousKey;
      var nextKey = event.detail && event.detail.nextKey;
      if (!nextKey || ENGAGEMENT_KEYS.indexOf(nextKey) < 0) return;

      if (previousKey) enqueueCheckpoint(previousKey, false, false);
      activeKey = nextKey;
      renderEngagementState(workspace, currentState);
      startSelectedTab();
    });

    document.addEventListener('visibilitychange', function () {
      if (trackingSuspended || !activeKey) return;
      if (document.visibilityState === 'hidden') {
        enqueueCheckpoint(activeKey, false, true).then(function () {
          if (!currentState.overallCompleted) {
            setProgressState(workspace, 'Tạm dừng', 'is-paused');
          }
        });
      } else {
        renderEngagementState(workspace, currentState);
        startSelectedTab();
      }
    });

    window.addEventListener('pagehide', function () {
      if (trackingSuspended) return;
      trackingSuspended = true;
      stopHeartbeatTimer();
      if (!pauseWithBeacon(activeKey)) requestCheckpoint(activeKey, false, true).catch(function () {});
    });

    window.addEventListener('pageshow', function (event) {
      if (!event.persisted || !trackingSuspended) return;
      trackingSuspended = false;
      activeKey = engagementKey(selectedTab(tabList));
      renderEngagementState(workspace, currentState);
      startSelectedTab();
      startHeartbeatTimer();
    });

    if (!currentState.overallCompleted) {
      startSelectedTab();
      startHeartbeatTimer();
    }
  }

  setupAdaptiveClassMenu();
  setupLessonTabs();
  setupQuickResources();
  setupLessonNeighbours();
  setupEngagementTracker();

  document.addEventListener('click', function (event) {
    var trigger = event.target.closest('[data-action="leave-class"]');
    if (!trigger) return;
    event.preventDefault();

    var classId = trigger.dataset.classId;
    var className = trigger.dataset.className || 'này';
    var form = document.getElementById('leave-form-' + classId);
    if (!form) return;

    // No modal helper loaded → submit directly rather than trap the user.
    if (!window.KshModal || !window.KshModal.confirm) {
      form.submit();
      return;
    }
    window.KshModal.confirm({
      title: 'Rời lớp học',
      body: 'Bạn có chắc muốn rời lớp ' + className + '?',
      confirmLabel: 'Rời lớp',
      onConfirm: function () { form.submit(); }
    });
  });
})();
