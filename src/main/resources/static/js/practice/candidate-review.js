(function () {
  'use strict';

  const config = window.KSH_CANDIDATE_REVIEW || {};
  const candidateId = String(config.candidateId || '');
  const strategyCatalog = Array.isArray(config.strategyCatalog)
    ? config.strategyCatalog.filter(entry => entry && entry.selectable)
    : [];
  const baseUrl = `/practice/manage/authoring-candidates/${encodeURIComponent(candidateId)}`;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  let view = null;
  let groups = [];
  let typedEditors = [];
  let dirty = false;
  let applyRequestId = null;
  let previewReturnFocus = null;

  function headers() {
    const result = { 'Accept': 'application/json', 'Content-Type': 'application/json' };
    if (csrfHeader && csrfToken) result[csrfHeader] = csrfToken;
    return result;
  }

  async function request(path, options = {}) {
    const response = await fetch(`${baseUrl}${path}`, {
      credentials: 'same-origin',
      ...options,
      headers: { ...headers(), ...(options.headers || {}) }
    });
    const raw = await response.text();
    let payload = {};
    try { payload = raw ? JSON.parse(raw) : {}; } catch (ignored) {
      throw new Error(`Máy chủ trả về dữ liệu không hợp lệ (HTTP ${response.status}).`);
    }
    if (!response.ok) {
      const error = new Error(payload.error || payload.resultCode
        || `Thao tác thất bại (HTTP ${response.status}).`);
      error.code = payload.code || payload.resultCode || '';
      error.status = response.status;
      throw error;
    }
    return payload;
  }

  function showNotice(message, kind = '') {
    const notice = document.getElementById('candidate-notice');
    notice.textContent = message;
    notice.className = `candidate-notice${kind ? ` is-${kind}` : ''}`;
    notice.hidden = false;
    notice.focus();
  }

  function hideNotice() {
    document.getElementById('candidate-notice').hidden = true;
  }

  function stateLabel(state) {
    return ({
      REVIEWING: 'Đang rà soát', READY_TO_APPLY: 'Sẵn sàng áp dụng',
      APPLIED: 'Đã áp dụng', REJECTED: 'Đã từ chối', EXPIRED: 'Đã hết hạn',
      FAILED: 'Thất bại', VALIDATED: 'Đã kiểm tra'
    })[state] || state || 'Không xác định';
  }

  function deepCopy(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function issueOrder(issue) {
    const severity = ({ ERROR: 0, WARNING: 1, INFO: 2 })[issue.severity] ?? 3;
    const group = Number(issue.path?.match(/\/groups\/(\d+)/)?.[1] ?? -1);
    const question = Number(issue.path?.match(/\/questions\/(\d+)/)?.[1] ?? -1);
    const field = String(issue.path || '')
      .replace(/\/groups\/\d+/, '/groups')
      .replace(/\/questions\/\d+/, '/questions');
    return [severity, group, question, field, issue.code || ''];
  }

  function compareIssues(left, right) {
    const a = issueOrder(left);
    const b = issueOrder(right);
    for (let index = 0; index < a.length; index += 1) {
      if (typeof a[index] === 'number' && a[index] !== b[index]) return a[index] - b[index];
      const comparison = String(a[index]).localeCompare(String(b[index]), 'vi');
      if (comparison) return comparison;
    }
    return 0;
  }

  function focusIssue(path) {
    const candidates = [...document.querySelectorAll('[data-candidate-path]')];
    const target = candidates.find(node => node.dataset.candidatePath === path)
      || candidates.find(node => path.startsWith(node.dataset.candidatePath));
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.classList.add('candidate-highlight');
    target.focus?.();
    setTimeout(() => target.classList.remove('candidate-highlight'), 1800);
  }

  function renderIssues() {
    const list = document.getElementById('candidate-issue-list');
    const issues = [...(view?.issues || [])].sort(compareIssues);
    document.getElementById('candidate-issue-count').textContent = String(issues.length);
    list.replaceChildren();
    if (!issues.length) {
      const empty = document.createElement('div');
      empty.className = 'candidate-empty';
      empty.textContent = 'Không còn vấn đề validation.';
      list.appendChild(empty);
      return;
    }
    issues.forEach(issue => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `candidate-issue candidate-issue--${String(issue.severity || 'INFO').toLowerCase()}`;
      const code = document.createElement('strong');
      code.textContent = `${issue.severity} · ${issue.code}`;
      const message = document.createElement('span');
      message.textContent = issue.messageVi;
      const path = document.createElement('code');
      path.textContent = issue.path || '/';
      button.append(code, message, path);
      button.addEventListener('click', () => focusIssue(issue.path || ''));
      list.appendChild(button);
    });
  }

  function field(label, input, path, wide = false) {
    const wrapper = document.createElement('div');
    wrapper.className = `candidate-field${wide ? ' candidate-field--wide' : ''}`;
    wrapper.dataset.candidatePath = path;
    const title = document.createElement('label');
    title.textContent = label;
    if (input.id) title.htmlFor = input.id;
    wrapper.append(title, input);
    return wrapper;
  }

  function textInput(value, update, multiline = false) {
    const input = document.createElement(multiline ? 'textarea' : 'input');
    if (!multiline) input.type = 'text';
    input.className = multiline ? 'form-textarea' : 'form-input';
    input.value = value == null ? '' : String(value);
    input.addEventListener('input', () => {
      update(input.value);
      dirty = true;
      refreshActions();
    });
    return input;
  }

  function numberInput(value, update) {
    const input = document.createElement('input');
    input.type = 'number';
    input.min = '0.01';
    input.step = '0.01';
    input.className = 'form-input';
    input.value = value;
    input.addEventListener('input', () => {
      update(Number(input.value));
      dirty = true;
      refreshActions();
    });
    return input;
  }

  function typedJsonEditor(label, object, key, path) {
    const textarea = document.createElement('textarea');
    let jsonDirty = false;
    textarea.dataset.typedJson = 'true';
    textarea.className = 'form-textarea';
    textarea.value = JSON.stringify(object[key] || {}, null, 2);
    textarea.addEventListener('input', () => {
      jsonDirty = true;
      dirty = true;
      textarea.setCustomValidity('');
      refreshActions();
    });
    typedEditors.push(() => {
      if (!jsonDirty) return;
      try {
        const parsed = JSON.parse(textarea.value);
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error();
        object[key] = parsed;
        textarea.setCustomValidity('');
      } catch (error) {
        textarea.setCustomValidity(`${label} phải là một JSON object hợp lệ.`);
        textarea.reportValidity();
        throw new Error(`${label} không phải JSON object hợp lệ.`);
      }
    });
    return field(label, textarea, path, true);
  }

  function strategySupports(entry, group, question) {
    const questionType = question.questionType === 'GAP_FILL'
      ? 'FILL_BLANK' : question.questionType;
    if (!(entry.supportedQuestionTypes || []).includes(questionType)) return false;
    const evidence = entry.requiredEvidence || [];
    if (evidence.includes('OPTION_IDS')
        && !(question.questionContent?.options || []).length) return false;
    if (evidence.includes('BLANK_IDS')
        && !(question.questionContent?.blanks || []).length) return false;
    if (evidence.includes('SOURCE_TEXT')) {
      const stimulus = group.stimulus || {};
      const source = stimulus.passageText || stimulus.transcriptText || question.prompt;
      if (!String(source || '').trim()) return false;
    }
    return true;
  }

  function strategySelect(group, question, path) {
    const select = document.createElement('select');
    select.className = 'form-input';
    const blank = document.createElement('option');
    blank.value = '';
    blank.textContent = 'Chọn chiến lược giải thích…';
    select.appendChild(blank);
    strategyCatalog
      .filter(entry => strategySupports(entry, group, question))
      .forEach(entry => {
        const option = document.createElement('option');
        option.value = String(entry.code);
        option.textContent = `${entry.labelVi} — ${entry.descriptionVi}`;
        select.appendChild(option);
      });
    select.value = question.explanationStrategy?.strategyCode || '';
    select.addEventListener('change', () => {
      const selected = strategyCatalog.find(entry => String(entry.code) === select.value);
      if (!selected) delete question.explanationStrategy;
      else question.explanationStrategy = {
        registryVersion: selected.registryVersion,
        strategyCode: String(selected.code),
        strategyVersion: selected.strategyVersion
      };
      dirty = true;
      refreshActions();
    });
    return field('Chiến lược giải thích bắt buộc', select, path, true);
  }

  function renderQuestion(group, groupIndex, question, questionIndex) {
    const basePath = `/groups/${groupIndex}/questions/${questionIndex}`;
    const article = document.createElement('article');
    article.className = 'candidate-question';
    article.dataset.candidatePath = basePath;
    const header = document.createElement('div');
    header.className = 'candidate-question-header';
    const title = document.createElement('h4');
    title.textContent = `${question.questionOrder}. ${question.questionType}`;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'candidate-remove';
    remove.textContent = 'Loại câu khỏi candidate';
    remove.addEventListener('click', () => {
      group.questions.splice(questionIndex, 1);
      dirty = true;
      renderGroups();
      refreshActions();
    });
    header.append(title, remove);
    article.appendChild(header);

    const fields = document.createElement('div');
    fields.className = 'candidate-fields';
    fields.append(
      field('Prompt', textInput(question.prompt, value => { question.prompt = value; }, true), `${basePath}/prompt`, true),
      field('Điểm', numberInput(question.points, value => { question.points = value; }), `${basePath}/points`),
      field('Giải thích cho giảng viên (VI)', textInput(question.explanationVi || '', value => { question.explanationVi = value; }, true), `${basePath}/explanationVi`, true));

    const accepted = document.createElement('input');
    accepted.type = 'checkbox';
    accepted.checked = question.reviewState === 'ACCEPTED';
    accepted.addEventListener('change', () => {
      question.reviewState = accepted.checked ? 'ACCEPTED' : 'REVIEW_REQUIRED';
      dirty = true;
      refreshActions();
    });
    fields.append(field('Chấp nhận câu hỏi này', accepted, `${basePath}/reviewState`));
    if (['READING', 'LISTENING'].includes(view.candidate.target.skill)) {
      fields.appendChild(strategySelect(
        group, question, `${basePath}/explanationStrategy`));
    }

    const options = question.questionContent?.options || [];
    if (options.length) {
      const optionBlock = document.createElement('div');
      optionBlock.className = 'candidate-field candidate-field--wide';
      optionBlock.dataset.candidatePath = `${basePath}/questionContent/options`;
      optionBlock.appendChild(document.createElement('span')).textContent = 'Lựa chọn';
      options.forEach((option, optionIndex) => {
        const row = document.createElement('div');
        row.className = 'candidate-option-row';
        row.dataset.candidatePath = `${basePath}/questionContent/options/${optionIndex}/text`;
        const id = document.createElement('code');
        id.textContent = option.id;
        row.append(id, textInput(option.text, value => { option.text = value; }));
        optionBlock.appendChild(row);
      });
      fields.appendChild(optionBlock);
    }
    fields.append(
      typedJsonEditor('questionContent typed', question, 'questionContent', `${basePath}/questionContent`),
      typedJsonEditor('answerSpec typed', question, 'answerSpec', `${basePath}/answerSpec`));
    article.appendChild(fields);
    return article;
  }

  function renderGroup(group, groupIndex) {
    const basePath = `/groups/${groupIndex}`;
    const section = document.createElement('section');
    section.className = 'candidate-group';
    section.dataset.candidatePath = basePath;
    const header = document.createElement('div');
    header.className = 'candidate-group-header';
    const title = document.createElement('h3');
    title.textContent = `Nhóm ${group.groupOrder} · ${group.candidateGroupId}`;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'candidate-remove';
    remove.textContent = 'Loại nhóm';
    remove.addEventListener('click', () => {
      groups.splice(groupIndex, 1);
      dirty = true;
      renderGroups();
      refreshActions();
    });
    header.append(title, remove);
    section.appendChild(header);

    const fields = document.createElement('div');
    fields.className = 'candidate-fields';
    fields.style.padding = '14px';
    fields.append(
      field('Nhãn nhóm', textInput(group.label, value => { group.label = value; }), `${basePath}/label`),
      field('Hướng dẫn', textInput(group.instruction, value => { group.instruction = value; }, true), `${basePath}/instruction`, true));
    const stimulus = group.stimulus || (group.stimulus = {});
    const stimulusField = view.candidate.target.skill === 'LISTENING' ? 'transcriptText' : 'passageText';
    if (['READING', 'LISTENING'].includes(view.candidate.target.skill)) {
      fields.appendChild(field(
        view.candidate.target.skill === 'LISTENING' ? 'Transcript' : 'Bài đọc',
        textInput(stimulus[stimulusField] || '', value => { stimulus[stimulusField] = value; }, true),
        `${basePath}/stimulus/${stimulusField}`, true));
    }
    const approved = document.createElement('input');
    approved.type = 'checkbox';
    approved.checked = Boolean(stimulus.provenance?.approved);
    approved.addEventListener('change', () => {
      stimulus.provenance = stimulus.provenance || {};
      stimulus.provenance.approved = approved.checked;
      dirty = true;
      refreshActions();
    });
    fields.appendChild(field('Xác nhận stimulus', approved, `${basePath}/stimulus/provenance/approved`));
    section.appendChild(fields);

    const questions = document.createElement('div');
    questions.className = 'candidate-question-list';
    (group.questions || []).forEach((question, index) =>
      questions.appendChild(renderQuestion(group, groupIndex, question, index)));
    section.appendChild(questions);
    return section;
  }

  function renderGroups() {
    typedEditors = [];
    const container = document.getElementById('candidate-group-list');
    container.replaceChildren();
    if (!groups.length) {
      const empty = document.createElement('div');
      empty.className = 'candidate-empty';
      empty.textContent = 'Candidate không còn nhóm nào. Validator sẽ chặn READY/APPLY.';
      container.appendChild(empty);
      return;
    }
    groups.forEach((group, index) => container.appendChild(renderGroup(group, index)));
  }

  function hasWarnings() {
    return (view?.issues || []).some(issue => issue.severity === 'WARNING');
  }

  function hasBlockers() {
    return (view?.issues || []).some(issue => issue.blocking);
  }

  function refreshActions() {
    if (!view) return;
    const editable = ['REVIEWING', 'VALIDATED', 'READY_TO_APPLY'].includes(view.state);
    const warningsAccepted = !hasWarnings()
      || document.getElementById('warning-ack').checked;
    document.getElementById('save-review').disabled = !editable || !dirty;
    document.getElementById('mark-ready').disabled = view.state !== 'REVIEWING'
      || hasBlockers() || !warningsAccepted;
    document.getElementById('apply-candidate').disabled = view.state !== 'READY_TO_APPLY' || dirty;
    document.getElementById('learner-preview').disabled = !editable;
    document.getElementById('reject-candidate').disabled = !editable;
    document.querySelectorAll('.candidate-content input,.candidate-content textarea,.candidate-content select,.candidate-remove')
      .forEach(control => { control.disabled = !editable; });
    const warningLabel = document.getElementById('warning-ack-label');
    warningLabel.hidden = !hasWarnings();
  }

  function acceptView(nextView) {
    view = nextView;
    groups = deepCopy(view.candidate?.groups || []);
    dirty = false;
    applyRequestId = null;
    document.getElementById('candidate-state').textContent = stateLabel(view.state);
    document.getElementById('candidate-target').textContent =
      `Test ${view.candidate.target.testNo} · ${view.candidate.target.skill} · ${view.candidate.target.lessonCode}`;
    document.getElementById('candidate-version').textContent = String(view.version);
    document.getElementById('candidate-digest').textContent = view.contentDigest;
    document.getElementById('candidate-digest').title = view.contentDigest;
    document.getElementById('warning-ack').checked = Boolean(
      view.candidate?.warningsAcknowledged);
    document.getElementById('back-to-editor').href =
      `/practice/manage/drafts/${view.candidate.target.draftId}`;
    renderIssues();
    renderGroups();
    refreshActions();
  }

  function versionDigestBody() {
    return {
      candidateVersion: view.version,
      candidateDigest: view.contentDigest
    };
  }

  async function saveReview(showSuccess = true) {
    typedEditors.forEach(commit => commit());
    const payload = await request('/review', {
      method: 'POST',
      body: JSON.stringify({
        ...versionDigestBody(),
        groups,
        acknowledgeWarnings: document.getElementById('warning-ack').checked
      })
    });
    acceptView(payload);
    if (showSuccess) showNotice('Đã lưu rà soát; candidate đã normalize, validate và tính lại digest.', 'success');
    return payload;
  }

  async function guarded(action) {
    hideNotice();
    try {
      await action();
    } catch (error) {
      if (error.status === 409) {
        showNotice(`${error.message} Trang sẽ tải lại candidate mới nhất.`, 'error');
        try { acceptView(await request('/data')); } catch (ignored) { /* keep conflict */ }
      } else {
        showNotice(error.message || 'Không thể hoàn tất thao tác.', 'error');
      }
    }
  }

  document.getElementById('save-review').addEventListener('click', () =>
    guarded(() => saveReview(true)));

  document.getElementById('warning-ack').addEventListener('change', () => {
    dirty = true;
    refreshActions();
  });

  document.getElementById('mark-ready').addEventListener('click', () => guarded(async () => {
    await saveReview(false);
    const ready = await request('/ready', {
      method: 'POST', body: JSON.stringify(versionDigestBody())
    });
    acceptView(ready);
    showNotice('Candidate đã sẵn sàng. Bản nháp vẫn chưa thay đổi.', 'success');
  }));

  document.getElementById('reject-candidate').addEventListener('click', () => guarded(async () => {
    if (!window.confirm('Từ chối toàn bộ candidate này? Hành động không sửa bản nháp.')) return;
    const rejected = await request('/reject', {
      method: 'POST', body: JSON.stringify(versionDigestBody())
    });
    acceptView(rejected);
    showNotice('Đã từ chối candidate. Bản nháp không thay đổi.', 'success');
  }));

  document.getElementById('learner-preview').addEventListener('click', () => guarded(async () => {
    if (dirty) await saveReview(false);
    previewReturnFocus = document.activeElement;
    const result = await request('/learner-preview', {
      method: 'POST', body: JSON.stringify(versionDigestBody())
    });
    const delivery = window.PracticeDraftPreview.mapResponse(result.delivery);
    window.PracticeDraftPreview.renderModal(delivery);
    const modal = document.getElementById('preview-modal');
    modal.style.display = 'flex';
    modal.inert = false;
    modal.setAttribute('aria-hidden', 'false');
    document.getElementById('preview-modal-close')?.focus();
  }));

  document.getElementById('apply-candidate').addEventListener('click', () => guarded(async () => {
    if (!window.confirm('Áp dụng toàn bộ candidate vào bản nháp? Xuất bản vẫn là bước riêng.')) return;
    applyRequestId = applyRequestId || crypto.randomUUID();
    const result = await request('/apply', {
      method: 'POST',
      body: JSON.stringify({
        ...versionDigestBody(),
        applyRequestId
      })
    });
    showNotice(result.replayed
      ? 'Yêu cầu apply đã được ghi nhận trước đó; đang mở Editor.'
      : 'Đã áp dụng atomic thành công; đang mở Editor.', 'success');
    window.location.assign(result.editorUrl);
  }));

  window.closePreviewModal = function closePreviewModal() {
    document.querySelectorAll('#preview-modal audio').forEach(audio => {
      audio.pause();
      audio.removeAttribute('src');
    });
    const modal = document.getElementById('preview-modal');
    modal.inert = true;
    modal.setAttribute('aria-hidden', 'true');
    modal.style.display = 'none';
    if (previewReturnFocus instanceof HTMLElement && document.contains(previewReturnFocus)) {
      previewReturnFocus.focus();
    }
    previewReturnFocus = null;
  };

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape'
        && document.getElementById('preview-modal')?.getAttribute('aria-hidden') === 'false') {
      window.closePreviewModal();
    }
  });

  guarded(async () => acceptView(await request('/data')));
})();
