/* ═══════════════════════════════════════════════════════════════════════════
   KSH — Shared AJAX detail tabs
   ----------------------------------------------------------------------------
   Progressive enhancement for any detail page that has:
     - nav.detail-tabs with a.detail-tab links (?tab=…)
     - #tabPanel wrapping the active tab body

   On tab click (or in-panel .page-link click) the orchestrator fetches the
   same URL, lifts #tabPanel from the HTML response, and swaps it in place —
   no full-page reload. A loading spinner is shown while the request is in
   flight. History is updated with pushState so Back/Forward keep working.

   With JS off, every tab/pager link is a normal navigation.

   Optional hooks (page scripts may assign these before or after load):
     window.KshDetailTabs.onBeforeSwap()  — teardown outgoing tab (timers…)
     window.KshDetailTabs.onAfterSwap(panel, tab) — remount incoming tab JS
     window.KshDetailTabs.saveButtonSelector — default '.toolbar-save'
     window.KshDetailTabs.infoTabName — default 'info' (save enabled only here)

   Requires nothing else. Safe to include on pages without tabs (no-ops).
   ══════════════════════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  var LOADING_HTML =
    '<div class="detail-tab-loading" role="status" aria-live="polite">' +
      '<span class="detail-tab-spinner" aria-hidden="true"></span>' +
      '<span class="detail-tab-loading-text">Đang tải…</span>' +
    '</div>';

  function ready(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  /** Reads the ?tab= value of a URL, defaulting to 'info'. */
  function tabOf(url) {
    try {
      var parsed = new URL(url, window.location.origin);
      return parsed.searchParams.get('tab') || 'info';
    } catch (error) {
      return 'info';
    }
  }

  function api() {
    if (!window.KshDetailTabs) window.KshDetailTabs = {};
    return window.KshDetailTabs;
  }

  ready(function () {
    var panel = document.getElementById('tabPanel');
    var tabsNav = document.querySelector('.detail-tabs');
    if (!panel || !tabsNav) return;

    // Already claimed by a page-specific orchestrator (e.g. test-detail-tabs).
    if (tabsNav.getAttribute('data-ajax-tabs') === 'owned') return;
    tabsNav.setAttribute('data-ajax-tabs', 'on');

    var hooks = api();
    var saveSelector = hooks.saveButtonSelector || '.toolbar-save';
    var infoTab = hooks.infoTabName || 'info';
    var navigationSequence = 0;
    var navigationController = null;
    var navigationActive = false;

    function saveButtons() {
      return document.querySelectorAll(saveSelector);
    }

    /** Reflects the active tab in the nav + toolbar Save button(s). */
    function syncChrome(tab) {
      tabsNav.querySelectorAll('a.detail-tab').forEach(function (link) {
        var href = link.getAttribute('href');
        var isActive = href && tabOf(href) === tab;
        link.classList.toggle('active', isActive);
        link.setAttribute('aria-selected', isActive ? 'true' : 'false');
      });
      saveButtons().forEach(function (button) {
        button.disabled = tab !== infoTab;
      });
    }

    function showLoading() {
      panel.classList.add('is-loading');
      panel.setAttribute('aria-busy', 'true');
      // Keep min-height so the layout does not jump while the spinner shows.
      if (!panel.style.minHeight) {
        panel.style.minHeight = Math.max(panel.offsetHeight, 120) + 'px';
      }
      panel.innerHTML = LOADING_HTML;
    }

    function clearLoadingState() {
      panel.classList.remove('is-loading');
      panel.removeAttribute('aria-busy');
      panel.style.minHeight = '';
    }

    /**
     * Fetches `url`, swaps #tabPanel in place. Falls back to a full navigation
     * when the response has no #tabPanel (auth redirect, error page, …).
     */
    function navigate(url, push) {
      var requestId = ++navigationSequence;
      if (navigationController) {
        navigationController.abort();
      }
      navigationController = typeof window.AbortController === 'function'
        ? new window.AbortController()
        : null;

      if (!navigationActive && typeof hooks.onBeforeSwap === 'function') {
        try { hooks.onBeforeSwap(); } catch (error) { /* ignore teardown errors */ }
      }
      navigationActive = true;

      showLoading();

      var fetchOptions = {
        headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'text/html' },
        credentials: 'same-origin'
      };
      if (navigationController) {
        fetchOptions.signal = navigationController.signal;
      }

      fetch(url, fetchOptions)
        .then(function (response) {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          // Authentication middleware may redirect to /login.
          if (response.redirected && response.url && response.url.indexOf('/login') !== -1) {
            throw new Error('auth redirect');
          }
          return response.text();
        })
        .then(function (html) {
          if (requestId !== navigationSequence) return;
          var doc = new DOMParser().parseFromString(html, 'text/html');
          var fresh = doc.getElementById('tabPanel');
          if (!fresh) throw new Error('no #tabPanel in response');

          clearLoadingState();
          panel.innerHTML = fresh.innerHTML;

          var tab = tabOf(url);
          syncChrome(tab);

          var title = doc.querySelector('title');
          if (title) document.title = title.textContent;

          if (push) {
            window.history.pushState({ tab: tab }, '', url);
          }

          if (typeof hooks.onAfterSwap === 'function') {
            try { hooks.onAfterSwap(panel, tab); } catch (error) { /* ignore remount errors */ }
          }

          document.dispatchEvent(new CustomEvent('ksh:detail-tab-loaded', {
            detail: { panel: panel, tab: tab, url: url }
          }));

          navigationController = null;
          navigationActive = false;
        })
        .catch(function (error) {
          if (requestId !== navigationSequence || error.name === 'AbortError') return;
          navigationController = null;
          navigationActive = false;
          window.location.href = url;
        });
    }

    document.addEventListener('click', function (event) {
      var link = event.target.closest ? event.target.closest('a') : null;
      if (!link) return;
      var href = link.getAttribute('href');
      if (!href || href.charAt(0) === '#') return;

      var isTab = tabsNav.contains(link) && link.classList.contains('detail-tab');
      var isPanelPager = panel.contains(link) &&
        (link.classList.contains('page-link') || link.closest('.detail-pagination'));
      if (!isTab && !isPanelPager) return;

      // Let modified clicks (new tab) behave natively.
      if (event.defaultPrevented || event.button !== 0 ||
          event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

      // Same active link and exact URL: suppress a redundant round-trip.
      if (isTab && link.classList.contains('active') && tabOf(href) === tabOf(window.location.href)) {
        var sameUrl;
        try {
          sameUrl = new URL(href, window.location.origin).href === window.location.href;
        } catch (error) {
          sameUrl = false;
        }
        if (sameUrl) {
          event.preventDefault();
          return;
        }
      }

      event.preventDefault();
      navigate(href, true);
    });

    window.addEventListener('popstate', function () {
      navigate(window.location.href, false);
    });

    try {
      var initialTab = tabOf(window.location.href);
      if (!window.history.state || !window.history.state.tab) {
        window.history.replaceState({ tab: initialTab }, '', window.location.href);
      }
      syncChrome(initialTab);
    } catch (error) {
      // History state is an enhancement only.
    }
  });
})();
