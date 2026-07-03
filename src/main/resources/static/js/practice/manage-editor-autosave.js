import { editorState } from './manage-editor-state.js';
import { updatePublishEligibilityBanner, updateValidationPanel, validateDraft } from './manage-editor-validation.js';

let autosaveTimeout = null;

export function triggerAutosave() {
  const status = document.getElementById('autosave-status');
  if (status) {
    status.innerText = "Đang tự động lưu...";
    status.style.background = 'rgba(245,158,11,0.1)';
    status.style.color = 'var(--pp-warning)';
  }

  if (autosaveTimeout) clearTimeout(autosaveTimeout);
  autosaveTimeout = setTimeout(performAutosave, 1000);
}

function csrfHeaders() {
  const tokenMeta = document.querySelector('meta[name="_csrf"]');
  const headerMeta = document.querySelector('meta[name="_csrf_header"]');
  const headers = {};
  if (tokenMeta && headerMeta) {
    headers[headerMeta.content] = tokenMeta.content;
  }
  return headers;
}

export function performAutosave() {
  if (!isBackendDraftAvailable()) {
    const status = document.getElementById('autosave-status');
    if (status) {
      status.innerText = "Bản nháp cục bộ";
      status.style.background = 'rgba(49, 94, 251, 0.08)';
      status.style.color = 'var(--pp-accent)';
    }
    validateDraft();
    return;
  }

  const titleInput = document.getElementById('draft-title')?.value || "";
  const bodyPayload = {
    draftJson: JSON.stringify(editorState.draft),
    title: titleInput,
    description: editorState.draft.document ? editorState.draft.document.description || "Soạn thảo thủ công" : "Soạn thảo thủ công",
    version: editorState.version
  };

  const headers = Object.assign({
    'Content-Type': 'application/json'
  }, csrfHeaders());

  let attempt = 0;
  const maxRetries = 3;

  function executeSave() {
    attempt++;
    fetch(`/practice/manage/drafts/${editorState.draftId}/autosave`, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(bodyPayload)
    })
    .then(res => {
      if (!res.ok) {
        throw { 
          type: 'http_error', 
          status: res.status, 
          message: `HTTP error: ${res.status}`
        };
      }
      return res.json();
    })
    .then(data => {
      const status = document.getElementById('autosave-status');
      if (data.status === 'success') {
        if (status) {
          status.innerText = "Đã lưu bản nháp";
          status.style.background = 'rgba(16, 185, 129, 0.1)';
          status.style.color = 'var(--pp-success)';
        }
        
        editorState.version = data.version;
        editorState.latestValidation = data.validation;
        updateValidationPanel(data.validation);
        updatePublishEligibilityBanner();
      } else if (data.status === 'conflict') {
        if (status) {
          status.innerText = "Xung đột ghi đè!";
          status.style.background = 'rgba(239, 68, 68, 0.1)';
          status.style.color = 'var(--pp-error)';
        }
        alert(data.error || "Một giảng viên khác đã chỉnh sửa bản nháp này. Vui lòng làm mới trang.");
      } else {
        throw {
          type: 'application_error',
          message: data.error || 'Server returned non-success status'
        };
      }
    })
    .catch(e => {
      console.warn(`[PracticeEditor] Autosave attempt ${attempt} failed:`, e);
      if (attempt < maxRetries) {
        console.log(`[PracticeEditor] Retrying autosave in 1000ms...`);
        setTimeout(executeSave, 1000);
      } else {
        console.error(`[PracticeEditor] Autosave failed after ${maxRetries} attempts. Detailed error:`, e);
        showSaveError();
      }
    });
  }

  executeSave();
}

function isBackendDraftAvailable() {
  return editorState.draftId && Number(editorState.draftId) > 0 && window.location.protocol !== 'file:';
}

function showSaveError() {
  const status = document.getElementById('autosave-status');
  if (status) {
    status.innerText = "Lưu thất bại!";
    status.style.background = 'rgba(239, 68, 68, 0.1)';
    status.style.color = 'var(--pp-error)';
  }
}
