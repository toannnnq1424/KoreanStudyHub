/**
 * Class board compose form — rich-text editor wiring, image upload, the
 * newsfeed-style collapse/expand toggle on the compose box, and the per-card
 * kebab menu that holds the edit/delete actions.
 *
 * The hidden input named "content" is the field actually submitted. Quill
 * live-syncs its HTML into that input on every text-change.
 *
 * Images are uploaded at paste/insert time via window.LfQuill.createQuill, so
 * in the normal case nothing remains to upload at submit. The submit handler
 * registered here is a safety net for a surviving data: URI, and it is the
 * form's SINGLE submit orchestrator — a second independent listener would be
 * the double-submit hazard documented in the deferred-upload rule.
 *
 * window.LfQuill exports only isEmptyHtml, waitForQuill, createQuill, and
 * rewriteDataImages. uploadImage and bindImagePaste are closure-private, which
 * is why image paste is obtained by going through createQuill rather than
 * binding it here.
 *
 * The compose box collapses on an outside click or Escape only when the draft
 * is empty. Collapsing a non-empty box would hide typed work, and after a
 * validation error it would also hide the error message shown beside the
 * rejected draft. Emptiness is read from the hidden input rather than from a
 * Quill instance, so the rule holds on the fallback path too.
 *
 * When Quill fails to load (CDN blocked, offline), the host element degrades to
 * a plain contentEditable wired to the same hidden input, so the form stays
 * usable rather than silently submitting nothing.
 *
 * This script deliberately never reads #flash-data or data-flash-* attributes —
 * notifications.js is the sole owner of draining flash messages.
 */
(function () {
    'use strict';

    /** Toolbar shared by the editor and the footer "Thêm hình ảnh" button. */
    var TOOLBAR = [
        [{ header: [2, 3, false] }],
        ['bold', 'italic', 'underline'],
        [{ list: 'ordered' }, { list: 'bullet' }],
        ['link', 'image'],
        ['clean']
    ];

    /** True when the editor holds neither text nor an image. */
    function isEmpty(html) {
        if (window.LfQuill && window.LfQuill.isEmptyHtml) {
            return window.LfQuill.isEmptyHtml(html);
        }
        return !html || !String(html).replace(/<[^>]*>/g, '').trim();
    }

    /** Reflects editor emptiness onto the submit button's disabled state. */
    function syncSubmitState(hidden) {
        var submit = document.querySelector('[data-announcement-submit]');
        if (!submit) return;
        submit.disabled = isEmpty(hidden.value);
    }

    function initAnnouncementEditor() {
        var hidden = document.querySelector('[data-announcement-value]');
        var host = document.querySelector('[data-announcement-editor]');
        if (!hidden || !host) return;

        var form = document.querySelector('[data-announcement-form]');
        // Class-scoped upload URL, so it cannot be a constant in this file.
        var imageUrl = form && form.getAttribute('data-image-url');

        if (window.Quill && window.LfQuill && imageUrl) {
            // createQuill registers the image toolbar handler AND paste handling
            // internally; bindImagePaste is not reachable from outside that module.
            var editor = window.LfQuill.createQuill(
                host, hidden, 'Nhập nội dung thông báo…', TOOLBAR, imageUrl);
            if (editor) {
                editor.clipboard.dangerouslyPasteHTML(hidden.value || '');
                editor.on('text-change', function () {
                    syncSubmitState(hidden);
                });
                bindImageButton(editor, host);
                bindSubmitFlow(form, hidden, editor, imageUrl);
                syncSubmitState(hidden);
                return;
            }
        }

        // Fallback: Quill unavailable, keep the field editable and submittable.
        host.contentEditable = 'true';
        host.innerHTML = hidden.value || '';
        host.addEventListener('input', function () {
            hidden.value = host.innerHTML;
            syncSubmitState(hidden);
        });
        syncSubmitState(hidden);
    }

    /**
     * Points the footer "Thêm hình ảnh" button at the Quill toolbar's own image
     * button, so both affordances share one upload path rather than duplicating it.
     */
    function bindImageButton(editor, host) {
        var btn = document.querySelector('[data-announcement-image]');
        if (!btn) return;
        btn.addEventListener('click', function () {
            var container = host.parentNode;
            var toolbarBtn = container && container.querySelector('.ql-toolbar .ql-image');
            if (toolbarBtn) toolbarBtn.click();
        });
    }

    /**
     * The compose form's single submit orchestrator. Images normally upload at
     * paste time, so this only catches a surviving data: URI — it awaits that
     * upload, then re-submits. Registering a second submit listener anywhere else
     * on this form would double-submit.
     */
    function bindSubmitFlow(form, hidden, editor, imageUrl) {
        if (!form || !window.LfQuill || !window.LfQuill.rewriteDataImages) return;
        var proceeding = false;
        form.addEventListener('submit', function (e) {
            if (proceeding) return;
            e.preventDefault();
            window.LfQuill.rewriteDataImages(editor, imageUrl).then(function (ok) {
                if (!ok) return;
                hidden.value = editor.root.innerHTML;
                proceeding = true;
                // requestSubmit, not submit(), so other listeners still run.
                form.requestSubmit();
            });
        });
    }

    /**
     * Collapses the compose box, unless doing so would hide something the user
     * still needs to see.
     *
     * Two reasons to stay open:
     *  - a non-empty draft, because collapsing would hide typed work;
     *  - a rendered validation error, because collapsing hides the explanation of
     *    why the post was rejected. That case is NOT covered by the emptiness
     *    check: the server rejects an empty post, so the box comes back pre-opened
     *    with an error beside a draft that is itself empty.
     *
     * Emptiness is read from the hidden input, which both the Quill path and the
     * contentEditable fallback keep synced, so this never assumes a Quill instance.
     */
    function collapseComposeIfEmpty(box) {
        if (!box || box.getAttribute('data-compose-open') !== 'true') return;
        // The server renders this only after a rejected submit.
        if (box.querySelector('.ann-error')) return;
        var hidden = box.querySelector('[data-announcement-value]');
        if (hidden && !isEmpty(hidden.value)) return;
        box.setAttribute('data-compose-open', 'false');
    }

    /**
     * True while a native file picker opened from the compose box is pending.
     *
     * The image button opens an <input type="file"> picker, which pulls focus out
     * of the document; the click that dismisses the dialog can land outside the
     * box and would otherwise read as an outside click. The flag is cleared on the
     * first interaction after focus returns to the window.
     */
    var pickerPending = false;

    /**
     * Expands the compose box on click and collapses it on an outside click or
     * Escape. The open state lives in data-compose-open so the server can pre-open
     * it after a validation error without this script inspecting any flash payload.
     *
     * Outside-click detection is delegated on document, matching initItemMenus.
     * The two features keep separate listeners because they close on different
     * conditions — menus always close, compose only closes when empty — and a
     * single handler would have to re-derive that split anyway.
     */
    function initComposeToggle() {
        var box = document.querySelector('[data-announcement-compose]');
        var trigger = box && box.querySelector('[data-announcement-open]');
        if (!box || !trigger) return;

        trigger.addEventListener('click', function () {
            box.setAttribute('data-compose-open', 'true');
            var host = box.querySelector('[data-announcement-editor]');
            // Quill's editable node is nested; fall back to the host when absent.
            var target = (host && host.querySelector('.ql-editor')) || host;
            if (target && target.focus) target.focus();
        });

        // The image button hands off to the toolbar's picker; arm the guard here
        // because the toolbar button itself is created by Quill after boot.
        box.addEventListener('click', function (e) {
            if (e.target.closest('[data-announcement-image]') ||
                e.target.closest('.ql-toolbar')) {
                pickerPending = true;
            }
        });

        // Focus returning from the file dialog must not be read as an outside click.
        window.addEventListener('focus', function () {
            pickerPending = false;
        });

        document.addEventListener('mousedown', function (e) {
            if (pickerPending) {
                pickerPending = false;
                return;
            }
            // Quill renders tooltips (link prompt) outside the box in some themes.
            if (e.target.closest('[data-announcement-compose]')) return;
            if (e.target.closest('.ql-tooltip')) return;
            collapseComposeIfEmpty(box);
        });

        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            collapseComposeIfEmpty(box);
        });
    }

    /** Closes every open kebab menu, optionally sparing one. */
    function closeAllMenus(except) {
        document.querySelectorAll('[data-ann-menu]').forEach(function (menu) {
            if (menu === except) return;
            var panel = menu.querySelector('[data-ann-menu-panel]');
            var trigger = menu.querySelector('[data-ann-menu-trigger]');
            if (panel) panel.hidden = true;
            if (trigger) trigger.setAttribute('aria-expanded', 'false');
            disarmDelete(menu);
        });
    }

    /** Resets a delete item back to its single-click idle label. */
    function disarmDelete(menu) {
        var del = menu.querySelector('[data-ann-delete-confirm]');
        if (!del) return;
        del.removeAttribute('data-armed');
        del.textContent = del.getAttribute('data-ann-label-idle') || 'Xoá';
    }

    /**
     * Kebab menu on each announcement card. Delegated on document so cards
     * rendered by any page load are covered without per-card listeners.
     *
     * Edit opens an inline panel in the card body rather than living inside the
     * absolutely-positioned menu: a textarea in a 160px dropdown would overflow
     * the card, so the menu only reveals the panel and then closes itself.
     */
    function initItemMenus() {
        document.addEventListener('click', function (e) {
            var trigger = e.target.closest('[data-ann-menu-trigger]');
            if (trigger) {
                var menu = trigger.closest('[data-ann-menu]');
                var panel = menu && menu.querySelector('[data-ann-menu-panel]');
                if (!panel) return;
                var willOpen = panel.hidden;
                closeAllMenus(menu);
                panel.hidden = !willOpen;
                trigger.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
                if (!willOpen) disarmDelete(menu);
                return;
            }

            var editBtn = e.target.closest('[data-ann-edit-toggle]');
            if (editBtn) {
                var card = editBtn.closest('.ann-item');
                var box = card && card.querySelector('[data-ann-edit]');
                closeAllMenus();
                if (box) {
                    box.hidden = false;
                    var input = box.querySelector('.ann-edit-input');
                    if (input) input.focus();
                }
                return;
            }

            var cancelBtn = e.target.closest('[data-ann-edit-cancel]');
            if (cancelBtn) {
                var editBox = cancelBtn.closest('[data-ann-edit]');
                if (editBox) editBox.hidden = true;
                return;
            }

            // First click arms the delete item; the second submits the real form.
            // A native confirm() would block the page, so confirmation is inline.
            var delBtn = e.target.closest('[data-ann-delete-confirm]');
            if (delBtn) {
                if (delBtn.getAttribute('data-armed') !== 'true') {
                    e.preventDefault();
                    delBtn.setAttribute('data-armed', 'true');
                    delBtn.textContent =
                        delBtn.getAttribute('data-ann-label-confirm') || 'Xoá';
                }
                return;
            }

            // Any click landing outside a trigger or panel closes open menus.
            if (!e.target.closest('[data-ann-menu-panel]')) closeAllMenus();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key !== 'Escape') return;
            closeAllMenus();
        });
    }

    function boot() {
        initAnnouncementEditor();
        initComposeToggle();
        initItemMenus();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();