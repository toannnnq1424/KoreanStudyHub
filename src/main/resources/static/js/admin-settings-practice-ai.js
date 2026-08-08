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
    var providerSelect = document.getElementById('providerProfileId');
    var providerCombobox = document.querySelector('[data-settings-combobox]');
    var providerTrigger = document.getElementById('provider-profile-trigger');
    var providerOptions = document.getElementById('provider-profile-options');
    var modelInput = document.getElementById('model');
    var suggestions = document.getElementById('model-suggestions');
    var directAudioVerification = document.getElementById('direct-audio-model-verification');
    var modelSearchQuery = '';
    var credentialMode = document.getElementById('credentialMode');
    var credentialSecret = document.getElementById('credentialSecret');

    function syncCredentialMode() {
      if (!credentialMode || !credentialSecret) return;
      var adc = credentialMode.value === 'GOOGLE_CLOUD_ADC';
      credentialSecret.disabled = adc;
      credentialSecret.required = !adc;
      if (adc) credentialSecret.value = '';
    }

    if (credentialMode && credentialSecret) {
      credentialMode.addEventListener('change', syncCredentialMode);
      syncCredentialMode();
    }

    function comboboxOption(value) {
      if (!providerOptions) return null;
      return Array.from(providerOptions.querySelectorAll('[role="option"]'))
        .find(function (option) { return option.getAttribute('data-value') === value; }) || null;
    }

    function closeProviderCombobox(returnFocus) {
      if (!providerTrigger || !providerOptions) return;
      providerOptions.hidden = true;
      providerTrigger.setAttribute('aria-expanded', 'false');
      if (providerCombobox) providerCombobox.classList.remove('is-open');
      if (returnFocus) providerTrigger.focus();
    }

    function openProviderCombobox() {
      if (!providerTrigger || !providerOptions || providerTrigger.disabled) return;
      providerOptions.hidden = false;
      providerTrigger.setAttribute('aria-expanded', 'true');
      if (providerCombobox) providerCombobox.classList.add('is-open');
      var selected = providerOptions.querySelector('[aria-selected="true"]');
      var first = providerOptions.querySelector('[role="option"]');
      (selected || first)?.focus();
    }

    function syncProviderCombobox() {
      if (!providerSelect || !providerTrigger || !providerOptions) return;
      var selected = comboboxOption(providerSelect.value);
      var label = providerTrigger.querySelector('[data-combobox-label]');
      if (label) label.textContent = selected ? selected.textContent.trim() : 'Chọn nhà cung cấp';
      providerOptions.querySelectorAll('[role="option"]').forEach(function (option) {
        option.setAttribute('aria-selected', String(option === selected));
      });
    }

    function chooseProvider(option) {
      if (!providerSelect || !option) return;
      providerSelect.value = option.getAttribute('data-value') || '';
      syncProviderCombobox();
      providerSelect.dispatchEvent(new Event('change', { bubbles: true }));
      closeProviderCombobox(true);
    }

    if (providerSelect && providerTrigger && providerOptions) {
      syncProviderCombobox();
      providerTrigger.addEventListener('click', function () {
        if (providerOptions.hidden) openProviderCombobox();
        else closeProviderCombobox(false);
      });
      providerTrigger.addEventListener('keydown', function (event) {
        if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
          event.preventDefault();
          openProviderCombobox();
        }
      });
      providerOptions.addEventListener('click', function (event) {
        chooseProvider(event.target.closest('[role="option"]'));
      });
      providerOptions.addEventListener('keydown', function (event) {
        var options = Array.from(providerOptions.querySelectorAll('[role="option"]'));
        var current = options.indexOf(document.activeElement);
        if (event.key === 'Escape') {
          event.preventDefault();
          closeProviderCombobox(true);
        } else if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          chooseProvider(document.activeElement.closest('[role="option"]'));
        } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
          event.preventDefault();
          var next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
            : event.key === 'ArrowDown' ? Math.min(options.length - 1, current + 1)
              : Math.max(0, current - 1);
          options[next]?.focus();
        } else if (event.key === 'Tab') {
          closeProviderCombobox(false);
        }
      });
      document.addEventListener('pointerdown', function (event) {
        if (providerCombobox && !providerCombobox.contains(event.target)) closeProviderCombobox(false);
      });
    }

    function suggestionProvider(baseUrl) {
      try {
        var parsed = new URL(baseUrl);
        if (parsed.protocol !== 'https:') return 'custom';
        var path = parsed.pathname.replace(/\/+$/, '');
        if (parsed.hostname === 'api.openai.com' && path === '/v1') return 'openai';
        if (parsed.hostname === 'generativelanguage.googleapis.com' && path === '/v1beta/openai') return 'gemini';
        if ((parsed.hostname === 'aiplatform.googleapis.com'
          || /^[a-z0-9-]+-aiplatform\.googleapis\.com$/.test(parsed.hostname))
          && /^\/v1(?:beta1)?\/projects\/[^/]+\/locations\/[^/]+\/endpoints\/openapi$/.test(path)) {
          return 'gemini-enterprise';
        }
        if (parsed.hostname === 'api.deepseek.com' && path === '/v1') return 'deepseek';
        var alibabaHost = parsed.hostname === 'dashscope.aliyuncs.com'
          || parsed.hostname === 'dashscope-intl.aliyuncs.com'
          || parsed.hostname === 'dashscope-us.aliyuncs.com'
        if (alibabaHost && path === '/compatible-mode/v1') return 'alibaba';
      } catch (ignored) {
        return 'custom';
      }
      return 'custom';
    }

    function normalizeModelSearch(value) {
      return String(value || '').toLocaleLowerCase('vi')
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    }

    function currentModelGroup() {
      if (!suggestions) return null;
      return suggestions.querySelector('[data-suggestion-provider]:not([hidden])');
    }

    function visibleModelOptions(includeDisabled) {
      var group = currentModelGroup();
      if (!group) return [];
      return Array.from(group.querySelectorAll('[data-model]')).filter(function (button) {
        return !button.hidden && (includeDisabled || !button.disabled);
      });
    }

    function filterModelSuggestions() {
      if (!modelInput || !suggestions) return;
      var group = currentModelGroup();
      if (!group) return;
      var query = normalizeModelSearch(modelSearchQuery);
      group.querySelectorAll('[data-model]').forEach(function (button) {
        var searchText = [button.getAttribute('data-model'), button.getAttribute('data-keywords'), button.textContent].join(' ');
        button.hidden = Boolean(query) && !normalizeModelSearch(searchText).includes(query);
        button.setAttribute('aria-selected', String(button.getAttribute('data-model') === modelInput.value));
      });
      var matching = visibleModelOptions(true);
      var count = suggestions.querySelector('[data-model-count]');
      if (count) count.textContent = matching.length + ' model';
      var empty = suggestions.querySelector('[data-model-empty]');
      if (empty) empty.hidden = matching.length > 0 || !group.querySelector('[data-model]');
    }

    function syncDirectAudioVerification() {
      if (!directAudioVerification || !modelInput) return;
      var group = currentModelGroup();
      var verified = group && Array.from(group.querySelectorAll(
        '[data-model][data-verification="verified"]')).some(function (button) {
          return button.getAttribute('data-model') === modelInput.value.trim();
        });
      directAudioVerification.classList.toggle('is-unverified', !verified);
      directAudioVerification.textContent = verified
        ? 'Gợi ý provider/model đã xác minh kỹ thuật. Vẫn cần đủ policy evidence và readiness trước khi bật.'
        : 'Model tùy chỉnh · Cần kiểm tra. Có thể lưu nháp nhưng không thể bật hoặc gửi audio.';
    }

    function openModelSuggestions() {
      if (!modelInput || !suggestions || !providerSelect?.value || modelInput.disabled) return;
      suggestions.hidden = false;
      modelInput.setAttribute('aria-expanded', 'true');
      filterModelSuggestions();
    }

    function closeModelSuggestions() {
      if (!modelInput || !suggestions) return;
      suggestions.hidden = true;
      modelInput.setAttribute('aria-expanded', 'false');
    }

    function refreshModelSuggestions() {
      if (!providerSelect || !modelInput || !suggestions) return;
      var selected = providerSelect.options[providerSelect.selectedIndex];
      var baseUrl = selected ? selected.getAttribute('data-base-url') : '';
      if (!baseUrl) {
        modelSearchQuery = '';
        closeModelSuggestions();
        modelInput.placeholder = 'Chọn nhà cung cấp rồi tìm model';
        return;
      }
      var provider = suggestionProvider(baseUrl);
      suggestions.querySelectorAll('[data-suggestion-provider]').forEach(function (group) {
        group.hidden = group.getAttribute('data-suggestion-provider') !== provider;
      });
      var first = suggestions.querySelector('[data-suggestion-provider="' + provider + '"] [data-model]');
      modelInput.placeholder = first
        ? 'Tìm model, ví dụ ' + first.getAttribute('data-model')
        : 'Nhập model do nhà cung cấp cấp';
      modelSearchQuery = '';
      closeModelSuggestions();
      filterModelSuggestions();
      syncDirectAudioVerification();
    }

    if (providerSelect && modelInput && suggestions) {
      providerSelect.addEventListener('change', refreshModelSuggestions);
      suggestions.addEventListener('click', function (event) {
        var button = event.target.closest('[data-model]');
        if (!button || button.hidden || button.disabled) return;
        modelInput.value = button.getAttribute('data-model');
        modelSearchQuery = '';
        modelInput.dispatchEvent(new Event('change', { bubbles: true }));
        syncDirectAudioVerification();
        modelInput.focus();
        closeModelSuggestions();
        announce('Đã điền model ' + modelInput.value);
      });
      modelInput.addEventListener('focus', function () {
        modelSearchQuery = '';
        openModelSuggestions();
      });
      modelInput.addEventListener('click', function () {
        if (!suggestions.hidden) return;
        modelSearchQuery = '';
        openModelSuggestions();
      });
      modelInput.addEventListener('input', function () {
        modelSearchQuery = modelInput.value;
        openModelSuggestions();
        filterModelSuggestions();
        syncDirectAudioVerification();
      });
      modelInput.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
          event.preventDefault();
          closeModelSuggestions();
          return;
        }
        if (event.key === 'ArrowDown') {
          event.preventDefault();
          openModelSuggestions();
          visibleModelOptions(false)[0]?.focus();
        }
      });
      suggestions.addEventListener('keydown', function (event) {
        var options = visibleModelOptions(false);
        var current = options.indexOf(document.activeElement);
        if (event.key === 'Escape') {
          event.preventDefault();
          closeModelSuggestions();
          modelInput.focus();
        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault();
          var next = event.key === 'ArrowDown'
            ? Math.min(options.length - 1, current + 1)
            : Math.max(0, current - 1);
          options[next]?.focus();
        } else if (event.key === 'Tab') {
          closeModelSuggestions();
        }
      });
      document.addEventListener('pointerdown', function (event) {
        var picker = event.target.closest('.settings-model-picker');
        if (!picker) closeModelSuggestions();
      });
      refreshModelSuggestions();
    }

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
