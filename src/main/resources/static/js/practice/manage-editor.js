console.info('[PracticeEditor] manage-editor.js loaded');

import { editorState } from './manage-editor-state.js';
import { renderTree, selectNode, handleQuestionTypeChange } from './manage-editor-tree.js';
import { 
  handleRootClick, 
  handleOutsideClick, 
  setupDropzone, 
  saveCurrentNode, 
  saveDraftMetadata, 
  addOptionRow,
  toggleAdvancedAudio,
  handleAudioSelect,
  handleImageSelect,
  handleRenameActive, 
  handleAddGroupFromMenu, 
  handleAddQuestionFromMenu, 
  handleMoveToGroupFromMenu, 
  handleDuplicateActive, 
  handleMoveActive, 
  handleDeleteActive,
  openPreviewModal,
  closePreviewModal,
  toggleDotsDropdown,
  confirmDeleteDraft,
  focusDraftTitle,
  setCorrectOption,
  removeOptionRow,
  setTfngCorrect,
  syncAddMenuContextButtons
} from './manage-editor-actions.js';
import { validateDraft } from './manage-editor-validation.js';
import { triggerAutosave } from './manage-editor-autosave.js';

function bindEditorEvents() {
  const editorRoot = document.querySelector("[data-practice-editor-root]");
  console.info('[PracticeEditor] root:', editorRoot);

  if (!editorRoot) {
    throw new Error("Missing practice editor root");
  }

  editorRoot.addEventListener("click", handleRootClick);
  document.addEventListener("click", handleOutsideClick);
  console.info('[PracticeEditor] click listeners bound');
}

document.addEventListener("DOMContentLoaded", () => {
  try {
    bindEditorEvents();
  } catch (error) {
    console.error("[PracticeEditor] Event binding failed:", error);
  }

  try {
    renderTree();
    setupDropzone();
    validateDraft();
  } catch (error) {
    console.error("[PracticeEditor] Initialization failed", error);
    alert("Trình biên soạn khởi tạo không thành công.");
  }
});

// decoupled tree -> actions callbacks via custom events
window.addEventListener('practice-editor:navigate', (event) => {
  const { type, sIdx, gIdx, qIdx } = event.detail;
  selectNode(type, sIdx, gIdx, qIdx);
});
window.addEventListener('practice-editor:node-selected', () => {
  syncAddMenuContextButtons();
});

// Export globals for legacy compatibility
window.saveCurrentNode = saveCurrentNode;
window.saveDraftMetadata = saveDraftMetadata;
window.handleQuestionTypeChange = handleQuestionTypeChange;
window.addOptionRow = addOptionRow;
window.toggleAdvancedAudio = toggleAdvancedAudio;
window.handleAudioSelect = handleAudioSelect;
window.handleImageSelect = handleImageSelect;
window.selectNode = selectNode;
window.handleRenameActive = handleRenameActive;
window.handleAddGroupFromMenu = handleAddGroupFromMenu;
window.handleAddQuestionFromMenu = handleAddQuestionFromMenu;
window.handleMoveToGroupFromMenu = handleMoveToGroupFromMenu;
window.handleDuplicateActive = handleDuplicateActive;
window.handleMoveActive = handleMoveActive;
window.handleDeleteActive = handleDeleteActive;
window.openPreviewModal = openPreviewModal;
window.closePreviewModal = closePreviewModal;
window.toggleDotsDropdown = toggleDotsDropdown;
window.confirmDeleteDraft = confirmDeleteDraft;
window.focusDraftTitle = focusDraftTitle;
window.setCorrectOption = setCorrectOption;
window.removeOptionRow = removeOptionRow;
window.setTfngCorrect = setTfngCorrect;
window.triggerAutosave = triggerAutosave;
