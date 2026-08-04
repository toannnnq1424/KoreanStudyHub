/* Exam detail tabs (Epic #11): switch the four tabs (thông tin / theo dõi /
 * bài nộp / lịch sử) in place — no full-page reload. The orchestrator fetches
 * the same ?tab= URL the tab link points at, lifts #tabPanel out of the HTML
 * response, and swaps it into the live DOM. Pager links and the submissions
 * search form inside the panel are intercepted too, so the whole detail screen
 * navigates without a reload.
 *
 * Lifecycle: the monitor tab owns two setInterval timers, so its teardown() is
 * invoked before every swap to avoid leaking a poll loop across tabs. The info
 * builder re-mounts from the #lfData JSON island that travels inside #tabPanel.
 *
 * Progressive enhancement: with JS off, every tab link / pager / search is a
 * plain server-rendered navigation, so the screen still works.
 */
(function () {
    'use strict';

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
            var u = new URL(url, window.location.origin);
            return u.searchParams.get('tab') || 'info';
        } catch (e) {
            return 'info';
        }
    }

    var LOADING_HTML =
        '<div class="detail-tab-loading" role="status" aria-live="polite">' +
          '<span class="detail-tab-spinner" aria-hidden="true"></span>' +
          '<span class="detail-tab-loading-text">Đang tải…</span>' +
        '</div>';

    ready(function () {
        var panel = document.getElementById('tabPanel');
        var tabsNav = document.querySelector('.detail-tabs');
        if (!panel) return;

        if (!tabsNav) return;

        var dirtyGuard = window.KshDirtyFormGuard.create(panel);

        // Mark owned so the shared detail-tabs.js (if also loaded) no-ops.
        tabsNav.setAttribute('data-ajax-tabs', 'owned');

        var saveBtn = document.getElementById('lfSave');
        var monitorTeardown = function () {};

        /** Tears down the outgoing tab, then mounts the incoming one. */
        function remount() {
            if (typeof monitorTeardown === 'function') monitorTeardown();
            if (window.LfForm) window.LfForm.mount();
            monitorTeardown = window.MnMonitor
                ? window.MnMonitor.mount(panel)
                : function () {};
        }

        /** Reflects the active tab in the nav + the toolbar Save button. */
        function syncChrome(tab) {
            tabsNav.querySelectorAll('.detail-tab').forEach(function (a) {
                a.classList.toggle('active', tabOf(a.getAttribute('href')) === tab);
            });
            // Save is form-associated with #lfForm, which only exists on the
            // info tab; disable it elsewhere so it never posts an absent form.
            if (saveBtn) saveBtn.disabled = tab !== 'info';
        }

        function showLoading() {
            panel.classList.add('is-loading');
            panel.setAttribute('aria-busy', 'true');
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

        var navigationSequence = 0;
        var navigationController = null;
        var navigationActive = false;

        /**
         * Fetches `url`, swaps #tabPanel in place, and re-mounts tab JS. Falls
         * back to a full navigation on any error (e.g. an auth redirect to the
         * login page, where the response carries no #tabPanel).
         */
        function navigate(url, push) {
            dirtyGuard.beginNavigation();
            var requestId = ++navigationSequence;
            if (navigationController) {
                navigationController.abort();
            }
            navigationController = typeof window.AbortController === 'function'
                ? new window.AbortController()
                : null;
            if (!navigationActive && typeof monitorTeardown === 'function') {
                monitorTeardown();
            }
            navigationActive = true;
            showLoading();
            var fetchOptions = {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            };
            if (navigationController) {
                fetchOptions.signal = navigationController.signal;
            }
            fetch(url, fetchOptions)
                .then(function (r) {
                    if (!r.ok) throw new Error('HTTP ' + r.status);
                    return r.text();
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
                    if (push) dirtyGuard.pushState({ tab: tab }, url);

                    // Re-mount info builder + monitor against the fresh DOM.
                    if (window.LfForm) window.LfForm.mount();
                    monitorTeardown = window.MnMonitor
                        ? window.MnMonitor.mount(panel)
                        : function () {};
                    dirtyGuard.reset();
                    navigationController = null;
                    navigationActive = false;
                })
                .catch(function (error) {
                    if (requestId !== navigationSequence || error.name === 'AbortError') return;
                    navigationController = null;
                    navigationActive = false;
                    // Non-recoverable in-place: hand off to a real navigation.
                    dirtyGuard.allowHardNavigation();
                    window.location.href = url;
                });
        }

        // ── Delegated clicks: tab links + in-panel pager links ─────────────
        document.addEventListener('click', function (e) {
            var link = e.target.closest ? e.target.closest('a') : null;
            if (!link) return;
            var href = link.getAttribute('href');
            if (!href) return;

            var isTab = tabsNav.contains(link) && link.classList.contains('detail-tab');
            var isPanelPager = panel.contains(link) && link.classList.contains('page-link');
            if (!isTab && !isPanelPager) return;

            // Let modified clicks (new tab / download) behave natively.
            if (e.defaultPrevented || e.button !== 0 ||
                e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

            // Keep an already-active exact URL in place. In particular, do not
            // ask to discard an info-form draft only to fetch the same panel.
            if (isTab && link.classList.contains('active') &&
                    tabOf(href) === tabOf(window.location.href)) {
                var sameUrl;
                try {
                    sameUrl = new URL(href, window.location.origin).href ===
                        window.location.href;
                } catch (error) {
                    sameUrl = false;
                }
                if (sameUrl) {
                    e.preventDefault();
                    return;
                }
            }

            e.preventDefault();
            if (!dirtyGuard.confirmNavigation()) return;
            navigate(href, true);
        });

        // ── In-panel submissions search: GET form → AJAX navigation ────────
        document.addEventListener('submit', function (e) {
            var form = e.target;
            if (!panel.contains(form) || !form.classList.contains('sb-search')) return;
            e.preventDefault();
            if (!dirtyGuard.confirmNavigation()) return;
            var action = form.getAttribute('action') || window.location.pathname;
            var params = new URLSearchParams(new FormData(form)).toString();
            navigate(action + (params ? '?' + params : ''), true);
        });

        // ── Back / forward: re-fetch the URL the history entry points at ───
        window.addEventListener('popstate', function (event) {
            dirtyGuard.handlePopState(event, function (url) {
                navigate(url, false);
            });
        });

        // ── First load: mount whatever tab the server rendered ─────────────
        remount();
        dirtyGuard.reset();
        dirtyGuard.installHistory({ tab: tabOf(window.location.href) }, window.location.href);
    });
})();
