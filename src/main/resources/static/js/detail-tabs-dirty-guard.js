/*
 * KSH — unsaved-form guard for AJAX detail tabs.
 *
 * The tab orchestrators replace #tabPanel without a page unload, so the
 * browser's native beforeunload protection cannot see a tab click or an AJAX
 * Back/Forward traversal. This helper gives both orchestrators one policy:
 *
 *   - compare the current editable-form state with a post-mount baseline;
 *   - ask before an AJAX navigation can tear down timers or replace the DOM;
 *   - rebound an owned popstate to the rendered entry before asking, so Cancel
 *     leaves the URL, history stack, panel and tab-owned timers untouched;
 *   - suppress a duplicate beforeunload prompt only for an already-authorised
 *     hard-navigation fallback or a real, non-cancelled native form submit.
 *
 * Explicit GET forms are read-only navigation controls (search/filter), not
 * editable drafts. Dynamic controls and rich editors are included because the
 * snapshot is calculated from the live DOM at navigation time.
 */
(function () {
  'use strict';

  var HISTORY_INDEX = '__kshDetailTabsIndex';
  var MESSAGE =
    'Bạn có thay đổi chưa lưu. Rời khỏi tab này sẽ làm mất các thay đổi. Bạn có muốn tiếp tục?';
  var activeGuard = null;

  function isExplicitGet(form) {
    return (form.getAttribute('method') || '').toLowerCase() === 'get';
  }

  function isIgnored(element) {
    if (element.getAttribute('data-dirty-guard') === 'ignore') return true;
    return Boolean(element.closest &&
      element.closest('[data-dirty-guard="ignore"]'));
  }

  function controlState(control) {
    var tag = control.tagName.toLowerCase();
    var type = (control.getAttribute('type') || '').toLowerCase();
    var identity = control.getAttribute('name') ||
      control.getAttribute('id') ||
      control.getAttribute('class') ||
      '';

    if (control.hasAttribute('contenteditable')) {
      return [tag, 'contenteditable', identity, control.innerHTML];
    }
    if (tag === 'select') {
      return [
        tag,
        control.multiple ? 'multiple' : 'single',
        identity,
        Array.prototype.map.call(control.options, function (option) {
          return option.selected ? '1:' + option.value : '0:' + option.value;
        })
      ];
    }
    if (type === 'checkbox' || type === 'radio') {
      return [tag, type, identity, control.checked ? '1' : '0', control.value];
    }
    return [tag, type, identity, control.value];
  }

  function panelSnapshot(panel) {
    var forms = Array.prototype.filter.call(panel.querySelectorAll('form'), function (form) {
      return !isExplicitGet(form) && !isIgnored(form);
    });

    if (!forms.length) return null;

    return JSON.stringify(forms.map(function (form) {
      var controls = Array.prototype.filter.call(
        form.querySelectorAll('input, select, textarea, [contenteditable]'),
        function (control) {
          var type = (control.getAttribute('type') || '').toLowerCase();
          return !isIgnored(control) &&
            !control.disabled &&
            type !== 'button' &&
            type !== 'submit' &&
            type !== 'reset' &&
            type !== 'image';
        }
      );
      return controls.map(controlState);
    }));
  }

  function stateWithIndex(state, index) {
    var copy = {};
    if (state && typeof state === 'object' && !Array.isArray(state)) {
      Object.keys(state).forEach(function (key) {
        copy[key] = state[key];
      });
    }
    copy[HISTORY_INDEX] = index;
    return copy;
  }

  function indexOfState(state) {
    if (!state || typeof state[HISTORY_INDEX] !== 'number') return null;
    return state[HISTORY_INDEX];
  }

  function DirtyFormGuard(panel) {
    this.panel = panel;
    this.baseline = null;
    this.suspended = false;
    this.hardNavigationAllowed = false;
    this.mutationPending = false;
    this.mutationPanelWasInert = false;
    this.mutationPanelAriaBusy = null;
    this.currentHistoryIndex = null;
    this.pendingTraversal = null;
    this.renderedUrl = window.location.href;
    this.renderedState = window.history.state;

    this.onBeforeUnload = this.onBeforeUnload.bind(this);
    this.onNativeSubmit = this.onNativeSubmit.bind(this);
    this.onNativeReset = this.onNativeReset.bind(this);
    window.addEventListener('beforeunload', this.onBeforeUnload);
    window.addEventListener('submit', this.onNativeSubmit);
    window.addEventListener('reset', this.onNativeReset);
    this.reset();
  }

  DirtyFormGuard.prototype.destroy = function () {
    this.clearMutationLock();
    window.removeEventListener('beforeunload', this.onBeforeUnload);
    window.removeEventListener('submit', this.onNativeSubmit);
    window.removeEventListener('reset', this.onNativeReset);
  };

  DirtyFormGuard.prototype.reset = function () {
    this.clearMutationLock();
    this.baseline = panelSnapshot(this.panel);
    this.suspended = false;
    this.hardNavigationAllowed = false;
  };

  DirtyFormGuard.prototype.isDirty = function () {
    // A persisted mutation may still complete after the user leaves. Treat the
    // in-flight interval as protected even when the form matched its baseline.
    if (this.mutationPending) return true;
    if (this.suspended || this.baseline === null) return false;
    return panelSnapshot(this.panel) !== this.baseline;
  };

  DirtyFormGuard.prototype.confirmNavigation = function () {
    // Gate direct clicks while a history rebound is restoring or committing an
    // owned entry, or while a server mutation owns the rendered form.
    if (this.mutationPending || this.pendingTraversal) return false;
    return !this.isDirty() || window.confirm(MESSAGE);
  };

  /*
   * Freezes the current panel for an asynchronous mutation.
   *
   * confirmDirty=true is for operations (AI/question-bank insertion) that do
   * not save the visible draft and will redirect after success. A normal Save
   * passes false: it is preserving the draft, so prompting would be misleading.
   */
  DirtyFormGuard.prototype.beginMutation = function (confirmDirty) {
    if (this.mutationPending || this.pendingTraversal) return false;
    if (confirmDirty && !this.confirmNavigation()) return false;

    this.mutationPending = true;
    this.mutationPanelWasInert = this.panel.hasAttribute('inert');
    this.mutationPanelAriaBusy = this.panel.getAttribute('aria-busy');
    this.panel.setAttribute('inert', '');
    this.panel.setAttribute('aria-busy', 'true');
    return true;
  };

  DirtyFormGuard.prototype.clearMutationLock = function () {
    if (!this.mutationPending) return;
    this.mutationPending = false;
    if (!this.mutationPanelWasInert) {
      this.panel.removeAttribute('inert');
    }
    if (this.mutationPanelAriaBusy === null) {
      this.panel.removeAttribute('aria-busy');
    } else {
      this.panel.setAttribute('aria-busy', this.mutationPanelAriaBusy);
    }
    this.mutationPanelWasInert = false;
    this.mutationPanelAriaBusy = null;
  };

  DirtyFormGuard.prototype.cancelMutation = function () {
    // Keep the original baseline: failed persistence must leave local edits
    // dirty, editable and protected for a retry.
    this.clearMutationLock();
  };

  DirtyFormGuard.prototype.completeMutation = function () {
    this.clearMutationLock();
    this.allowHardNavigation();
  };

  DirtyFormGuard.prototype.beginNavigation = function () {
    // The user has either confirmed the loss or there was no editable draft.
    // Suspend comparisons while the panel temporarily contains the spinner.
    this.suspended = true;
  };

  DirtyFormGuard.prototype.allowHardNavigation = function () {
    this.hardNavigationAllowed = true;
    this.suspended = true;
  };

  DirtyFormGuard.prototype.onBeforeUnload = function (event) {
    if (this.hardNavigationAllowed || !this.isDirty()) return;
    event.preventDefault();
    event.returnValue = '';
  };

  DirtyFormGuard.prototype.onNativeSubmit = function (event) {
    var form = event.target;
    if (!form || !this.panel.contains(form) || isExplicitGet(form) || isIgnored(form)) return;

    // This listener is on window (after target/document listeners). Therefore a
    // custom AJAX submit that called preventDefault() does not accidentally
    // disable protection; a native submit that will really navigate does.
    if (!event.defaultPrevented) {
      this.allowHardNavigation();
    }
  };

  DirtyFormGuard.prototype.onNativeReset = function (event) {
    var form = event.target;
    if (!form || !this.panel.contains(form) || isIgnored(form) ||
        event.defaultPrevented) return;

    // Native reset applies after the event. Re-baseline on the next task so a
    // deliberate reset does not create a false unsaved-change warning.
    var guard = this;
    window.setTimeout(function () {
      if (document.documentElement.contains(form)) guard.reset();
    }, 0);
  };

  DirtyFormGuard.prototype.installHistory = function (state, url) {
    var existingIndex = indexOfState(window.history.state);
    if (existingIndex !== null) {
      this.currentHistoryIndex = existingIndex;
      this.renderedUrl = url;
      this.renderedState = window.history.state;
      return;
    }
    this.currentHistoryIndex = 0;
    this.renderedState = stateWithIndex(state, 0);
    this.renderedUrl = url;
    window.history.replaceState(this.renderedState, '', url);
  };

  DirtyFormGuard.prototype.pushState = function (state, url) {
    var nextIndex = (this.currentHistoryIndex === null ? 0 : this.currentHistoryIndex + 1);
    this.renderedState = stateWithIndex(state, nextIndex);
    this.renderedUrl = url;
    window.history.pushState(this.renderedState, '', url);
    this.currentHistoryIndex = nextIndex;
  };

  /*
   * Handles an AJAX-owned Back/Forward traversal.
   *
   * popstate fires after the browser has moved its history pointer. For a dirty
   * panel we first traverse by the inverse delta, returning to the entry whose
   * DOM is still rendered. Only then do we ask. Cancel stops there. Confirm
   * traverses to the target again and invokes navigate exactly once on arrival.
   * history.go() moves the pointer without inserting/replacing entries.
   */
  DirtyFormGuard.prototype.handlePopState = function (event, navigate) {
    var targetIndex = indexOfState(event.state);
    var targetUrl = window.location.href;
    var pending = this.pendingTraversal;

    if (pending && pending.phase === 'restore') {
      if (targetIndex !== this.currentHistoryIndex) {
        this.pendingTraversal = null;
        return;
      }
      // The rendered entry is restored. Release the gate while asking, then
      // re-arm it only when committing the original traversal.
      this.pendingTraversal = null;
      if (!this.confirmNavigation()) {
        return;
      }
      pending.phase = 'commit';
      this.pendingTraversal = pending;
      window.history.go(pending.delta);
      return;
    }

    if (pending && pending.phase === 'commit') {
      this.pendingTraversal = null;
      this.currentHistoryIndex = targetIndex === null ? pending.targetIndex : targetIndex;
      this.renderedUrl = window.location.href;
      this.renderedState = event.state;
      navigate(window.location.href);
      return;
    }

    if (!this.isDirty()) {
      if (targetIndex !== null) this.currentHistoryIndex = targetIndex;
      this.renderedUrl = targetUrl;
      this.renderedState = event.state;
      navigate(targetUrl);
      return;
    }

    if (targetIndex !== null && this.currentHistoryIndex !== null) {
      var delta = targetIndex - this.currentHistoryIndex;
      if (delta !== 0) {
        this.pendingTraversal = {
          phase: 'restore',
          delta: delta,
          targetIndex: targetIndex
        };
        window.history.go(-delta);
        return;
      }
    }

    // A same-document entry not created by the orchestrator has no usable
    // delta. Cancel restores the visible rendered URL/state with replaceState:
    // no entry is inserted or removed, and the old panel is not paired with a
    // moved URL. Cross-document traversal is protected by beforeunload.
    if (this.confirmNavigation()) {
      if (targetIndex !== null) this.currentHistoryIndex = targetIndex;
      this.renderedUrl = targetUrl;
      this.renderedState = event.state;
      navigate(targetUrl);
    } else {
      window.history.replaceState(this.renderedState, '', this.renderedUrl);
    }
  };

  window.KshDirtyFormGuard = {
    create: function (panel) {
      if (activeGuard) activeGuard.destroy();
      activeGuard = new DirtyFormGuard(panel);
      return activeGuard;
    },
    markClean: function () {
      if (activeGuard) activeGuard.reset();
    },
    confirmNavigation: function () {
      return !activeGuard || activeGuard.confirmNavigation();
    },
    beginMutation: function (confirmDirty) {
      return !activeGuard || activeGuard.beginMutation(confirmDirty);
    },
    cancelMutation: function () {
      if (activeGuard) activeGuard.cancelMutation();
    },
    completeMutation: function () {
      if (activeGuard) activeGuard.completeMutation();
    },
    allowHardNavigation: function () {
      if (activeGuard) activeGuard.allowHardNavigation();
    }
  };
})();
