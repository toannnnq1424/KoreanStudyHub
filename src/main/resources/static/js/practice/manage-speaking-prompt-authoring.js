(function () {
  'use strict';

  const STATUS_COPY = Object.freeze({
    idle: 'Chưa xử lý',
    queued: 'Đang chờ',
    processing: 'Đang xử lý',
    retry_wait: 'Đang chờ thử lại',
    ready: 'Sẵn sàng',
    needs_review: 'Cần giảng viên kiểm tra',
    stale: 'Đã cũ — cần tạo lại',
    failed_retryable: 'Tạm thời chưa xử lý được',
    failed_final: 'Không thể xử lý tệp/nội dung này',
    superseded: '',
    cancelled: 'Đã huỷ',
    succeeded: 'Sẵn sàng',
    failed: 'Tạm thời chưa xử lý được'
  });
  const AUTHORING_AUDIO_TYPES = Object.freeze({
    '.mp3': Object.freeze(['audio/mpeg']),
    '.wav': Object.freeze(['audio/wav', 'audio/x-wav']),
    '.m4a': Object.freeze(['audio/mp4', 'audio/x-m4a']),
    '.ogg': Object.freeze(['audio/ogg']),
    '.webm': Object.freeze(['audio/webm'])
  });
  const ASYNC_ERROR_COPY = Object.freeze({
    INVALID_INPUT: 'Tệp hoặc nội dung đề bài không hợp lệ. Hãy kiểm tra lại định dạng và nội dung.',
    CONFIGURATION: 'Tính năng AI chưa được cấu hình sẵn sàng. Hãy liên hệ quản trị viên.',
    RATE_LIMIT: 'Dịch vụ AI đang giới hạn yêu cầu. Hãy chờ rồi thử lại.',
    TIMEOUT: 'Dịch vụ AI xử lý quá lâu. Hãy thử lại sau ít phút.',
    TRANSPORT: 'Kết nối tới dịch vụ AI bị gián đoạn. Hãy kiểm tra kết nối rồi thử lại.',
    PROVIDER_REJECTED: 'Dịch vụ AI từ chối tệp hoặc nội dung này. Hãy kiểm tra định dạng hoặc thay nội dung.',
    EMPTY_OUTPUT: 'Dịch vụ AI không trả về nội dung. Hãy thử lại hoặc thay tệp/nội dung.',
    MALFORMED_OUTPUT: 'Kết quả AI hoặc audio được tạo không hợp lệ. Hãy tạo lại.',
    STALE_COMPLETION: 'Kết quả AI thuộc phiên bản nguồn cũ và đã bị bỏ qua. Hãy tải lại trạng thái hiện tại.'
  });
  const API_ERROR_COPY = Object.freeze({
    AUTH_REQUIRED: 'Phiên đăng nhập đã hết hạn. Hãy tải lại trang và đăng nhập lại.',
    INVALID_RESPONSE: 'Máy chủ trả về phản hồi không hợp lệ. Hãy tải lại trang rồi thử lại.',
    SOURCE_CONFLICT: 'Bản nháp đã thay đổi ở nơi khác. Hãy tải lại trang trước khi tiếp tục.',
    FORBIDDEN: 'Bạn không có quyền chỉnh sửa đề bài Nói này. Hãy kiểm tra tài khoản đang dùng.',
    NOT_FOUND: 'Không tìm thấy nguồn đề bài Nói hiện tại. Hãy tải lại trang.',
    INVALID_INPUT: 'Tệp hoặc nội dung đề bài Nói không hợp lệ. Hãy kiểm tra rồi thử lại.',
    RATE_LIMIT: 'Dịch vụ AI đang giới hạn yêu cầu. Hãy chờ rồi thử lại.',
    AI_UNAVAILABLE: 'Tính năng AI chưa sẵn sàng. Hãy liên hệ quản trị viên.',
    AI_TEMPORARILY_UNAVAILABLE: 'Dịch vụ AI tạm thời không kết nối được. Hãy thử lại sau ít phút.',
    UNPROCESSABLE_AUDIO: 'Tệp hoặc kết quả audio không thể xử lý. Hãy kiểm tra định dạng hoặc chọn tệp khác.',
    TEMPORARILY_UNAVAILABLE: 'Tính năng biên soạn đề bài Nói tạm thời chưa sẵn sàng. Hãy thử lại sau.',
    RETRY_LIMIT: 'Bạn đang thao tác quá nhanh. Hãy chờ hết thời gian giới hạn rồi thử lại.',
    NOT_RETRYABLE: 'Nguồn hiện tại không thể thử lại. Hãy kiểm tra bản chép lời hoặc thay file audio.'
  });
  const POLLABLE = new Set(['queued', 'processing', 'retry_wait']);

  let activeQuestion = null;
  let activeClientId = null;
  let state = null;
  let activationToken = 0;
  let pollTimer = null;
  let saveTimer = null;
  let saveChain = Promise.resolve();
  let bound = false;
  let localMode = 'audio_upload';
  let requestSequence = 0;
  let lastAppliedSequence = 0;
  let editGeneration = 0;
  let savedEditGeneration = 0;
  let activeUploadId = 0;
  let draftConflict = false;
  let transcriptDirty = false;
  let transcriptGeneration = 0;
  let sourceDestructiveMutationCount = 0;
  const acceptedByClient = new Map();
  const mutationBaseByClient = new Map();
  const pendingMutationSequences = new Map();
  const pendingOperations = new Set();

  function element(id) {
    return document.getElementById(id);
  }

  function endpointFor(clientId, suffix) {
    return `/practice/manage/drafts/${encodeURIComponent(DRAFT_ID)}`
      + `/questions/${encodeURIComponent(clientId)}/speaking-prompt`
      + (suffix || '');
  }

  function endpoint(suffix) {
    return endpointFor(activeClientId, suffix);
  }

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    return token && header ? { [header.content]: token.content } : {};
  }

  async function readResponse(response) {
    let payload = null;
    const contentType = (response.headers.get('content-type') || '')
      .toLowerCase();
    if (contentType.includes('application/json')) {
      try {
        payload = await response.json();
      } catch (ignored) {
        payload = null;
      }
    }
    const loginRedirect = response.redirected
      && response.url
      && new URL(response.url, window.location.origin).pathname === '/login';
    if (!response.ok || loginRedirect || !payload) {
      throw requestError(
        loginRedirect ? 401 : response.status,
        payload,
        loginRedirect ? 'AUTH_REQUIRED' : (!payload ? 'INVALID_RESPONSE' : null));
    }
    return payload;
  }

  function requestError(status, payload, fallbackCode) {
    const error = new Error('SPEAKING_PROMPT_AUTHORING_REQUEST_FAILED');
    error.status = status;
    error.code = payload && typeof payload.code === 'string'
      ? payload.code
      : fallbackCode || null;
    error.payload = payload;
    return error;
  }

  function safeRequestErrorMessage(error) {
    const code = error && typeof error.code === 'string'
      ? error.code
      : error && error.payload && typeof error.payload.code === 'string'
        ? error.payload.code
        : null;
    if (code && API_ERROR_COPY[code]) return API_ERROR_COPY[code];
    const status = Number(error && error.status);
    if (status === 409) return API_ERROR_COPY.SOURCE_CONFLICT;
    if (status === 401) return API_ERROR_COPY.AUTH_REQUIRED;
    if (status === 403) return API_ERROR_COPY.FORBIDDEN;
    if (status === 404) return API_ERROR_COPY.NOT_FOUND;
    if (status === 429) return API_ERROR_COPY.RATE_LIMIT;
    if (status === 422) return API_ERROR_COPY.INVALID_INPUT;
    if (status === 503) return API_ERROR_COPY.TEMPORARILY_UNAVAILABLE;
    return 'Không thể cập nhật đề bài Nói. Hãy thử lại; nếu lỗi tiếp diễn, hãy tải lại trang.';
  }

  function asyncOperationErrorMessage(operation) {
    if (!operation || !operation.publicErrorCategory) return null;
    const category = String(operation.publicErrorCategory).toUpperCase();
    const base = ASYNC_ERROR_COPY[category];
    if (!base) {
      return 'Tác vụ AI không hoàn tất. Hãy tải lại trạng thái và thử lại.';
    }
    if (operation.retryable === true) {
      return `${base} Bạn có thể dùng nút Thử lại khi tác vụ cho phép.`;
    }
    return base;
  }

  async function jsonRequest(url, method, body) {
    const headers = Object.assign(
      { 'Content-Type': 'application/json' },
      csrfHeaders());
    const response = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    return readResponse(response);
  }

  function currentDraftVersion() {
    const version = typeof DRAFT_VERSION === 'undefined'
      ? 0
      : Number(DRAFT_VERSION);
    return Number.isInteger(version) && version >= 0 ? version : 0;
  }

  function requestContext(
      clientId,
      mutation,
      maxDraftAdvance,
      initializeMutationBase) {
    return {
      clientId,
      sequence: ++requestSequence,
      localGeneration: clientId === activeClientId ? editGeneration : null,
      localTranscriptGeneration: clientId === activeClientId
        ? transcriptGeneration
        : null,
      mutation: mutation === true,
      initializeMutationBase: initializeMutationBase === true,
      expectedDraftVersion: currentDraftVersion(),
      maxDraftAdvance: Number.isInteger(maxDraftAdvance)
        ? maxDraftAdvance
        : 0
    };
  }

  function trackOperation(promise) {
    pendingOperations.add(promise);
    promise.then(
      () => pendingOperations.delete(promise),
      () => pendingOperations.delete(promise));
    return promise;
  }

  function beginMutation(request) {
    const pending = pendingMutationSequences.get(request.clientId) || new Set();
    pending.add(request.sequence);
    pendingMutationSequences.set(request.clientId, pending);
  }

  function endMutation(request) {
    const pending = pendingMutationSequences.get(request.clientId);
    if (!pending) return;
    pending.delete(request.sequence);
    if (pending.size === 0) {
      pendingMutationSequences.delete(request.clientId);
    }
  }

  function hasPendingMutation(clientId) {
    return (pendingMutationSequences.get(clientId)?.size || 0) > 0;
  }

  function beginSourceDestructiveMutation() {
    sourceDestructiveMutationCount += 1;
    renderSourceMutationLock();
  }

  function endSourceDestructiveMutation() {
    sourceDestructiveMutationCount = Math.max(
      0,
      sourceDestructiveMutationCount - 1);
    renderSourceMutationLock();
  }

  function sourceDestructiveMutationPending() {
    return sourceDestructiveMutationCount > 0;
  }

  function renderSourceMutationLock() {
    const locked = sourceDestructiveMutationPending();
    [
      'speaking-transcript-context',
      'speaking-transcript-confirmed',
      'speaking-save-transcript',
      'speaking-prompt-audio-file-input',
      'speaking-prompt-audio-dropzone',
      'speaking-replace-audio',
      'speaking-remove-audio',
      'speaking-adopt-excel-audio'
    ].forEach(id => {
      const control = element(id);
      if (control) control.disabled = locked;
    });
    document.querySelectorAll('[data-speaking-mode]').forEach(button => {
      button.disabled = locked;
    });
    const context = element('speaking-ai-context');
    if (context) context.setAttribute('aria-busy', String(locked));
  }

  function revisionOf(candidate) {
    const revision = Number(candidate && candidate.sourceRevision);
    return Number.isInteger(revision) && revision >= 0 ? revision : 0;
  }

  function draftVersionOf(candidate) {
    const version = Number(candidate && candidate.draftVersion);
    return Number.isInteger(version) && version >= 0 ? version : 0;
  }

  function acceptedRevision(clientId) {
    return revisionOf(mutationBaseByClient.get(clientId));
  }

  function showMessage(message, type) {
    const target = element('speaking-prompt-audio-upload-status');
    if (target) {
      target.hidden = false;
      target.textContent = message;
      target.dataset.kind = type || 'info';
    }
    if (type === 'error' && typeof showEditorToast === 'function') {
      showEditorToast(message, 'error');
    }
  }

  function clearMessage() {
    const target = element('speaking-prompt-audio-upload-status');
    if (!target) return;
    target.hidden = true;
    target.textContent = '';
    delete target.dataset.kind;
  }

  function bindOnce() {
    if (bound) return;
    bound = true;

    document.querySelectorAll('[data-speaking-mode]').forEach(button => {
      button.addEventListener('click', () => selectMode(button.dataset.speakingMode));
    });
    const picker = element('speaking-prompt-audio-file-input');
    const dropzone = element('speaking-prompt-audio-dropzone');
    if (dropzone && picker) {
      dropzone.addEventListener('click', () => picker.click());
      ['dragenter', 'dragover'].forEach(name => dropzone.addEventListener(name, event => {
        event.preventDefault();
        dropzone.classList.add('is-dragging');
      }));
      ['dragleave', 'drop'].forEach(name => dropzone.addEventListener(name, event => {
        event.preventDefault();
        dropzone.classList.remove('is-dragging');
      }));
      dropzone.addEventListener('drop', event => {
        const file = event.dataTransfer && event.dataTransfer.files[0];
        if (file) upload(file);
      });
    }
    element('speaking-replace-audio')?.addEventListener('click', () => picker?.click());
    element('speaking-remove-audio')?.addEventListener('click', removeOriginal);
    element('speaking-adopt-excel-audio')?.addEventListener(
      'click',
      adoptExcelStaging);
    element('speaking-retry-transcription')?.addEventListener('click', retryTranscription);
    element('speaking-save-transcript')?.addEventListener('click', saveTranscript);
    element('speaking-generate-tts')?.addEventListener('click', generateTts);
    element('speaking-transcript-context')?.addEventListener(
      'input',
      markTranscriptDirty);
    element('speaking-transcript-confirmed')?.addEventListener(
      'change',
      markTranscriptDirty);

    const manual = element('speaking-manual-text');
    manual?.addEventListener('input', () => {
      markDirty();
      markGeneratedStaleLocally();
      scheduleSave();
    });
    ['speaking-tts-enabled', 'speaking-tts-voice',
      'speaking-tts-speed', 'speaking-tts-format'].forEach(id => {
      element(id)?.addEventListener('change', () => {
        markDirty();
        renderTtsControls();
        markGeneratedStaleLocally();
        scheduleSave();
      });
    });
  }

  async function activate(question) {
    bindOnce();
    if (activeClientId && transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi chuyển câu hỏi.',
        'error');
      return;
    }
    if (activeClientId) deactivate();
    stopPolling();
    activationToken += 1;
    const token = activationToken;
    activeQuestion = question;
    activeClientId = question && question.clientId;
    state = null;
    editGeneration += 1;
    savedEditGeneration = editGeneration;
    transcriptGeneration += 1;
    transcriptDirty = false;
    lastAppliedSequence = 0;
    if (!activeClientId) {
      deactivate();
      return;
    }
    const standardPrompt = element('standard-question-prompt-field');
    if (standardPrompt) standardPrompt.style.display = 'none';
    seedFromQuestion(question);
    resetRemoteUi();
    clearMessage();
    try {
      const request = requestContext(
        activeClientId,
        false,
        0,
        !mutationBaseByClient.has(activeClientId));
      const loaded = await jsonRequest(endpoint(''), 'GET');
      if (token !== activationToken || activeClientId !== question.clientId) return;
      acceptState(question.clientId, loaded, request);
    } catch (error) {
      if (token === activationToken) {
        if (error && error.status === 409) {
          markDraftConflict();
        } else {
          showMessage(safeRequestErrorMessage(error), 'error');
        }
      }
    }
  }

  function deactivate() {
    if (transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi chuyển nội dung.',
        'error');
      return Promise.resolve(false);
    }
    const pendingSave = hasUnsavedInput()
      ? queueSave(false)
      : saveChain;
    activationToken += 1;
    activeUploadId += 1;
    stopPolling();
    if (saveTimer) window.clearTimeout(saveTimer);
    saveTimer = null;
    activeQuestion = null;
    activeClientId = null;
    state = null;
    editGeneration += 1;
    savedEditGeneration = editGeneration;
    transcriptGeneration += 1;
    transcriptDirty = false;
    lastAppliedSequence = 0;
    resetRemoteUi();
    return pendingSave;
  }

  function seedFromQuestion(question) {
    const options = question.speakingPromptAuthoring || {};
    localMode = options.inputType === 'manual_text'
      ? 'manual_text'
      : 'audio_upload';
    const manual = element('speaking-manual-text');
    if (manual) manual.value = question.prompt || '';
    const toggle = element('speaking-tts-enabled');
    if (toggle) toggle.checked = options.ttsEnabled === true;
    setSelectValue('speaking-tts-voice', options.voiceCode || '');
    setSelectValue('speaking-tts-speed', options.speed || '1');
    setSelectValue('speaking-tts-format', options.outputFormat || 'mp3');
    renderMode();
    renderTtsControls();
  }

  function resetRemoteUi() {
    renderAudio(element('speaking-prompt-audio-preview-container'), null, '', null);
    renderAudio(element('speaking-generated-audio-preview'), null, '', null);
    setChip('speaking-transcript-status-chip', 'idle');
    setChip('speaking-tts-status-chip', 'idle');
    const details = element('speaking-ai-context');
    if (details) details.hidden = true;
    const context = element('speaking-transcript-context');
    if (context) context.value = '';
    const confirmed = element('speaking-transcript-confirmed');
    if (confirmed) confirmed.checked = false;
    const lowConfidence = element('speaking-low-confidence');
    if (lowConfidence) lowConfidence.hidden = true;
    const remove = element('speaking-remove-audio');
    if (remove) remove.hidden = true;
    const replace = element('speaking-replace-audio');
    if (replace) replace.textContent = 'Chọn file';
    const retry = element('speaking-retry-transcription');
    if (retry) retry.hidden = true;
    const excelStaging = element('speaking-excel-staging-callout');
    if (excelStaging) excelStaging.hidden = true;
    const generate = element('speaking-generate-tts');
    if (generate) generate.hidden = true;
    const progress = element('speaking-prompt-upload-progress');
    if (progress) {
      progress.hidden = true;
      progress.removeAttribute('value');
    }
    clearMessage();
  }

  function acceptState(clientId, next, request, mutationGeneration) {
    if (!next || !clientId || !request) return false;
    const known = acceptedByClient.get(clientId);
    const nextRevision = revisionOf(next);
    const nextDraftVersion = draftVersionOf(next);
    const knownRevision = revisionOf(known);
    const knownDraftVersion = draftVersionOf(known);
    const activeResponse = clientId === activeClientId && !!activeQuestion;
    if (!request.mutation && activeResponse
        && (hasUnsavedInput()
          || transcriptDirty
          || request.localGeneration !== editGeneration
          || request.localTranscriptGeneration !== transcriptGeneration)) {
      return false;
    }
    if (!request.mutation && activeResponse
        && request.sequence < lastAppliedSequence) {
      return false;
    }
    if (!request.mutation
        && nextDraftVersion !== currentDraftVersion()) {
      markDraftConflict();
      return false;
    }
    const mutationBase = mutationBaseByClient.get(clientId);
    if (!request.mutation
        && !request.initializeMutationBase
        && mutationBase
        && nextRevision !== revisionOf(mutationBase)) {
      if (hasPendingMutation(clientId)) return false;
      markDraftConflict();
      return false;
    }
    if (request.mutation
        && (nextDraftVersion < request.expectedDraftVersion
          || nextDraftVersion
            > request.expectedDraftVersion + request.maxDraftAdvance)) {
      markDraftConflict();
      return false;
    }
    if (known && (nextRevision < knownRevision
        || (nextRevision === knownRevision
          && nextDraftVersion < knownDraftVersion))) {
      return false;
    }
    const strictlyNewer = !known
      || nextRevision > knownRevision
      || (nextRevision === knownRevision
        && nextDraftVersion > knownDraftVersion);
    if (clientId === activeClientId
        && request.sequence < lastAppliedSequence
        && !strictlyNewer) {
      return false;
    }
    acceptedByClient.set(clientId, next);
    if (request.mutation || request.initializeMutationBase) {
      mutationBaseByClient.set(clientId, next);
    }
    if (request.mutation && Number.isInteger(next.draftVersion)
        && typeof DRAFT_VERSION !== 'undefined') {
      DRAFT_VERSION = Math.max(DRAFT_VERSION, next.draftVersion);
    }
    if (clientId !== activeClientId || !activeQuestion) return true;
    lastAppliedSequence = Math.max(lastAppliedSequence, request.sequence);
    if (mutationGeneration !== undefined) {
      savedEditGeneration = Math.max(
        savedEditGeneration,
        mutationGeneration);
      if (mutationGeneration !== editGeneration) return true;
    } else if (request.localGeneration !== null
        && request.localGeneration !== editGeneration) {
      return true;
    }
    applyState(next);
    return true;
  }

  function markDraftConflict() {
    draftConflict = true;
    stopPolling();
    showMessage(
      'Bản nháp đã được chỉnh sửa ở nơi khác. Hãy tải lại trang trước khi tiếp tục.',
      'error');
  }

  function handleMutationError(error, clientId, showNonConflict) {
    if (clientId !== activeClientId) return;
    if (error && error.status === 409) {
      markDraftConflict();
      return;
    }
    if (showNonConflict) {
      showMessage(safeRequestErrorMessage(error), 'error');
    }
  }

  function applyState(next) {
    if (!next || !activeQuestion) return;
    state = next;
    localMode = next.inputType || localMode;
    const manual = element('speaking-manual-text');
    if (manual && document.activeElement !== manual) {
      manual.value = next.manualText || activeQuestion.prompt || '';
    }
    const toggle = element('speaking-tts-enabled');
    if (toggle) toggle.checked = next.ttsEnabled === true;
    populateApprovedOptions(next);
    renderMode();
    renderOriginal(next);
    renderTranscript(next);
    renderGenerated(next);
    renderTtsControls();
    syncDraft();
    schedulePolling(next);
  }

  function populateApprovedOptions(next) {
    const approved = next.approvedTts || {};
    const selected = next.selectedTts || {};
    populateSelect(
      'speaking-tts-voice',
      approved.voices || [],
      item => item.code,
      item => item.label,
      selected.voiceCode);
    populateSelect(
      'speaking-tts-speed',
      approved.speeds || [],
      item => String(item),
      item => `${item}×`,
      String(selected.speed == null ? 1 : selected.speed));
    populateSelect(
      'speaking-tts-format',
      approved.outputFormats || [],
      item => item,
      item => String(item).toUpperCase(),
      selected.outputFormat);
  }

  function populateSelect(id, items, valueFn, labelFn, selected) {
    const select = element(id);
    if (!select) return;
    const current = selected || select.value;
    select.replaceChildren();
    items.forEach(item => {
      const option = document.createElement('option');
      option.value = valueFn(item);
      option.textContent = labelFn(item);
      select.append(option);
    });
    if (Array.from(select.options).some(option => option.value === current)) {
      select.value = current;
    }
  }

  function setSelectValue(id, value) {
    const select = element(id);
    if (select && value !== undefined && value !== null) {
      select.value = String(value);
    }
  }

  function renderMode() {
    document.querySelectorAll('[data-speaking-mode]').forEach(button => {
      const active = button.dataset.speakingMode === localMode;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-checked', String(active));
    });
    const audio = element('speaking-audio-branch');
    const manual = element('speaking-manual-branch');
    if (audio) audio.hidden = localMode !== 'audio_upload';
    if (manual) manual.hidden = localMode !== 'manual_text';
    renderDeliveryControls();
  }

  function renderOriginal(next) {
    const container = element('speaking-prompt-audio-preview-container');
    const asset = next.originalAudio;
    renderAudio(
      container,
      asset,
      asset ? 'Audio của giảng viên' : '',
      null,
      next.sourceRevision);
    const remove = element('speaking-remove-audio');
    if (remove) remove.hidden = !asset;
    const replace = element('speaking-replace-audio');
    if (replace) replace.textContent = asset ? 'Thay file' : 'Chọn file';
    const excelStaging = element('speaking-excel-staging-callout');
    if (excelStaging) {
      excelStaging.hidden = next.excelStagingAudioAvailable !== true;
    }
    const durableStatus = next.sttOperation
      && next.sttOperation.taskStatus === 'retry_wait'
      ? 'retry_wait'
      : next.transcriptStatus;
    setChip('speaking-transcript-status-chip', durableStatus);
    const operationError = asyncOperationErrorMessage(next.sttOperation);
    if (operationError) showMessage(operationError, 'error');
    const retry = element('speaking-retry-transcription');
    if (retry) retry.hidden = durableStatus !== 'failed_retryable';
  }

  function renderTranscript(next) {
    if (transcriptDirty) return;
    const details = element('speaking-ai-context');
    const hasContext = typeof next.lecturerContext === 'string'
      && next.lecturerContext.length > 0;
    if (details) details.hidden = !hasContext;
    const textarea = element('speaking-transcript-context');
    if (textarea && document.activeElement !== textarea) {
      textarea.value = hasContext ? next.lecturerContext : '';
    }
    const confirmed = element('speaking-transcript-confirmed');
    if (confirmed) confirmed.checked = next.transcriptConfirmed === true;
    const review = element('speaking-low-confidence');
    if (review) review.hidden = next.transcriptStatus !== 'needs_review';
    renderSourceMutationLock();
  }

  function renderGenerated(next) {
    const badge = next.generatedAudio
      ? (next.generatedAudioCurrent ? 'Audio do AI tạo' : 'Bản cũ')
      : null;
    renderAudio(
      element('speaking-generated-audio-preview'),
      next.generatedAudio,
      'Audio do AI tạo',
      badge,
      next.sourceRevision);
    const durableStatus = next.ttsOperation
      && next.ttsOperation.taskStatus === 'retry_wait'
      ? 'retry_wait'
      : next.audioStatus;
    setChip('speaking-tts-status-chip', durableStatus);
    const operationError = asyncOperationErrorMessage(next.ttsOperation);
    if (operationError) showMessage(operationError, 'error');
    const generate = element('speaking-generate-tts');
    if (generate) {
      generate.hidden = !next.ttsEnabled;
      generate.textContent = next.generatedAudio ? 'Tạo lại audio' : 'Tạo audio';
    }
  }

  function previewAudioUrl(contentUrl, sourceRevision) {
    const separator = String(contentUrl).includes('?') ? '&' : '?';
    return `${contentUrl}${separator}sourceRevision=${encodeURIComponent(
      String(sourceRevision == null ? 0 : sourceRevision))}`;
  }

  function renderAudio(
      container,
      asset,
      provenance,
      badge,
      sourceRevision) {
    if (!container) return;
    container.replaceChildren();
    container.hidden = !asset;
    if (!asset) return;
    const heading = document.createElement('div');
    heading.className = 'sp-audio-preview__heading';
    const source = document.createElement('strong');
    source.textContent = provenance;
    heading.append(source);
    if (badge) {
      const marker = document.createElement('span');
      marker.className = badge === 'Bản cũ'
        ? 'sp-state-chip is-stale'
        : 'sp-state-chip is-ready';
      marker.textContent = badge;
      heading.append(marker);
    }
    const audio = document.createElement('audio');
    audio.controls = true;
    audio.preload = 'metadata';
    audio.src = previewAudioUrl(asset.contentUrl, sourceRevision);
    const filename = document.createElement('span');
    filename.className = 'sp-audio-preview__filename';
    filename.textContent = asset.filename || 'audio';
    const duration = document.createElement('span');
    duration.className = 'sp-audio-preview__duration';
    duration.textContent = 'Đang đọc thời lượng…';
    audio.addEventListener('loadedmetadata', () => {
      if (!Number.isFinite(audio.duration) || audio.duration <= 0) {
        duration.remove();
        return;
      }
      duration.textContent = `Thời lượng ${formatDuration(audio.duration)}`;
    }, { once: true });
    audio.addEventListener('error', () => duration.remove(), { once: true });
    container.append(heading, audio, filename, duration);
  }

  function renderLegacyPreview() {
    if (state) renderOriginal(state);
  }

  function setChip(id, status) {
    const chip = element(id);
    if (!chip) return;
    const copy = STATUS_COPY[status] || STATUS_COPY.idle;
    chip.hidden = status === 'superseded';
    chip.textContent = copy;
    chip.className = 'sp-state-chip';
    if (status === 'ready' || status === 'succeeded') chip.classList.add('is-ready');
    if (status === 'stale') chip.classList.add('is-stale');
    if (status === 'needs_review') chip.classList.add('is-review');
    if (status === 'failed_retryable' || status === 'failed_final') chip.classList.add('is-error');
  }

  function renderTtsControls() {
    const enabled = element('speaking-tts-enabled')?.checked === true;
    const controls = element('speaking-tts-controls');
    const generate = element('speaking-generate-tts');
    if (controls) controls.hidden = !enabled;
    if (generate) generate.hidden = !enabled;
    const textOnly = element('speaking-text-only-copy');
    if (textOnly) textOnly.hidden = enabled;
    renderDeliveryControls();
  }

  function renderDeliveryControls() {
    const textOnly = localMode === 'manual_text'
      && element('speaking-tts-enabled')?.checked !== true;
    const playLimitControl = element('speaking-play-limit-control');
    const playLimit = element('q-speak-play-limit');
    if (playLimitControl) playLimitControl.hidden = textOnly;
    if (playLimit) playLimit.disabled = textOnly;
  }

  function selectMode(mode) {
    if (!activeQuestion || !['audio_upload', 'manual_text'].includes(mode)) return;
    if (mode === localMode) return;
    if (draftConflict) {
      markDraftConflict();
      return;
    }
    if (sourceDestructiveMutationPending()) {
      showMessage('Đang cập nhật nguồn audio. Vui lòng chờ.', 'error');
      return;
    }
    if (transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi đổi nguồn đề bài.',
        'error');
      return;
    }
    localMode = mode;
    markDirty();
    renderMode();
    queueSave(true);
  }

  function markDirty() {
    editGeneration += 1;
  }

  function markTranscriptDirty() {
    if (sourceDestructiveMutationPending()) return;
    transcriptGeneration += 1;
    transcriptDirty = true;
  }

  function hasUnsavedInput() {
    return !!activeQuestion && editGeneration > savedEditGeneration;
  }

  function scheduleSave() {
    if (saveTimer) window.clearTimeout(saveTimer);
    saveTimer = window.setTimeout(() => {
      saveTimer = null;
      queueSave(false).catch(() => undefined);
    }, 700);
  }

  function queueSave(showErrors) {
    if (draftConflict) {
      markDraftConflict();
      return Promise.resolve(false);
    }
    const clientId = activeClientId;
    if (!clientId || !activeQuestion) return saveChain;
    if (saveTimer) window.clearTimeout(saveTimer);
    saveTimer = null;
    const snapshot = saveSnapshot();
    const mutationGeneration = editGeneration;
    const operation = saveChain
      .catch(() => undefined)
      .then(async () => {
        const request = requestContext(
          clientId,
          true,
          draftSnapshotMatchesState(snapshot) ? 0 : 1);
        const payload = Object.assign({}, snapshot, {
          expectedSourceRevision: acceptedRevision(clientId),
          expectedDraftVersion: request.expectedDraftVersion
        });
        beginMutation(request);
        try {
          const next = await jsonRequest(
            endpointFor(clientId, ''),
            'PUT',
            payload);
          acceptState(clientId, next, request, mutationGeneration);
          return next;
        } catch (error) {
          handleMutationError(error, clientId, showErrors);
          throw error;
        } finally {
          endMutation(request);
        }
      });
    saveChain = operation;
    trackOperation(operation);
    return operation;
  }

  function saveSnapshot() {
    return {
      inputType: localMode,
      manualText: element('speaking-manual-text')?.value || '',
      ttsEnabled: element('speaking-tts-enabled')?.checked === true,
      voiceCode: element('speaking-tts-voice')?.value || null,
      speed: numberValue(element('speaking-tts-speed')?.value, 1),
      outputFormat: element('speaking-tts-format')?.value || null
    };
  }

  function draftSnapshotMatchesState(snapshot) {
    if (!state || !snapshot || snapshot.inputType !== state.inputType) {
      return false;
    }
    if (snapshot.inputType === 'audio_upload') return true;
    const selected = state.selectedTts || {};
    return snapshot.manualText === (state.manualText || '')
      && snapshot.ttsEnabled === (state.ttsEnabled === true)
      && (snapshot.voiceCode || '') === (selected.voiceCode || '')
      && numberValue(snapshot.speed, 1)
        === numberValue(selected.speed, 1)
      && (snapshot.outputFormat || '') === (selected.outputFormat || '');
  }

  async function flush() {
    if (draftConflict) {
      markDraftConflict();
      return false;
    }
    if (transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi tiếp tục.',
        'error');
      return false;
    }
    if (saveTimer) {
      window.clearTimeout(saveTimer);
      saveTimer = null;
    }
    if (hasUnsavedInput()) queueSave(true);
    try {
      await saveChain;
      const outstanding = Array.from(pendingOperations);
      if (outstanding.length > 0) {
        await Promise.all(outstanding);
      }
      return !hasUnsavedInput() && pendingOperations.size === 0;
    } catch (error) {
      if (activeClientId && !draftConflict) {
        showMessage(safeRequestErrorMessage(error), 'error');
      }
      return false;
    }
  }

  function hasPendingChanges() {
    return draftConflict
      || !!saveTimer
      || hasUnsavedInput()
      || transcriptDirty
      || pendingOperations.size > 0;
  }

  function syncDraft() {
    if (!activeQuestion) return false;
    const changed = syncToQuestion(activeQuestion);
    if (changed
        && typeof window.onSpeakingPromptDraftMirrored === 'function') {
      window.onSpeakingPromptDraftMirrored();
    }
    return changed;
  }

  function syncToQuestion(question) {
    if (!question || !state) return false;
    const before = JSON.stringify({
      prompt: question.prompt,
      speakingPromptAuthoring: question.speakingPromptAuthoring,
      speakingPromptAudioUrl: question.speakingPromptAudioUrl,
      questionContent: question.questionContent
    });
    const selected = state.selectedTts || {};
    question.speakingPromptAuthoring = {
      inputType: state.inputType,
      ttsEnabled: state.ttsEnabled === true,
      voiceCode: selected.voiceCode || '',
      speed: selected.speed == null ? 1 : Number(selected.speed),
      outputFormat: selected.outputFormat || 'mp3',
      contractVersion: state.approvedTts
        ? state.approvedTts.contractVersion
        : 'speaking-prompt-authoring-v1'
    };
    if (state.inputType === 'manual_text') {
      question.prompt = state.manualText || question.prompt || '';
      const hidden = element('q-prompt');
      if (hidden) hidden.value = question.prompt;
    }
    const currentAudio = state.inputType === 'audio_upload'
      ? state.originalAudio
      : (state.generatedAudioCurrent ? state.generatedAudio : null);
    question.speakingPromptAudioUrl = currentAudio ? currentAudio.contentUrl : '';
    if (window.PracticeAuthoringContract) {
      window.PracticeAuthoringContract.syncQuestionContract(question);
    }
    const after = JSON.stringify({
      prompt: question.prompt,
      speakingPromptAuthoring: question.speakingPromptAuthoring,
      speakingPromptAudioUrl: question.speakingPromptAudioUrl,
      questionContent: question.questionContent
    });
    return before !== after;
  }

  function markGeneratedStaleLocally() {
    if (!state || !state.generatedAudio) return;
    state = Object.assign({}, state, {
      audioStatus: element('speaking-tts-enabled')?.checked ? 'stale' : 'idle',
      generatedAudioCurrent: false
    });
    renderGenerated(state);
  }

  function normalizedFileMime(file) {
    const value = file && typeof file.type === 'string' ? file.type : '';
    const separator = value.indexOf(';');
    return (separator < 0 ? value : value.substring(0, separator))
      .trim()
      .toLowerCase();
  }

  function fileExtension(file) {
    const name = file && typeof file.name === 'string' ? file.name : '';
    const dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.substring(dot).toLowerCase();
  }

  function probeClientAudioDuration(file) {
    if (!window.URL || typeof window.URL.createObjectURL !== 'function') {
      return Promise.resolve(null);
    }
    return new Promise(resolve => {
      const audio = document.createElement('audio');
      const objectUrl = window.URL.createObjectURL(file);
      let settled = false;
      const finish = duration => {
        if (settled) return;
        settled = true;
        window.clearTimeout(timeout);
        audio.removeAttribute('src');
        window.URL.revokeObjectURL(objectUrl);
        resolve(duration);
      };
      const timeout = window.setTimeout(() => finish(null), 2000);
      audio.preload = 'metadata';
      audio.addEventListener('loadedmetadata', () => {
        finish(Number.isFinite(audio.duration) && audio.duration > 0
          ? audio.duration
          : null);
      }, { once: true });
      audio.addEventListener('error', () => finish(null), { once: true });
      audio.src = objectUrl;
    });
  }

  async function validateAuthoringAudioFile(file) {
    const extension = fileExtension(file);
    const allowedMimes = AUTHORING_AUDIO_TYPES[extension];
    if (!allowedMimes) {
      showMessage(
        'Chỉ chấp nhận file MP3, WAV, M4A, OGG hoặc WebM. Hãy chọn lại đúng định dạng.',
        'error');
      return false;
    }
    const declaredMime = normalizedFileMime(file);
    if (!declaredMime || !allowedMimes.includes(declaredMime)) {
      showMessage(
        'Loại nội dung của file không khớp phần mở rộng. Hãy xuất lại audio đúng định dạng rồi chọn lại.',
        'error');
      return false;
    }
    const maximumBytes = state && state.maximumUploadBytes
      ? state.maximumUploadBytes
      : 50 * 1024 * 1024;
    if (file.size <= 0) {
      showMessage('Tệp audio đang rỗng. Hãy chọn một file có nội dung.', 'error');
      return false;
    }
    if (file.size > maximumBytes) {
      const maximumMb = Math.floor(maximumBytes / (1024 * 1024));
      showMessage(
        `Tệp audio vượt quá ${maximumMb} MB. Hãy giảm dung lượng rồi chọn lại.`,
        'error');
      return false;
    }
    const maximumSeconds = state && state.maximumUploadSeconds
      ? state.maximumUploadSeconds
      : 10 * 60;
    const duration = await probeClientAudioDuration(file);
    if (duration !== null && duration > maximumSeconds) {
      showMessage(
        `Tệp audio dài quá ${formatDuration(maximumSeconds)}. Hãy cắt ngắn rồi chọn lại.`,
        'error');
      return false;
    }
    return true;
  }

  async function upload(file) {
    if (!activeQuestion || !file) return;
    if (sourceDestructiveMutationPending()) {
      showMessage('Đang cập nhật nguồn audio. Vui lòng chờ.', 'error');
      return;
    }
    const clientId = activeClientId;
    const token = activationToken;
    if (!(await validateAuthoringAudioFile(file))) return;
    if (token !== activationToken || clientId !== activeClientId) return;
    if (localMode !== 'audio_upload') {
      localMode = 'audio_upload';
      markDirty();
      renderMode();
    }
    if (!(await flush()) || clientId !== activeClientId) return;
    beginSourceDestructiveMutation();
    let request = null;
    try {
      request = requestContext(clientId, true, 0);
      beginMutation(request);
      const uploadId = ++activeUploadId;
      const progress = element('speaking-prompt-upload-progress');
      if (progress) {
        progress.hidden = false;
        progress.removeAttribute('value');
      }
      showMessage('Đang tải file audio…', 'info');
      const data = new FormData();
      data.append('file', file);
      data.append(
        'expectedSourceRevision',
        String(acceptedRevision(clientId)));
      data.append(
        'expectedDraftVersion',
        String(request.expectedDraftVersion));
      const xhr = new XMLHttpRequest();
      xhr.open('POST', endpointFor(clientId, '/audio'));
      Object.entries(csrfHeaders()).forEach(([name, value]) => {
        xhr.setRequestHeader(name, value);
      });
      xhr.upload.addEventListener('progress', event => {
        if (uploadId !== activeUploadId
            || token !== activationToken
            || clientId !== activeClientId) return;
        if (!progress || !event.lengthComputable) return;
        progress.value = Math.round((event.loaded / event.total) * 100);
      });
      const operation = new Promise((resolve, reject) => {
        xhr.addEventListener('load', () => {
          if (uploadId === activeUploadId && progress) {
            progress.hidden = true;
          }
          let payload = null;
          try {
            const contentType = (xhr.getResponseHeader('content-type') || '')
              .toLowerCase();
            payload = contentType.includes('application/json')
              ? JSON.parse(xhr.responseText || '{}')
              : null;
          } catch (ignored) {
            payload = null;
          }
          let loginRedirect = false;
          try {
            loginRedirect = !!xhr.responseURL
              && new URL(xhr.responseURL, window.location.origin).pathname
                === '/login';
          } catch (ignored) {
            loginRedirect = false;
          }
          if (xhr.status !== 202 || loginRedirect || !payload) {
            reject(requestError(
              loginRedirect ? 401 : xhr.status,
              payload,
              loginRedirect
                ? 'AUTH_REQUIRED'
                : (!payload ? 'INVALID_RESPONSE' : null)));
            return;
          }
          acceptState(clientId, payload, request);
          resolve(payload);
        });
        xhr.addEventListener('error', () => {
          if (uploadId === activeUploadId && progress) {
            progress.hidden = true;
          }
          reject(requestError(0, null, 'AI_TEMPORARILY_UNAVAILABLE'));
        });
        xhr.send(data);
      });
      await trackOperation(operation);
      if (token === activationToken && clientId === activeClientId) {
        clearMessage();
      }
    } catch (error) {
      if (token === activationToken && clientId === activeClientId) {
        handleMutationError(error, clientId, true);
      }
    } finally {
      if (request) endMutation(request);
      endSourceDestructiveMutation();
    }
  }

  async function removeOriginal() {
    if (draftConflict) {
      markDraftConflict();
      return;
    }
    if (sourceDestructiveMutationPending()) {
      showMessage('Đang cập nhật nguồn audio. Vui lòng chờ.', 'error');
      return;
    }
    if (transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi gỡ audio.',
        'error');
      return;
    }
    if (!state || !state.originalAudio || !activeClientId) return;
    const clientId = activeClientId;
    beginSourceDestructiveMutation();
    const request = requestContext(clientId, true, 0);
    beginMutation(request);
    try {
      const operation = jsonRequest(
        endpointFor(clientId,
          `/audio?expectedSourceRevision=${encodeURIComponent(acceptedRevision(clientId))}`
            + `&expectedDraftVersion=${encodeURIComponent(request.expectedDraftVersion)}`),
        'DELETE');
      const next = await trackOperation(operation);
      acceptState(clientId, next, request);
    } catch (error) {
      handleMutationError(error, clientId, true);
    } finally {
      endMutation(request);
      endSourceDestructiveMutation();
    }
  }

  async function adoptExcelStaging() {
    if (draftConflict) {
      markDraftConflict();
      return;
    }
    if (sourceDestructiveMutationPending()) {
      showMessage('Đang cập nhật nguồn audio. Vui lòng chờ.', 'error');
      return;
    }
    if (transcriptDirty) {
      showMessage(
        'Hãy lưu và xác nhận Ngữ cảnh cho AI trước khi đổi audio.',
        'error');
      return;
    }
    if (!state || state.excelStagingAudioAvailable !== true
        || !activeClientId) return;
    const clientId = activeClientId;
    if (localMode !== 'audio_upload') {
      localMode = 'audio_upload';
      markDirty();
      renderMode();
    }
    if (!(await flush()) || clientId !== activeClientId) return;
    beginSourceDestructiveMutation();
    const request = requestContext(clientId, true, 0);
    beginMutation(request);
    try {
      showMessage(
        'Đang xác minh audio từ Excel và chuẩn bị bản chép lời…',
        'info');
      const operation = jsonRequest(
        endpointFor(clientId, '/audio/excel-staging'),
        'POST',
        {
          expectedSourceRevision: acceptedRevision(clientId),
          expectedDraftVersion: request.expectedDraftVersion
        });
      const next = await trackOperation(operation);
      acceptState(clientId, next, request);
      if (clientId === activeClientId) clearMessage();
    } catch (error) {
      handleMutationError(error, clientId, true);
    } finally {
      endMutation(request);
      endSourceDestructiveMutation();
    }
  }

  async function retryTranscription() {
    if (draftConflict) {
      markDraftConflict();
      return;
    }
    if (!state || !activeClientId) return;
    const clientId = activeClientId;
    const request = requestContext(clientId, true, 0);
    beginMutation(request);
    try {
      const operation = jsonRequest(
        endpointFor(clientId, '/transcription/retry'),
        'POST',
        {
          expectedSourceRevision: acceptedRevision(clientId),
          expectedDraftVersion: request.expectedDraftVersion
        });
      const next = await trackOperation(operation);
      acceptState(clientId, next, request);
    } catch (error) {
      handleMutationError(error, clientId, true);
    } finally {
      endMutation(request);
    }
  }

  async function saveTranscript() {
    if (draftConflict) {
      markDraftConflict();
      return;
    }
    if (sourceDestructiveMutationPending()) {
      showMessage('Đang cập nhật nguồn audio. Vui lòng chờ.', 'error');
      return;
    }
    if (!state || !activeClientId) return;
    const clientId = activeClientId;
    const contextGeneration = transcriptGeneration;
    const context = element('speaking-transcript-context')?.value || '';
    if (!context.trim()) {
      showMessage('Ngữ cảnh cho AI không được để trống.', 'error');
      return;
    }
    if (element('speaking-transcript-confirmed')?.checked !== true) {
      showMessage('Hãy xác nhận ngữ cảnh sau khi kiểm tra.', 'error');
      return;
    }
    const request = requestContext(clientId, true, 0);
    beginMutation(request);
    try {
      const operation = jsonRequest(
        endpointFor(clientId, '/transcription'),
        'PUT',
        {
          expectedSourceRevision: acceptedRevision(clientId),
          expectedDraftVersion: request.expectedDraftVersion,
          lecturerContext: context,
          confirmed: element('speaking-transcript-confirmed')?.checked === true
        });
      const next = await trackOperation(operation);
      const accepted = acceptState(clientId, next, request);
      if (accepted
          && clientId === activeClientId
          && contextGeneration === transcriptGeneration) {
        transcriptDirty = false;
        renderTranscript(next);
        clearMessage();
      }
    } catch (error) {
      handleMutationError(error, clientId, true);
    } finally {
      endMutation(request);
    }
  }

  async function generateTts() {
    if (!activeQuestion || localMode !== 'manual_text') return;
    const clientId = activeClientId;
    try {
      if (!(await flush())) return;
      if (clientId !== activeClientId || !state || !state.ttsEnabled) return;
      const request = requestContext(clientId, true, 0);
      beginMutation(request);
      try {
        const operation = jsonRequest(
          endpointFor(clientId, '/tts'),
          'POST',
          {
            expectedSourceRevision: acceptedRevision(clientId),
            expectedDraftVersion: request.expectedDraftVersion,
            voiceCode: element('speaking-tts-voice')?.value,
            speed: numberValue(element('speaking-tts-speed')?.value, 1),
            outputFormat: element('speaking-tts-format')?.value
          });
        const next = await trackOperation(operation);
        if (clientId === activeClientId) {
          clearMessage();
        }
        acceptState(clientId, next, request);
      } finally {
        endMutation(request);
      }
    } catch (error) {
      handleMutationError(error, clientId, true);
    }
  }

  function schedulePolling(next) {
    stopPolling();
    if (!activeClientId) return;
    const shouldPoll = [next.transcriptStatus, next.audioStatus,
      next.sttOperation && next.sttOperation.taskStatus,
      next.ttsOperation && next.ttsOperation.taskStatus]
      .some(status => POLLABLE.has(status));
    if (!shouldPoll) return;
    const clientId = activeClientId;
    pollTimer = window.setTimeout(async () => {
      pollTimer = null;
      if (clientId !== activeClientId) return;
      try {
        const request = requestContext(clientId);
        const refreshed = await jsonRequest(endpoint(''), 'GET');
        if (clientId !== activeClientId) return;
        acceptState(clientId, refreshed, request);
      } catch (error) {
        if (clientId === activeClientId) {
          if (error && error.status === 409) {
            markDraftConflict();
          } else {
            showMessage('Không thể cập nhật trạng thái. KSH sẽ thử lại.', 'info');
            pollTimer = window.setTimeout(() => schedulePolling(next), 4000);
          }
        }
      }
    }, 2500);
  }

  function stopPolling() {
    if (pollTimer) window.clearTimeout(pollTimer);
    pollTimer = null;
  }

  function numberValue(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function formatDuration(seconds) {
    const total = Math.max(0, Math.round(seconds));
    const minutes = Math.floor(total / 60);
    const remainder = total % 60;
    return `${minutes}:${String(remainder).padStart(2, '0')}`;
  }

  window.SpeakingPromptAuthoring = Object.freeze({
    activate,
    deactivate,
    flush,
    hasPendingChanges,
    upload,
    adoptExcelStaging,
    removeOriginal,
    renderLegacyPreview,
    syncToQuestion
  });
})();
