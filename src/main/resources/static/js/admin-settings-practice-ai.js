(function () {
  'use strict';

  function meta(name) {
    var element = document.querySelector('meta[name="' + name + '"]');
    return element ? element.getAttribute('content') : null;
  }

  function announce(message) {
    var region = document.getElementById('practiceAiActionResult');
    if (region) region.textContent = message;
  }

  function notify(ok, message) {
    announce(message);
    if (!window.KshToast) return;
    if (ok) window.KshToast.success(message);
    else window.KshToast.error(message);
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.js-practice-ai-reveal').forEach(function (button) {
      button.addEventListener('click', function () {
        var input = document.getElementById('credentialSecret');
        if (!input) return;
        if (button.getAttribute('aria-pressed') === 'true') {
          input.value = '********';
          input.type = 'password';
          button.setAttribute('aria-pressed', 'false');
          announce('Đã ẩn Practice AI secret');
          return;
        }
        button.disabled = true;
        fetch('/admin/settings/practice-ai/profiles/'
            + encodeURIComponent(button.getAttribute('data-profile-id')) + '/secret', {
          headers: { 'Accept': 'application/json' },
          credentials: 'same-origin'
        })
          .then(function (response) {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json();
          })
          .then(function (result) {
            if (!result.ok) throw new Error(result.errorCode || 'Secret unavailable');
            input.value = result.secret;
            input.type = 'text';
            button.setAttribute('aria-pressed', 'true');
            announce('Đã hiện Practice AI secret');
          })
          .catch(function (error) {
            notify(false, error.message || 'Không thể hiện secret');
          })
          .finally(function () { button.disabled = false; });
      });
    });

    document.querySelectorAll('.js-practice-ai-test').forEach(function (button) {
      button.addEventListener('click', function () {
        var purpose = button.getAttribute('data-purpose') || 'Practice AI';
        var url = button.getAttribute('data-url');
        var headers = { 'Accept': 'application/json' };
        var csrfHeader = meta('_csrf_header');
        var csrfToken = meta('_csrf');
        if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

        button.disabled = true;
        announce('Đang kiểm thử capability ' + purpose);
        fetch(url, { method: 'POST', headers: headers, credentials: 'same-origin' })
          .then(function (response) {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json();
          })
          .then(function (result) {
            var message = result.ok
              ? purpose + ': PASS (' + result.durationMs + ' ms)'
              : purpose + ': ' + result.status + ' · ' + result.errorCode;
            notify(result.ok, message);
            window.setTimeout(function () { window.location.reload(); }, 700);
          })
          .catch(function (error) {
            notify(false, purpose + ': ' + (error.message || 'Capability test failed'));
            button.disabled = false;
          });
      });
    });
  });
})();
