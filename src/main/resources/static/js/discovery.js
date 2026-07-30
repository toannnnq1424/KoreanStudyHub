(function () {
  'use strict';

  const koreaNetBody = document.querySelector(
    '.source-preview--korea-net .source-preview-body'
  );
  if (koreaNetBody) {
    koreaNetBody.querySelectorAll('p:not(.figcaption)').forEach(function (paragraph) {
      const html = paragraph.innerHTML.trim();
      if (!/(?:<br\s*\/?>\s*){2,}/i.test(html)) {
        return;
      }

      const fragment = document.createDocumentFragment();
      html
        .split(/(?:\s*<br\s*\/?>\s*){2,}/i)
        .map(function (part) {
          return part
            .replace(/^(?:\s*<br\s*\/?>)+/i, '')
            .replace(/(?:<br\s*\/?>\s*)+$/i, '')
            .trim();
        })
        .filter(Boolean)
        .forEach(function (part) {
          const block = document.createElement('p');
          block.innerHTML = part;
          fragment.appendChild(block);
        });

      if (fragment.childNodes.length > 0) {
        paragraph.replaceWith(fragment);
      }
    });
  }

  const hangulPattern = /[\u1100-\u11ff\u3130-\u318f\uac00-\ud7af]/;
  const secondaryCjkPattern =
    /([\u2e80-\u2fdf\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]+)/g;
  document.querySelectorAll(
    'h1, h2, h3, [lang="ko"], .article-deck, .lead-copy > p, ' +
    '.featured-copy > p, .latest-row > div > p, .opportunity-panel p, ' +
    '.source-ribbon-summary > strong, .source-preview-body p, .source-preview-body div'
  ).forEach(function (element) {
    if (hangulPattern.test(element.textContent || '')) {
      element.classList.add('is-korean');
    }
  });

  function softenSecondaryCjk(element) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    const textNodes = [];
    while (walker.nextNode()) {
      if (
        walker.currentNode.nodeValue &&
        secondaryCjkPattern.test(walker.currentNode.nodeValue)
      ) {
        textNodes.push(walker.currentNode);
      }
      secondaryCjkPattern.lastIndex = 0;
    }
    textNodes.forEach(function (node) {
      const parts = node.nodeValue.split(secondaryCjkPattern);
      const fragment = document.createDocumentFragment();
      parts.forEach(function (part) {
        if (!part) return;
        secondaryCjkPattern.lastIndex = 0;
        if (secondaryCjkPattern.test(part)) {
          const span = document.createElement('span');
          span.className = 'is-cjk-secondary';
          span.textContent = part;
          fragment.appendChild(span);
        } else {
          fragment.appendChild(document.createTextNode(part));
        }
      });
      node.replaceWith(fragment);
    });
  }

  document.querySelectorAll('h1.is-korean, h2.is-korean, h3.is-korean').forEach(
    softenSecondaryCjk
  );

  document.querySelectorAll(
    '.article-title, .lead-copy h2, .featured-copy h3, .latest-row h3, .related-grid h3'
  ).forEach(function (heading) {
    const length = Array.from((heading.textContent || '').trim()).length;
    if (length >= 54) {
      heading.classList.add('is-long-title');
    }
    if (length >= 95) {
      heading.classList.add('is-extra-long-title');
    }
  });

  document.querySelectorAll('[data-story-image]').forEach(function (image) {
    const showFallback = function () {
      const art = image.closest('.story-art');
      if (art) {
        art.classList.add('is-image-missing');
      }
    };
    image.addEventListener('error', showFallback);
    if (image.complete && image.naturalWidth === 0) {
      showFallback();
    }
  });

  const copyButton = document.querySelector('[data-copy-current-url]');
  if (copyButton) {
    copyButton.addEventListener('click', async function () {
      const original = copyButton.textContent.trim();
      try {
        await navigator.clipboard.writeText(window.location.href);
        copyButton.lastChild.textContent = ' Đã sao chép';
      } catch (error) {
        window.prompt('Sao chép đường dẫn này:', window.location.href);
      }
      window.setTimeout(function () {
        copyButton.lastChild.textContent = ' ' + original;
      }, 1800);
    });
  }

  const refreshForm = document.querySelector('[data-news-refresh-form]');
  const refreshButton = document.querySelector('[data-news-refresh-button]');
  if (refreshForm && refreshButton) {
    refreshForm.addEventListener('submit', function () {
      refreshButton.disabled = true;
      refreshButton.setAttribute('aria-busy', 'true');
      const label = refreshButton.querySelector('[data-news-refresh-label]');
      if (label) {
        label.textContent = 'Đang cào nguồn…';
      }
    });
  }

  const resetForm = document.querySelector('[data-news-reset-form]');
  if (resetForm) {
    resetForm.addEventListener('submit', function (event) {
      const accepted = window.confirm(
        'Xóa tối đa 1 bài local ở mỗi nguồn để kiểm tra crawler nhập lại?'
      );
      if (!accepted) {
        event.preventDefault();
      }
    });
  }

  const bulkForm = document.querySelector('[data-news-bulk-form]');
  if (bulkForm) {
    const bulkTable = bulkForm.querySelector('[data-news-bulk-table]');
    const selectAll = bulkTable ? bulkTable.querySelector('[data-news-select-all]') : null;
    const toolbar = bulkForm.querySelector('.news-bulk-toolbar');
    const count = bulkForm.querySelector('[data-news-bulk-count]');
    const hint = bulkForm.querySelector('[data-news-bulk-hint]');
    const actions = Array.prototype.slice.call(
      bulkForm.querySelectorAll('[data-news-bulk-action]')
    );

    function rowChecks() {
      return bulkTable
        ? Array.prototype.slice.call(bulkTable.querySelectorAll('.news-row-check'))
        : [];
    }

    function refreshBulkBar() {
      const checks = rowChecks();
      const checked = checks.filter(function (checkbox) {
        return checkbox.checked;
      }).length;
      if (count) {
        count.textContent = String(checked);
      }
      if (hint) {
        hint.hidden = checked > 0;
      }
      actions.forEach(function (action) {
        action.disabled = checked === 0;
      });
      if (selectAll) {
        selectAll.checked = checked > 0 && checked === checks.length;
        selectAll.indeterminate = checked > 0 && checked < checks.length;
      }
    }

    if (selectAll) {
      selectAll.addEventListener('change', function () {
        rowChecks().forEach(function (checkbox) {
          checkbox.checked = selectAll.checked;
        });
        refreshBulkBar();
      });
    }

    if (bulkTable) {
      bulkTable.addEventListener('change', function (event) {
        if (event.target && event.target.classList.contains('news-row-check')) {
          refreshBulkBar();
        }
      });
    }

    refreshBulkBar();
  }

  const vocabDrawer = document.querySelector('[data-vocab-drawer]');
  if (!vocabDrawer) {
    return;
  }

  const articleId = vocabDrawer.dataset.articleId;
  const selectionAction = document.querySelector('[data-vocab-selection-action]');
  const wordDisplay = vocabDrawer.querySelector('[data-vocab-word]');
  const pronunciationPreview = vocabDrawer.querySelector(
    '[data-vocab-pronunciation-preview]'
  );
  const status = vocabDrawer.querySelector('[data-vocab-status]');
  const form = vocabDrawer.querySelector('[data-vocab-form]');
  const wordInput = vocabDrawer.querySelector('[data-vocab-word-input]');
  const meaningInput = vocabDrawer.querySelector('[data-vocab-meaning]');
  const pronunciationInput = vocabDrawer.querySelector('[data-vocab-pronunciation]');
  const partOfSpeechInput = vocabDrawer.querySelector('[data-vocab-part-of-speech]');
  const dictionaryUrlInput = vocabDrawer.querySelector('[data-vocab-dictionary-url]');
  const dictionaryLink = vocabDrawer.querySelector('[data-vocab-dictionary-link]');
  const saveButton = vocabDrawer.querySelector('[data-vocab-save]');
  const deckLink = vocabDrawer.querySelector('[data-vocab-deck-link]');
  let selectedKorean = '';

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header || !token.content || !header.content) {
      return {};
    }
    return { [header.content]: token.content };
  }

  function normalizeSelectedWord(value) {
    return (value || '')
      .replace(/^[\s.,!?;:'"“”‘’()[\]{}<>·…/\\|]+/, '')
      .replace(/[\s.,!?;:'"“”‘’()[\]{}<>·…/\\|]+$/, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function setStatus(message, kind) {
    status.textContent = message;
    status.classList.toggle('is-success', kind === 'success');
    status.classList.toggle('is-error', kind === 'error');
  }

  function syncSaveState() {
    saveButton.disabled = !wordInput.value.trim() || !meaningInput.value.trim();
  }

  function setDictionaryLink(url) {
    dictionaryUrlInput.value = url || '';
    dictionaryLink.hidden = !url;
    if (url) {
      dictionaryLink.href = url;
    } else {
      dictionaryLink.removeAttribute('href');
    }
  }

  function fillWord(data) {
    const word = normalizeSelectedWord(data.word);
    selectedKorean = word;
    wordInput.value = word;
    wordDisplay.textContent = word || '단어';
    meaningInput.value = data.meaning || '';
    pronunciationInput.value = data.pronunciation || '';
    partOfSpeechInput.value = data.partOfSpeech || '';
    pronunciationPreview.textContent = data.pronunciation
      ? '[' + data.pronunciation + ']'
      : 'Nghĩa Việt từ Korean Basic Dictionary';
    setDictionaryLink(data.dictionaryUrl || '');
    syncSaveState();
  }

  function openDrawer(data) {
    vocabDrawer.hidden = false;
    if (data) {
      fillWord(data);
    }
  }

  function readEnvelope(response) {
    return response.json().catch(function () {
      return { ok: false, message: 'Phản hồi từ máy chủ không hợp lệ.' };
    }).then(function (payload) {
      if (!response.ok || !payload.ok) {
        throw new Error(payload.message || 'Không thể hoàn tất thao tác.');
      }
      return payload.data;
    });
  }

  async function lookupWord(word) {
    openDrawer({
      word: word,
      meaning: '',
      pronunciation: '',
      partOfSpeech: '',
      dictionaryUrl: ''
    });
    setStatus('Đang đối chiếu Korean Basic Dictionary…');
    saveButton.disabled = true;
    try {
      const response = await fetch(
        '/api/discovery/articles/' + articleId + '/dictionary?word=' +
          encodeURIComponent(word),
        { credentials: 'same-origin' }
      );
      const data = await readEnvelope(response);
      if (data.found) {
        fillWord({
          word: data.word,
          meaning: data.meaningVi,
          pronunciation: data.pronunciation,
          partOfSpeech: data.partOfSpeech,
          dictionaryUrl: data.dictionaryUrl
        });
        setStatus('Đã lấy nghĩa Việt từ từ điển quốc gia Hàn Quốc.', 'success');
      } else {
        fillWord({
          word: data.word || word,
          meaning: '',
          pronunciation: '',
          partOfSpeech: '',
          dictionaryUrl: ''
        });
        setStatus(
          data.dictionaryConfigured
            ? 'Không có kết quả chính xác. Bạn có thể nhập nghĩa Việt thủ công.'
            : 'Chưa cấu hình API key. Nhập nghĩa Việt thủ công để vẫn lưu được thẻ.',
          'error'
        );
        meaningInput.focus();
      }
    } catch (error) {
      setStatus(error.message, 'error');
      meaningInput.focus();
    }
  }

  document.querySelectorAll('[data-korean-reading-surface]').forEach(function (surface) {
    surface.addEventListener('mouseup', function () {
      window.setTimeout(function () {
        const selection = window.getSelection();
        const word = normalizeSelectedWord(selection ? selection.toString() : '');
        if (!word || word.length > 120 || !hangulPattern.test(word)) {
          selectionAction.hidden = true;
          return;
        }
        const range = selection.rangeCount ? selection.getRangeAt(0) : null;
        if (!range || !surface.contains(range.commonAncestorContainer)) {
          selectionAction.hidden = true;
          return;
        }
        const rect = range.getBoundingClientRect();
        selectedKorean = word;
        selectionAction.style.left =
          Math.max(12, Math.min(window.innerWidth - 145, rect.left)) + 'px';
        selectionAction.style.top =
          Math.max(76, rect.top - 48) + 'px';
        selectionAction.hidden = false;
      }, 0);
    });
  });

  selectionAction.addEventListener('click', function () {
    selectionAction.hidden = true;
    if (selectedKorean) {
      lookupWord(selectedKorean);
    }
  });

  document.querySelectorAll('[data-vocab-open]').forEach(function (button) {
    button.addEventListener('click', function () {
      openDrawer();
    });
  });

  document.querySelectorAll('[data-vocab-close]').forEach(function (button) {
    button.addEventListener('click', function () {
      vocabDrawer.hidden = true;
    });
  });

  document.querySelectorAll('[data-vocab-prefill]').forEach(function (button) {
    button.addEventListener('click', function () {
      fillWord({
        word: button.dataset.word,
        meaning: button.dataset.meaning,
        pronunciation: button.dataset.pronunciation,
        partOfSpeech: button.dataset.partOfSpeech,
        dictionaryUrl: button.dataset.dictionaryUrl
      });
      openDrawer();
      setStatus('Từ đã được đối chiếu. Bấm lưu để thêm vào kho cá nhân.', 'success');
    });
  });

  meaningInput.addEventListener('input', syncSaveState);
  wordInput.addEventListener('input', syncSaveState);

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    syncSaveState();
    if (saveButton.disabled) return;
    saveButton.disabled = true;
    saveButton.textContent = 'Đang lưu…';
    setStatus('Đang tạo flashcard cá nhân…');
    try {
      const response = await fetch(
        '/api/discovery/articles/' + articleId + '/flashcards',
        {
          method: 'POST',
          credentials: 'same-origin',
          headers: Object.assign(
            { 'Content-Type': 'application/json' },
            csrfHeaders()
          ),
          body: JSON.stringify({
            word: wordInput.value,
            meaningVi: meaningInput.value,
            pronunciation: pronunciationInput.value,
            partOfSpeech: partOfSpeechInput.value,
            dictionaryUrl: dictionaryUrlInput.value
          })
        }
      );
      const data = await readEnvelope(response);
      deckLink.href = data.deckUrl;
      setStatus(
        data.alreadySaved
          ? 'Từ này đã có trong bộ “' + data.deckTitle + '”.'
          : 'Đã lưu vào bộ “' + data.deckTitle + '”.',
        'success'
      );
    } catch (error) {
      setStatus(error.message, 'error');
    } finally {
      saveButton.textContent = 'Lưu vào flashcard';
      syncSaveState();
    }
  });
})();
