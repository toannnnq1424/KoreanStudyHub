(function () {
  'use strict';
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.js-storage-profile-reveal').forEach(function (button) {
      button.addEventListener('click', function () {
        var input = document.getElementById('secretAccessKey');
        var live = document.getElementById('storageProfileActionResult');
        if (!input) return;
        if (button.getAttribute('aria-pressed') === 'true') {
          input.value = '********'; input.type = 'password';
          button.setAttribute('aria-pressed', 'false');
          if (live) live.textContent = 'Đã ẩn storage secret';
          return;
        }
        button.disabled = true;
        fetch('/admin/settings/storage-profiles/'
          + encodeURIComponent(button.getAttribute('data-profile-code')) + '/secret', {
          headers: { 'Accept': 'application/json' }, credentials: 'same-origin'
        }).then(function (response) {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          return response.json();
        }).then(function (result) {
          if (!result.ok) throw new Error(result.errorCode || 'Secret unavailable');
          input.value = result.secret; input.type = 'text';
          button.setAttribute('aria-pressed', 'true');
          if (live) live.textContent = 'Đã hiện storage secret';
        }).catch(function (error) {
          if (window.KshToast) window.KshToast.error(error.message);
          if (live) live.textContent = error.message;
        }).finally(function () { button.disabled = false; });
      });
    });
  });
})();
