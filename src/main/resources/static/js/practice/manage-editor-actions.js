import { editorState, makeClientId, normalizeDraftTree, updateGroupQuestionRange } from './manage-editor-state.js';

/**
 * Re-number all questions within a section sequentially (1, 2, 3, …)
 * based on current group order. Must be called after any mutation that
 * changes group/question order within a section.
 */
export function reNumberSectionQuestions(sIdx) {
  const sec = editorState.draft.sections[sIdx];
  if (!sec || !sec.groups) return;
  let counter = 1;
  sec.groups.forEach(grp => {
    if (grp.questions) {
      grp.questions.forEach(q => { q.questionNo = counter++; });
      updateGroupQuestionRange(grp);
    }
  });
}
import { 
  renderTree, 
  selectNode, 
  getSkillName, 
  getGroupName,
  renderOptionRows,
  updateAudioPreview,
  updateImagePreview,
  updateDraftSkillDisplay
} from './manage-editor-tree.js';
import { triggerAutosave } from './manage-editor-autosave.js';
import { validateDraft } from './manage-editor-validation.js';

export function syncAddMenuContextButtons() {
  const grpBtn = document.getElementById('menu-add-group');
  const qBtn = document.getElementById('menu-add-question');
  if (!grpBtn || !qBtn) return;

  const canAddGroup = !!(editorState.currentNode && (editorState.currentNode.type === 'section' || editorState.currentNode.type === 'group' || editorState.currentNode.type === 'question'));
  const canAddQuestion = !!(editorState.currentNode && (editorState.currentNode.type === 'group' || editorState.currentNode.type === 'question'));

  grpBtn.disabled = !canAddGroup;
  grpBtn.style.opacity = canAddGroup ? '1' : '0.45';
  grpBtn.style.cursor = canAddGroup ? 'pointer' : 'not-allowed';

  qBtn.disabled = !canAddQuestion;
  qBtn.style.opacity = canAddQuestion ? '1' : '0.45';
  qBtn.style.cursor = canAddQuestion ? 'pointer' : 'not-allowed';
}

function showEditorToast(message, type = "info") {
  alert(message);
}

function refreshCopiedQuestionIds(question) {
  if (!question || typeof question !== 'object') return question;
  question.clientId = makeClientId('q');
  return question;
}

function refreshCopiedGroupIds(group) {
  if (!group || typeof group !== 'object') return group;
  group.clientId = makeClientId('grp');
  if (Array.isArray(group.questions)) {
    group.questions.forEach(refreshCopiedQuestionIds);
  }
  return group;
}

function refreshCopiedSectionIds(section) {
  if (!section || typeof section !== 'object') return section;
  section.clientId = makeClientId('sec');
  if (Array.isArray(section.groups)) {
    section.groups.forEach(refreshCopiedGroupIds);
  }
  return section;
}

export function addSectionBySkill(skill) {
  if (!skill || !["READING", "LISTENING", "WRITING", "SPEAKING"].includes(skill)) {
    console.error("[PracticeEditor] Action failed: missing data-skill or invalid skill");
    showEditorToast("Lỗi: missing data-skill", "error");
    return;
  }
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft || !Array.isArray(editorState.draft.sections)) {
    console.error("[PracticeEditor] Action failed: editor state chưa khởi tạo");
    showEditorToast("Lỗi: editor state chưa khởi tạo", "error");
    return;
  }
  if (typeof renderTree !== 'function' || typeof selectNode !== 'function') {
    console.error("[PracticeEditor] Action failed: render function không tồn tại");
    showEditorToast("Lỗi: render function không tồn tại", "error");
    return;
  }

  const BASE_NAMES = {
    READING: 'Phần Đọc',
    LISTENING: 'Phần Nghe',
    WRITING: 'Phần Viết',
    SPEAKING: 'Phần Nói'
  };
  const baseName = BASE_NAMES[skill] || 'Phần mới';

  let candidate = baseName;
  let count = 2;
  while (editorState.draft.sections.some(s => s.title === candidate)) {
    candidate = baseName + ' ' + count++;
  }

  const newSec = {
    clientId: makeClientId('sec'),
    title: candidate,
    skill: skill,
    durationMinutes: skill === 'WRITING' ? 50 : 40,
    groups: []
  };

  editorState.draft.sections.push(newSec);
  const newIdx = editorState.draft.sections.length - 1;
  editorState.expandedNodes[`sec-${newIdx}`] = true;

  try {
    selectNode('section', newIdx);
    syncAddMenuContextButtons();
    validateDraft();
  } catch (err) {
    console.error('[DraftEditor] selectNode threw an error after adding section:', err);
  }

  triggerAutosave();
}

export function addSectionWrapper(skill, event) {
  if (event) { event.preventDefault(); event.stopPropagation(); }
  addSectionBySkill(skill);
  closeAddMenu();
}

export function addGroupWrapper(event) {
  if (event) event.preventDefault();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (typeof renderTree !== 'function' || typeof selectNode !== 'function') {
    console.error("[PracticeEditor] Action failed: render function không tồn tại");
    showEditorToast("Lỗi: render function không tồn tại", "error");
    return;
  }
  if (!editorState.currentNode || editorState.currentNode.sIdx === null || editorState.currentNode.sIdx === undefined) {
    console.error("[PracticeEditor] Action failed: selected section not found");
    showEditorToast("Hãy chọn một phần thi trước", "warning");
    return;
  }
  const sIdx = editorState.currentNode.sIdx;
  const sec = editorState.draft.sections[sIdx];
  if (!sec) {
    console.error("[PracticeEditor] Action failed: selected section not found in DRAFT_DATA");
    return;
  }
  
  const newGrp = {
    clientId: makeClientId('grp'),
    label: "Nhóm mới",
    instruction: "Hãy đọc đoạn văn sau và chọn đáp án chính xác.",
    passageText: "",
    questionFrom: null,
    questionTo: null,
    questions: []
  };
  if (!sec.groups) sec.groups = [];
  sec.groups.push(newGrp);
  const newGIdx = sec.groups.length - 1;
  editorState.expandedNodes[`grp-${sIdx}-${newGIdx}`] = true;
  selectNode('group', sIdx, newGIdx);
  syncAddMenuContextButtons();
  triggerAutosave();
}

export function addQuestionWrapper(event) {
  if (event) event.preventDefault();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (typeof renderTree !== 'function' || typeof selectNode !== 'function') {
    console.error("[PracticeEditor] Action failed: render function không tồn tại");
    showEditorToast("Lỗi: render function không tồn tại", "error");
    return;
  }
  if (!editorState.currentNode || editorState.currentNode.sIdx === null || editorState.currentNode.gIdx === null || editorState.currentNode.sIdx === undefined || editorState.currentNode.gIdx === undefined) {
    console.error("[PracticeEditor] Action failed: selected group not found");
    showEditorToast("Hãy chọn một nhóm câu hỏi trước", "warning");
    return;
  }
  const { sIdx, gIdx } = editorState.currentNode;
  const sec = editorState.draft.sections[sIdx];
  if (!sec) return;
  const grp = sec.groups[gIdx];
  if (!grp) {
    console.error("[PracticeEditor] Action failed: selected group not found in DRAFT_DATA");
    return;
  }

  let maxNo = 0;
  if (sec.groups) {
    sec.groups.forEach(g => {
      if (g.questions) {
        g.questions.forEach(q => {
          const no = parseInt(q.questionNo, 10);
          if (!isNaN(no) && no > maxNo) {
            maxNo = no;
          }
        });
      }
    });
  }
  const nextQNo = maxNo + 1;

  let qType = "SINGLE_CHOICE";
  let options = [];
  let answerKey = "";
  if (sec.skill === "WRITING") {
    qType = "ESSAY";
    options = [];
    answerKey = "";
  } else if (sec.skill === "SPEAKING") {
    qType = "SPEAKING";
    options = [];
    answerKey = "";
  } else {
    qType = "SINGLE_CHOICE";
    options = ["Phương án A", "Phương án B", "Phương án C", "Phương án D"];
    answerKey = "1";
  }

  const newQ = {
    clientId: makeClientId('q'),
    questionNo: nextQNo,
    questionType: qType,
    prompt: qType === "MCQ" || qType === "SINGLE_CHOICE" ? "Nội dung câu hỏi trắc nghiệm" : "Yêu cầu bài làm",
    options: options,
    answer: qType === "MCQ" || qType === "SINGLE_CHOICE" ? { type: "SINGLE", value: answerKey } : null,
    answerKey: answerKey,
    explanationVi: "",
    points: 2.0
  };

  if (!grp.questions) grp.questions = [];
  grp.questions.push(newQ);
  const newQIdx = grp.questions.length - 1;

  updateGroupQuestionRange(grp);

  selectNode('question', sIdx, gIdx, newQIdx);
  validateDraft();
  triggerAutosave();
}

export function deleteSection(sIdx, event) {
  if (event) event.stopPropagation();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx]) return;
  const sec = editorState.draft.sections[sIdx];
  const grps = sec.groups || [];
  let qCount = 0;
  grps.forEach(g => { if (g.questions) qCount += g.questions.length; });

  const msg = `Xoá phần thi "${sec.title}"?\nHành động này sẽ xóa đồng thời ${grps.length} nhóm và ${qCount} câu hỏi con.`;
  if (confirm(msg)) {
    editorState.draft.sections.splice(sIdx, 1);
    editorState.currentNode = null;
    document.getElementById('editor-placeholder').style.display = 'block';
    renderTree();
    triggerAutosave();
  }
}

export function deleteGroup(sIdx, gIdx, event) {
  if (event) event.stopPropagation();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx] || !editorState.draft.sections[sIdx].groups[gIdx]) return;
  const grp = editorState.draft.sections[sIdx].groups[gIdx];
  const qs = grp.questions || [];

  const msg = `Xoá nhóm câu hỏi "${grp.label}"?\nHành động này sẽ xóa đồng thời ${qs.length} câu hỏi con.`;
  if (confirm(msg)) {
    editorState.draft.sections[sIdx].groups.splice(gIdx, 1);
    reNumberSectionQuestions(sIdx);
    editorState.currentNode = null;
    document.getElementById('editor-placeholder').style.display = 'block';
    renderTree();
    triggerAutosave();
  }
}

export function deleteQuestion(sIdx, gIdx, qIdx, event) {
  if (event) event.stopPropagation();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx] || !editorState.draft.sections[sIdx].groups[gIdx] || !editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx]) return;
  const grp = editorState.draft.sections[sIdx].groups[gIdx];
  const q = grp.questions[qIdx];
  if (confirm(`Bạn có chắc muốn xoá Câu số ${q.questionNo}?`)) {
    grp.questions.splice(qIdx, 1);
    reNumberSectionQuestions(sIdx);
    editorState.currentNode = null;
    document.getElementById('editor-placeholder').style.display = 'block';
    renderTree();
    triggerAutosave();
  }
}

export function moveSection(sIdx, dir) {
  editorState.draft = normalizeDraftTree(editorState.draft);
  const targetIdx = sIdx + dir;
  if (targetIdx < 0 || targetIdx >= editorState.draft.sections.length) return;
  const temp = editorState.draft.sections[sIdx];
  editorState.draft.sections[sIdx] = editorState.draft.sections[targetIdx];
  editorState.draft.sections[targetIdx] = temp;
  if (editorState.currentNode && editorState.currentNode.type === 'section') {
    if (editorState.currentNode.sIdx === sIdx) editorState.currentNode.sIdx = targetIdx;
    else if (editorState.currentNode.sIdx === targetIdx) editorState.currentNode.sIdx = sIdx;
  }
  renderTree();
  triggerAutosave();
}

export function moveGroup(sIdx, gIdx, dir) {
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx]) return;
  const grps = editorState.draft.sections[sIdx].groups;
  const targetIdx = gIdx + dir;
  if (targetIdx < 0 || targetIdx >= grps.length) return;
  const temp = grps[gIdx];
  grps[gIdx] = grps[targetIdx];
  grps[targetIdx] = temp;
  if (editorState.currentNode && editorState.currentNode.type === 'group' && editorState.currentNode.sIdx === sIdx) {
    if (editorState.currentNode.gIdx === gIdx) editorState.currentNode.gIdx = targetIdx;
    else if (editorState.currentNode.gIdx === targetIdx) editorState.currentNode.gIdx = gIdx;
  }
  reNumberSectionQuestions(sIdx);
  renderTree();
  triggerAutosave();
}

export function moveQuestion(sIdx, gIdx, qIdx, dir, event) {
  if (event) event.stopPropagation();
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx] || !editorState.draft.sections[sIdx].groups[gIdx]) return;
  const questions = editorState.draft.sections[sIdx].groups[gIdx].questions;
  if (dir === -1 && qIdx === 0) return;
  if (dir === 1 && qIdx === questions.length - 1) return;
  
  const targetIdx = qIdx + dir;
  const temp = questions[qIdx];
  questions[qIdx] = questions[targetIdx];
  questions[targetIdx] = temp;
  
  if (editorState.currentNode && editorState.currentNode.type === 'question' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx) {
    editorState.currentNode.qIdx = targetIdx;
  }
  renderTree();
  renderGroupQuestions(sIdx, gIdx);
  triggerAutosave();
}

export function duplicateSection(sIdx) {
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx]) return;
  const copy = JSON.parse(JSON.stringify(editorState.draft.sections[sIdx]));
  refreshCopiedSectionIds(copy);
  copy.title += " (Bản sao)";
  editorState.draft.sections.splice(sIdx + 1, 0, copy);
  reNumberSectionQuestions(sIdx + 1);
  renderTree();
  triggerAutosave();
}

export function duplicateGroup(sIdx, gIdx) {
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx] || !editorState.draft.sections[sIdx].groups[gIdx]) return;
  const copy = JSON.parse(JSON.stringify(editorState.draft.sections[sIdx].groups[gIdx]));
  refreshCopiedGroupIds(copy);
  copy.label += " (Bản sao)";
  editorState.draft.sections[sIdx].groups.splice(gIdx + 1, 0, copy);
  reNumberSectionQuestions(sIdx);
  renderTree();
  triggerAutosave();
}

export function duplicateQuestion(sIdx, gIdx, qIdx) {
  editorState.draft = normalizeDraftTree(editorState.draft);
  if (!editorState.draft.sections[sIdx] || !editorState.draft.sections[sIdx].groups[gIdx] || !editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx]) return;
  const copy = JSON.parse(JSON.stringify(editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx]));
  refreshCopiedQuestionIds(copy);
  copy.questionNo += 1;
  editorState.draft.sections[sIdx].groups[gIdx].questions.splice(qIdx + 1, 0, copy);
  reNumberSectionQuestions(sIdx);
  renderTree();
  triggerAutosave();
}

export function startRenameSection(sIdx, event) {
  event.stopPropagation();
  const sec = editorState.draft.sections[sIdx];
  const val = prompt("Nhập tên phần thi mới:", sec.title);
  if (val && val.trim().length > 0) {
    sec.title = val.trim();
    renderTree();
    if (editorState.currentNode && editorState.currentNode.type === 'section' && editorState.currentNode.sIdx === sIdx) {
      document.getElementById('sec-title').value = sec.title;
    }
    triggerAutosave();
  }
}

export function startRenameGroup(sIdx, gIdx, event) {
  event.stopPropagation();
  const grp = editorState.draft.sections[sIdx].groups[gIdx];
  const val = prompt("Nhập tên nhãn nhóm mới (ví dụ: 1-5):", grp.label);
  if (val && val.trim().length > 0) {
    grp.label = val.trim();
    renderTree();
    if (editorState.currentNode && editorState.currentNode.type === 'group' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx) {
      document.getElementById('grp-label').value = grp.label;
    }
    triggerAutosave();
  }
}

export function startRenameQuestion(sIdx, gIdx, qIdx, event) {
  event.stopPropagation();
  const q = editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx];
  const val = prompt("Nhập số câu hỏi mới:", q.questionNo);
  if (val && !isNaN(parseInt(val))) {
    q.questionNo = parseInt(val);
    renderTree();
    if (editorState.currentNode && editorState.currentNode.type === 'question' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx && editorState.currentNode.qIdx === qIdx) {
      document.getElementById('q-no').value = q.questionNo;
    }
    triggerAutosave();
  }
}

export function saveCurrentNode() {
  if (!editorState.currentNode) return;
  editorState.draft = normalizeDraftTree(editorState.draft);
  const { type, sIdx, gIdx, qIdx } = editorState.currentNode;

  if (type === 'section') {
    const sec = editorState.draft.sections[sIdx];
    if (!sec) return;
    sec.title = document.getElementById('sec-title').value;
    sec.skill = document.getElementById('sec-skill').value;
    sec.durationMinutes = parseInt(document.getElementById('sec-duration').value) || 40;
  } else if (type === 'group') {
    const grp = editorState.draft.sections[sIdx].groups[gIdx];
    if (!grp) return;
    grp.label = document.getElementById('grp-label').value;
    grp.audioUrl = document.getElementById('grp-audio').value;
    grp.passageText = document.getElementById('grp-passage').value;
  } else if (type === 'question') {
    const grp = editorState.draft.sections[sIdx].groups[gIdx];
    if (!grp || !grp.questions[qIdx]) return;
    const q = grp.questions[qIdx];
    q.questionNo = parseInt(document.getElementById('q-no').value) || (qIdx + 1);
    q.prompt = document.getElementById('q-prompt').value;
    q.points = parseFloat(document.getElementById('q-points').value) || 2.0;
    q.explanationVi = document.getElementById('q-explanation').value;
    
    const typeSel = document.getElementById('q-type').value;
    if (typeSel === 'MATCHING' || typeSel === 'MATCHING_INFORMATION') {
      q.answerKey = document.getElementById('q-answer-key').value;
    } else if (typeSel === 'GAP_FILL' || typeSel === 'FILL_BLANK') {
      const val = document.getElementById('q-gap-key').value;
      q.answer = { type: "FILL", value: val };
      q.answerKey = val;
    } else if (typeSel === 'ESSAY') {
      q.essayMinChars = parseInt(document.getElementById('q-essay-min').value) || 0;
      q.essayMaxChars = parseInt(document.getElementById('q-essay-max').value) || 0;
      q.essayTaskType = document.getElementById('q-essay-task').value;
      q.essaySample = document.getElementById('q-essay-sample').value;
      q.essayRubric = document.getElementById('q-essay-rubric').value;
    } else if (typeSel === 'SPEAKING') {
      q.prepTimeSeconds = parseInt(document.getElementById('q-speak-prep').value) || 0;
      q.respTimeSeconds = parseInt(document.getElementById('q-speak-resp').value) || 0;
      q.speakingSample = document.getElementById('q-speak-sample').value;
    }
    updateGroupQuestionRange(grp);
  }

  renderTree();
  validateDraft();
  triggerAutosave();
}

export function saveDraftMetadata() {
  if (!editorState.draft.document) editorState.draft.document = {};
  const selectedCat = document.getElementById('draft-category').value;
  editorState.draft.document.detectedCategory = selectedCat;
  editorState.draft.category = selectedCat;
  editorState.draft.document.description = document.getElementById('draft-description').value;
  triggerAutosave();
  updateDraftSkillDisplay();
}

export function handleQuestionTypeChange() {
  if (!editorState.currentNode || editorState.currentNode.type !== 'question') return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  const type = document.getElementById('q-type').value;
  q.questionType = type;

  document.getElementById('options-area').style.display = 'none';
  document.getElementById('tfng-area').style.display = 'none';
  document.getElementById('matching-area').style.display = 'none';
  document.getElementById('gapfill-area').style.display = 'none';
  document.getElementById('essay-area').style.display = 'none';
  document.getElementById('speaking-area').style.display = 'none';

  if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
    document.getElementById('options-area').style.display = 'block';
    renderOptionRows(q);
  } else if (type === 'TRUE_FALSE_NOT_GIVEN') {
    document.getElementById('tfng-area').style.display = 'block';
    const val = (q.answer && q.answer.value) || q.answerKey || '';
    document.querySelectorAll('input[name="tfng-correct"]').forEach(rad => {
      rad.checked = (rad.value === val);
    });
  } else if (type === 'MATCHING' || type === 'MATCHING_INFORMATION') {
    document.getElementById('matching-area').style.display = 'block';
    document.getElementById('q-answer-key').value = q.answerKey || '';
  } else if (type === 'GAP_FILL' || type === 'FILL_BLANK') {
    document.getElementById('gapfill-area').style.display = 'block';
    document.getElementById('q-gap-key').value = (q.answer && q.answer.value) || q.answerKey || '';
  } else if (type === 'ESSAY') {
    document.getElementById('essay-area').style.display = 'block';
    document.getElementById('q-essay-min').value = q.essayMinChars || '';
    document.getElementById('q-essay-max').value = q.essayMaxChars || '';
    document.getElementById('q-essay-task').value = q.essayTaskType || 'Q53';
    document.getElementById('q-essay-sample').value = q.essaySample || '';
    document.getElementById('q-essay-rubric').value = q.essayRubric || '';
  } else if (type === 'SPEAKING') {
    document.getElementById('speaking-area').style.display = 'block';
    document.getElementById('q-speak-prep').value = q.prepTimeSeconds || '';
    document.getElementById('q-speak-resp').value = q.respTimeSeconds || '';
    document.getElementById('q-speak-sample').value = q.speakingSample || '';
  }

  triggerAutosave();
}

function getCircledNumber(num) {
  const circles = ["⓪", "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"];
  return circles[num] || `(${num})`;
}

export function renderOptionRows(q) {
  const list = document.getElementById('options-list');
  if (!list) return;
  list.innerHTML = '';

  if (!q.options) q.options = ["Phương án A", "Phương án B", "Phương án C", "Phương án D"];

  q.options.forEach((opt, idx) => {
    const val = opt.text || opt;
    const selectedValues = String((q.answer && q.answer.value) || q.answerKey || '')
      .split(',')
      .map(v => v.trim())
      .filter(Boolean);
    const isCorrect = selectedValues.includes(String(idx + 1));

    const card = document.createElement('div');
    card.className = 'opt-card';
    
    // Create elements instead of innerHTML onclick to avoid global namespaces
    const container = document.createElement('div');
    container.style.display = 'flex';
    container.style.alignItems = 'center';
    container.style.gap = '12px';
    container.style.width = '100%';
    
    const dragHandle = document.createElement('div');
    dragHandle.className = 'opt-drag-handle';
    dragHandle.innerText = '☰';
    container.appendChild(dragHandle);

    const optIndex = document.createElement('div');
    optIndex.className = 'opt-index';
    optIndex.innerText = getCircledNumber(idx + 1);
    container.appendChild(optIndex);

    const checkbox = document.createElement('input');
    checkbox.type = q.questionType === 'SINGLE_CHOICE' ? 'radio' : 'checkbox';
    checkbox.name = 'correct-opt';
    checkbox.className = 'opt-checkbox';
    checkbox.checked = isCorrect;
    checkbox.addEventListener('change', () => setCorrectOption(idx));
    container.appendChild(checkbox);

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'opt-text-input';
    input.value = val;
    input.addEventListener('input', () => saveOptions());
    container.appendChild(input);

    const delBtn = document.createElement('button');
    delBtn.className = 'opt-delete-btn';
    delBtn.innerText = '✕';
    delBtn.addEventListener('click', () => removeOptionRow(idx));
    container.appendChild(delBtn);

    card.appendChild(container);
    list.appendChild(card);
  });
}

export function setCorrectOption(idx) {
  if (!editorState.currentNode) return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  if (q.questionType === 'MULTIPLE_CHOICE') {
    const inputs = Array.from(document.querySelectorAll('input[name="correct-opt"]'));
    const selected = inputs
      .map((input, inputIdx) => input.checked ? String(inputIdx + 1) : null)
      .filter(Boolean);
    q.answer = { type: "MULTIPLE", value: selected.join(',') };
    q.answerKey = selected.join(',');
  } else {
    q.answer = { type: "SINGLE", value: String(idx + 1) };
    q.answerKey = String(idx + 1);
  }
  triggerAutosave();
}

export function saveOptions() {
  if (!editorState.currentNode) return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  const inputs = document.querySelectorAll('.opt-text-input');
  q.options = [];
  inputs.forEach(input => {
    q.options.push(input.value);
  });
  triggerAutosave();
}

export function addOptionRow() {
  if (!editorState.currentNode) return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  if (!q.options) q.options = [];
  q.options.push("Lựa chọn mới");
  renderOptionRows(q);
  triggerAutosave();
}

export function removeOptionRow(idx) {
  if (!editorState.currentNode) return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  q.options.splice(idx, 1);
  renderOptionRows(q);
  triggerAutosave();
}

export function setTfngCorrect(val) {
  if (!editorState.currentNode) return;
  const q = editorState.draft.sections[editorState.currentNode.sIdx].groups[editorState.currentNode.gIdx].questions[editorState.currentNode.qIdx];
  q.answer = { type: "TFNG", value: val };
  q.answerKey = val;
  triggerAutosave();
}

export function updateAudioPreview(url) {
  const container = document.getElementById('audio-preview-container');
  if (!container) return;
  if (url && url.trim().length > 0) {
    container.style.display = 'block';
    container.innerHTML = `
      <div style="display:flex; flex-direction:column; gap:8px;">
        <div style="font-size:0.8rem; font-weight:600; color:var(--pp-text-muted); display:flex; justify-content:space-between; align-items:center;">
          <span>Đang phát thử tài nguyên:</span>
          <a href="${url}" target="_blank" style="color:var(--pp-accent); display:flex; align-items:center; gap:4px; text-decoration:none;">
            Tải xuống
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          </a>
        </div>
        <audio controls src="${url}" style="width:100%; border-radius:8px;"></audio>
      </div>
    `;
  } else {
    container.style.display = 'none';
  }
}

export function updateImagePreview(url, type) {
  const container = document.getElementById(type + '-image-preview-container');
  if (!container) return;
  if (url && url.trim().length > 0) {
    container.style.display = 'block';
    
    const wrapper = document.createElement('div');
    wrapper.style.position = 'relative';
    wrapper.style.display = 'inline-block';
    wrapper.style.maxWidth = '100%';
    
    const img = document.createElement('img');
    img.src = url;
    img.style.maxHeight = '200px';
    img.style.maxWidth = '100%';
    img.style.borderRadius = '6px';
    img.style.display = 'block';
    img.style.margin = '0 auto';
    wrapper.appendChild(img);

    const delBtn = document.createElement('button');
    delBtn.type = 'button';
    delBtn.style.position = 'absolute';
    delBtn.style.top = '-8px';
    delBtn.style.right = '-8px';
    delBtn.style.background = '#EF4444';
    delBtn.style.color = '#fff';
    delBtn.style.border = 'none';
    delBtn.style.borderRadius = '50%';
    delBtn.style.width = '20px';
    delBtn.style.height = '20px';
    delBtn.style.fontSize = '11px';
    delBtn.style.cursor = 'pointer';
    delBtn.style.display = 'flex';
    delBtn.style.alignItems = 'center';
    delBtn.style.justifyContent = 'center';
    delBtn.style.boxShadow = '0 2px 4px rgba(0,0,0,0.2)';
    delBtn.title = 'Xóa hình ảnh';
    delBtn.innerText = '✕';
    delBtn.addEventListener('click', () => removeImage(type));
    wrapper.appendChild(delBtn);

    container.innerHTML = '';
    container.appendChild(wrapper);
    
    const urlText = document.createElement('div');
    urlText.style.fontSize = '0.75rem';
    urlText.style.color = 'var(--pp-text-muted)';
    urlText.style.marginTop = '6px';
    urlText.style.wordBreak = 'break-all';
    urlText.innerText = url;
    container.appendChild(urlText);
  } else {
    container.style.display = 'none';
    container.innerHTML = '';
  }
}

export function renderGroupQuestions(sIdx, gIdx) {
  const wrapper = document.getElementById('grp-questions-list');
  if (!wrapper) return;
  wrapper.innerHTML = '';
  const grp = editorState.draft.sections[sIdx].groups[gIdx];

  if (!grp.questions || grp.questions.length === 0) {
    wrapper.innerHTML = `
      <div style="text-align:center; padding:24px; color:var(--pp-text-muted); font-size:0.85rem; border:1px dashed var(--pp-border); border-radius:12px;">
        Chưa có câu hỏi nào trong nhóm này.
      </div>
    `;
    return;
  }

  grp.questions.forEach((q, qIdx) => {
    const card = document.createElement('div');
    card.className = 'q-summary-card';
    card.innerHTML = `
      <div>
        <strong style="color:var(--pp-text);">Câu ${q.questionNo}</strong>
        <span style="margin-left:8px; font-size:0.78rem; padding:2px 8px; border-radius:6px; background:#f1f5f9; font-weight:600;">${q.questionType}</span>
        <p style="margin:4px 0 0 0; font-size:0.8rem; color:var(--pp-text-muted); width: 450px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${q.prompt || 'Chưa có tiêu đề câu hỏi'}</p>
      </div>
      <div style="display:flex; align-items:center; gap:6px;">
        <span style="font-size:0.8rem; font-weight:600; color:var(--pp-accent); margin-right:8px;">${q.points || 2.0}đ</span>
        <button class="tree-ellipsis-btn move-up-btn" title="Di chuyển lên">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m18 15-6-6-6 6"/></svg>
        </button>
        <button class="tree-ellipsis-btn move-down-btn" title="Di chuyển xuống">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 12 12 12-12"/></svg>
        </button>
        <button class="tree-ellipsis-btn delete-q-btn" style="color:var(--pp-error);" title="Xoá">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </div>
    `;
    
    // Wire up events dynamically
    card.addEventListener('click', () => selectNode('question', sIdx, gIdx, qIdx));
    
    const upBtn = card.querySelector('.move-up-btn');
    upBtn.addEventListener('click', (e) => moveQuestion(sIdx, gIdx, qIdx, -1, e));
    
    const downBtn = card.querySelector('.move-down-btn');
    downBtn.addEventListener('click', (e) => moveQuestion(sIdx, gIdx, qIdx, 1, e));
    
    const delBtn = card.querySelector('.delete-q-btn');
    delBtn.addEventListener('click', (e) => deleteQuestion(sIdx, gIdx, qIdx, e));

    wrapper.appendChild(card);
  });
}

export function handleAudioSelect(input) {
  if (input.files && input.files[0]) {
    uploadAudioFile(input.files[0]);
  }
}

export function handleImageSelect(input, type) {
  if (input.files && input.files[0]) {
    uploadImageFile(input.files[0], type);
  }
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

export function uploadImageFile(file, type) {
  const statusText = document.getElementById(type + '-image-upload-status');
  if (!statusText) return;
  statusText.innerText = "Đang tải ảnh lên...";
  statusText.style.display = 'block';
  statusText.style.color = 'var(--pp-text-muted)';

  const formData = new FormData();
  formData.append("file", file);

  const headers = csrfHeaders();

  fetch(`/practice/manage/drafts/${editorState.draftId}/upload-image`, {
    method: 'POST',
    headers: headers,
    body: formData
  })
  .then(res => {
    if (!res.ok) throw new Error("Lỗi tải ảnh lên máy chủ");
    return res.json();
  })
  .then(data => {
    statusText.innerText = "Tải lên ảnh thành công: " + data.filename;
    statusText.style.color = 'var(--pp-success)';

    const sIdx = editorState.currentNode.sIdx;
    const gIdx = editorState.currentNode.gIdx;
    if (type === 'group') {
      editorState.draft.sections[sIdx].groups[gIdx].imageUrl = data.url;
      updateImagePreview(data.url, 'group');
    } else if (type === 'question') {
      const qIdx = editorState.currentNode.qIdx;
      editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx].imageUrl = data.url;
      updateImagePreview(data.url, 'question');
    }
    triggerAutosave();
  })
  .catch(err => {
    statusText.innerText = "Lỗi: " + err.message;
    statusText.style.color = 'var(--pp-error)';
  });
}

export function uploadAudioFile(file) {
  const statusText = document.getElementById('audio-upload-status');
  if (!statusText) return;
  statusText.innerText = "Đang tải tệp âm thanh...";
  statusText.style.display = 'block';
  statusText.style.color = 'var(--pp-text-muted)';

  const formData = new FormData();
  formData.append("file", file);

  const headers = csrfHeaders();
  
  fetch(`/practice/manage/drafts/${editorState.draftId}/upload-audio`, {
    method: 'POST',
    headers: headers,
    body: formData
  })
  .then(res => {
    if (!res.ok) throw new Error("Lỗi tải lên máy chủ");
    return res.json();
  })
  .then(data => {
    statusText.innerText = "Tải lên tệp thành công: " + data.filename;
    statusText.style.color = 'var(--pp-success)';
    
    const sIdx = editorState.currentNode.sIdx;
    const gIdx = editorState.currentNode.gIdx;
    editorState.draft.sections[sIdx].groups[gIdx].audioUrl = data.url;
    document.getElementById('grp-audio').value = data.url;
    
    updateAudioPreview(data.url);
    triggerAutosave();
  })
  .catch(err => {
    statusText.innerText = "Lỗi: " + err.message;
    statusText.style.color = 'var(--pp-error)';
  });
}

export function removeImage(type) {
  if (!editorState.currentNode) return;
  const sIdx = editorState.currentNode.sIdx;
  const gIdx = editorState.currentNode.gIdx;
  if (type === 'group') {
    editorState.draft.sections[sIdx].groups[gIdx].imageUrl = '';
    updateImagePreview('', 'group');
  } else if (type === 'question') {
    const qIdx = editorState.currentNode.qIdx;
    editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx].imageUrl = '';
    updateImagePreview('', 'question');
  }
  const statusEl = document.getElementById(type + '-image-upload-status');
  if (statusEl) statusEl.style.display = 'none';
  triggerAutosave();
}

export function toggleAdvancedAudio(event) {
  if (event) event.preventDefault();
  const container = document.getElementById('grp-audio-url-container');
  if (container) container.style.display = container.style.display === 'block' ? 'none' : 'block';
}

export function closeAddMenu() {
  const menu = document.getElementById('add-structure-menu');
  const trigger = document.getElementById('add-structure-trigger');

  if (!menu) {
    return;
  }

  menu.hidden = true;
  menu.style.display = 'none';
  trigger?.setAttribute('aria-expanded', 'false');
}

export function toggleAddMenu(trigger) {
  const menu = document.getElementById('add-structure-menu');

  if (!menu || !trigger) {
    console.error('[PracticeEditor] Add menu or trigger not found');
    return;
  }

  const willOpen = menu.hidden || menu.style.display !== 'block';

  menu.hidden = !willOpen;
  menu.style.display = willOpen ? 'block' : 'none';
  trigger.setAttribute(
    'aria-expanded',
    willOpen ? 'true' : 'false'
  );

  if (willOpen) {
    syncAddMenuContextButtons();
  }
}

export function handleOutsideClick(event) {
  const menu = document.getElementById('add-structure-menu');
  const trigger = document.getElementById('add-structure-trigger');

  if (
    menu &&
    !menu.hidden &&
    !menu.contains(event.target) &&
    !trigger?.contains(event.target)
  ) {
    closeAddMenu();
  }
}

export function handleRootClick(event) {
  const trigger = event.target.closest(
    '[data-action="toggle-add-menu"]'
  );

  if (trigger) {
    event.preventDefault();
    event.stopPropagation();
    toggleAddMenu(trigger);
    return;
  }

  const button = event.target.closest(
    '#add-structure-menu [data-editor-action]'
  );

  if (!button) {
    return;
  }

  event.preventDefault();
  event.stopPropagation();

  if (button.disabled) {
    return;
  }

  const action = button.dataset.editorAction;

  console.info('[PracticeEditor] action:', action);
  console.info('[PracticeEditor] skill:', button.dataset.skill);

  switch (action) {
    case 'add-section':
      addSectionBySkill(button.dataset.skill);
      break;

    case 'add-group':
      addGroupWrapper();
      break;

    case 'add-question':
      addQuestionWrapper();
      break;

    default:
      console.warn(
        '[PracticeEditor] Unknown action:',
        action
      );
      return;
  }

  closeAddMenu();
}

export function setupDropzone() {
  const audioDropzone = document.getElementById('audio-dropzone');
  if (audioDropzone) {
    setupDropzoneEvents(audioDropzone, uploadAudioFile);
  }
  
  const groupImgDropzone = document.getElementById('group-image-dropzone');
  if (groupImgDropzone) {
    setupDropzoneEvents(groupImgDropzone, (file) => uploadImageFile(file, 'group'));
  }
  
  const questionImgDropzone = document.getElementById('question-image-dropzone');
  if (questionImgDropzone) {
    setupDropzoneEvents(questionImgDropzone, (file) => uploadImageFile(file, 'question'));
  }
}

function setupDropzoneEvents(dropzone, uploadFn) {
  ['dragenter', 'dragover'].forEach(name => {
    dropzone.addEventListener(name, (e) => {
      e.preventDefault();
      dropzone.classList.add('dragging');
    }, false);
  });

  ['dragleave', 'drop'].forEach(name => {
    dropzone.addEventListener(name, (e) => {
      e.preventDefault();
      dropzone.classList.remove('dragging');
    }, false);
  });

  dropzone.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files.length > 0) {
      uploadFn(files[0]);
    }
  });
}

let activePreviewGroupIndex = 0;
let previewGroups = [];

export function handleRenameActive(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { type, sIdx, gIdx, qIdx } = editorState.contextNode;
  if (type === 'section') startRenameSection(sIdx, event);
  else if (type === 'group') startRenameGroup(sIdx, gIdx, event);
  else if (type === 'question') startRenameQuestion(sIdx, gIdx, qIdx, event);
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleAddGroupFromMenu(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { sIdx } = editorState.contextNode;
  editorState.currentNode = { type: 'section', sIdx: sIdx };
  addGroupWrapper();
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleAddQuestionFromMenu(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { sIdx, gIdx } = editorState.contextNode;
  editorState.currentNode = { type: 'group', sIdx: sIdx, gIdx: gIdx };
  addQuestionWrapper();
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleMoveToGroupFromMenu(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { sIdx, gIdx, qIdx } = editorState.contextNode;
  
  const section = editorState.draft.sections[sIdx];
  const choices = section.groups.map((g, idx) => `${idx + 1}. ${getGroupName(g, idx)}`).join('\n');
  const ans = prompt(`Nhập số thứ tự nhóm muốn di chuyển câu hỏi này đến (1 đến ${section.groups.length}):\n\n${choices}`);
  if (!ans) return;
  const destIdx = parseInt(ans) - 1;
  if (isNaN(destIdx) || destIdx < 0 || destIdx >= section.groups.length) {
    alert("Lựa chọn không hợp lệ!");
    return;
  }
  if (destIdx === gIdx) return;
  
  const q = section.groups[gIdx].questions.splice(qIdx, 1)[0];
  if (!section.groups[destIdx].questions) section.groups[destIdx].questions = [];
  section.groups[destIdx].questions.push(q);
  
  reNumberSectionQuestions(sIdx);
  selectNode('question', sIdx, destIdx, section.groups[destIdx].questions.length - 1);
  triggerAutosave();
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleDuplicateActive(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { type, sIdx, gIdx, qIdx } = editorState.contextNode;
  if (type === 'section') {
    duplicateSection(sIdx);
  } else if (type === 'group') {
    duplicateGroup(sIdx, gIdx);
  } else if (type === 'question') {
    duplicateQuestion(sIdx, gIdx, qIdx);
  }
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleMoveActive(dir, event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { type, sIdx, gIdx, qIdx } = editorState.contextNode;
  if (type === 'section') {
    moveSection(sIdx, dir);
  } else if (type === 'group') {
    moveGroup(sIdx, gIdx, dir);
  } else if (type === 'question') {
    moveQuestion(sIdx, gIdx, qIdx, dir, event);
  }
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function handleDeleteActive(event) {
  if (event) event.preventDefault();
  if (!editorState.contextNode) return;
  const { type, sIdx, gIdx, qIdx } = editorState.contextNode;
  if (type === 'section') deleteSection(sIdx, event);
  else if (type === 'group') deleteGroup(sIdx, gIdx, event);
  else if (type === 'question') deleteQuestion(sIdx, gIdx, qIdx, event);
  const menu = document.getElementById('tree-context-menu');
  if (menu) menu.style.display = 'none';
}

export function toggleDotsDropdown(event) {
  if (event) event.stopPropagation();
  const menu = document.getElementById('dots-dropdown-menu');
  if (menu) {
    const isShowing = menu.style.display === 'block';
    menu.style.display = isShowing ? 'none' : 'block';
  }
}

export function confirmDeleteDraft(event) {
  if (event) event.preventDefault();
  if (confirm("Bạn có chắc chắn muốn xoá vĩnh viễn bản nháp đề thi này?")) {
    document.getElementById('delete-draft-form').submit();
  }
}

export function focusDraftTitle(event) {
  if (event) event.preventDefault();
  const titleInput = document.getElementById('draft-title');
  if (titleInput) {
    titleInput.focus();
    titleInput.select();
  }
  const menu = document.getElementById('dots-dropdown-menu');
  if (menu) menu.style.display = 'none';
}

function getCircledNumber(num) {
  const circles = ["⓪", "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"];
  return circles[num] || `(${num})`;
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

export function openPreviewModal() {
  const modal = document.getElementById('preview-modal');
  if (modal) modal.style.display = 'flex';

  previewGroups = [];
  let totalPoints = 0;
  if (editorState.draft.sections) {
    editorState.draft.sections.forEach((sec, sIdx) => {
      if (sec.groups) {
        sec.groups.forEach((grp, gIdx) => {
          let grpPoints = 0;
          if (grp.questions) {
            grp.questions.forEach(q => {
              grpPoints += parseFloat(q.points) || 0;
              totalPoints += parseFloat(q.points) || 0;
            });
          }
          previewGroups.push({
            secTitle: sec.title,
            skill: sec.skill,
            sIdx: sIdx,
            gIdx: gIdx,
            grp: grp,
            points: grpPoints
          });
        });
      }
    });
  }

  const titleEl = document.getElementById('preview-modal-title');
  if (titleEl) titleEl.innerText = `Xem trước: ${editorState.draft.document ? editorState.draft.document.title || 'Đề luyện tập' : 'Đề luyện tập'}`;
  
  const pointsEl = document.getElementById('preview-total-points-label');
  if (pointsEl) pointsEl.innerText = `Tổng điểm: ${totalPoints}đ`;

  const selector = document.getElementById('preview-group-selector');
  if (selector) {
    selector.innerHTML = '';
    if (previewGroups.length === 0) {
      selector.innerHTML = `<span style="font-size:0.85rem; color:#94A3B8; font-style:italic;">Bộ đề chưa có câu hỏi nào</span>`;
      document.getElementById('preview-group-instruction').innerHTML = '';
      document.getElementById('preview-group-passage').style.display = 'none';
      document.getElementById('preview-group-audio-container').style.display = 'none';
      document.getElementById('preview-group-image-container').style.display = 'none';
      document.getElementById('preview-questions-stack').innerHTML = '';
      return;
    }

    previewGroups.forEach((item, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `preview-grp-tab ${idx === activePreviewGroupIndex ? 'active' : ''}`;
      
      let labelText = item.grp.label || `Nhóm ${idx + 1}`;
      if (item.grp.questions && item.grp.questions.length > 0) {
        const nos = item.grp.questions.map(q => parseInt(q.questionNo) || 0).filter(n => n > 0);
        if (nos.length > 0) {
          const minNo = Math.min(...nos);
          const maxNo = Math.max(...nos);
          labelText = minNo === maxNo ? `Câu ${minNo}` : `Câu ${minNo}–${maxNo}`;
        }
      }
      
      btn.innerText = `${labelText} (${getSkillName(item.skill)})`;
      btn.onclick = () => {
        activePreviewGroupIndex = idx;
        document.querySelectorAll('.preview-grp-tab').forEach((b, bIdx) => {
          b.classList.toggle('active', bIdx === idx);
        });
        renderActivePreviewGroup();
      };
      selector.appendChild(btn);
    });
  }

  activePreviewGroupIndex = 0;
  renderActivePreviewGroup();
}

export function renderActivePreviewGroup() {
  if (previewGroups.length === 0 || activePreviewGroupIndex >= previewGroups.length) return;
  const item = previewGroups[activePreviewGroupIndex];
  const grp = item.grp;

  const instrEl = document.getElementById('preview-group-instruction');
  if (instrEl) {
    instrEl.innerHTML = `
      <div style="font-size:0.75rem; font-weight:700; color:#94A3B8; text-transform:uppercase; margin-bottom:4px;">HƯỚNG DẪN BÀI THI:</div>
      <div style="font-weight:600; color:#1E293B;">${escapeHtml(grp.instruction || 'Hãy đọc và trả lời câu hỏi.')}</div>
    `;
  }

  const audioContainer = document.getElementById('preview-group-audio-container');
  const audioPlayer = document.getElementById('preview-group-audio');
  if (audioContainer && audioPlayer) {
    if (item.skill === 'LISTENING' && grp.audioUrl) {
      audioContainer.style.display = 'block';
      audioPlayer.src = grp.audioUrl;
    } else {
      audioContainer.style.display = 'none';
      audioPlayer.src = '';
    }
  }

  const imgContainer = document.getElementById('preview-group-image-container');
  const imgEl = document.getElementById('preview-group-image');
  if (imgContainer && imgEl) {
    if (grp.imageUrl) {
      imgContainer.style.display = 'block';
      imgEl.src = grp.imageUrl;
    } else {
      imgContainer.style.display = 'none';
      imgEl.src = '';
    }
  }

  const passageContainer = document.getElementById('preview-group-passage');
  if (passageContainer) {
    if (grp.passageText && grp.passageText.trim().length > 0) {
      passageContainer.style.display = 'block';
      passageContainer.innerText = grp.passageText;
    } else {
      passageContainer.style.display = 'none';
    }
  }

  const questionsStack = document.getElementById('preview-questions-stack');
  if (questionsStack) {
    questionsStack.innerHTML = '';

    if (!grp.questions || grp.questions.length === 0) {
      questionsStack.innerHTML = `<div style="text-align:center; color:#94A3B8; padding:40px; font-style:italic;">Nhóm này chưa có câu hỏi nào.</div>`;
      return;
    }

    grp.questions.forEach((q, qIdx) => {
      const qCard = document.createElement('div');
      qCard.className = 'preview-q-card';
      
      let qImageHtml = '';
      if (q.imageUrl) {
        qImageHtml = `
          <div style="text-align:center; margin-bottom:12px;">
            <img src="${q.imageUrl}" style="max-height:180px; max-width:100%; border-radius:6px; border:1px solid #E2E8F0;" />
          </div>
        `;
      }

      let optionsHtml = '';
      if (q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTIPLE_CHOICE') {
        if (q.options) {
          q.options.forEach((opt, oIdx) => {
            const isCorrect = (q.answerKey === String(oIdx + 1) || (q.answer && q.answer.value === String(oIdx + 1)));
            optionsHtml += `
              <div class="preview-option ${isCorrect ? 'correct' : ''}">
                <div class="preview-option-letter">${getCircledNumber(oIdx + 1)}</div>
                <div style="flex:1;">${escapeHtml(opt.text || opt)}</div>
                ${isCorrect ? '<span style="font-weight:700; color:#10B981; font-size:0.8rem; margin-left:auto;">Đáp án đúng</span>' : ''}
              </div>
            `;
          });
        }
      } else if (q.questionType === 'TRUE_FALSE_NOT_GIVEN') {
        const val = q.answerKey || (q.answer && q.answer.value) || '';
        ['TRUE', 'FALSE', 'NOT_GIVEN'].forEach(tf => {
          const isCorrect = (val === tf);
          optionsHtml += `
            <div class="preview-option ${isCorrect ? 'correct' : ''}" style="display:inline-flex; width:30%; margin-right:2%;">
              <div class="preview-option-letter" style="background:${isCorrect ? '#10B981' : '#E2E8F0'}; color:${isCorrect ? '#fff' : '#475569'};">${tf[0]}</div>
              <span>${tf.replace('_', ' ')}</span>
            </div>
          `;
        });
      } else if (q.questionType === 'MATCHING') {
        optionsHtml += `
          <div style="padding:12px; background:#F8FAFC; border:1px dashed #CBD5E1; border-radius:8px; font-size:0.88rem; font-weight:600; color:#475569;">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:5px;"><path d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg> Đáp án ghép cặp đúng: <span style="color:#315EFB;">${escapeHtml(q.answerKey || 'Chưa định cấu hình')}</span>
          </div>
        `;
      } else if (q.questionType === 'GAP_FILL') {
        optionsHtml += `
          <div style="padding:12px; background:#F8FAFC; border:1px dashed #CBD5E1; border-radius:8px; font-size:0.88rem; font-weight:600; color:#475569;">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:5px;"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg> Từ/Cụm từ cần điền đúng: <span style="color:#315EFB;">${escapeHtml(q.answerKey || 'Chưa định cấu hình')}</span>
          </div>
        `;
      } else if (q.questionType === 'ESSAY') {
        optionsHtml += `
          <div style="display:flex; flex-direction:column; gap:10px;">
            <textarea disabled class="form-textarea" placeholder="Nơi học viên nhập bài viết bài luận..." style="height:80px; width:100%; border-radius:8px; box-sizing:border-box; background:#F8FAFC; cursor:not-allowed; resize:none;"></textarea>
            <div style="font-size:0.8rem; color:#475569; background:#EFF6FF; border-left:4px solid #315EFB; padding:10px; border-radius:4px;">
              <strong>Bài mẫu gợi ý:</strong><br>
              <span style="white-space:pre-wrap;">${escapeHtml(q.essaySample || 'Chưa thiết lập bài mẫu')}</span>
            </div>
          </div>
        `;
      } else if (q.questionType === 'SPEAKING') {
        optionsHtml += `
          <div style="display:flex; flex-direction:column; gap:10px;">
            <div style="display:flex; gap:12px; font-size:0.8rem; font-weight:600; color:#475569;">
              <span style="display:flex;align-items:center;gap:4px;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg> Chuẩn bị: <strong>${q.prepTimeSeconds || 0}s</strong></span>
              <span style="display:flex;align-items:center;gap:4px;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg> Thời gian nói: <strong>${q.respTimeSeconds || 0}s</strong></span>
            </div>
            <div style="font-size:0.8rem; color:#475569; background:#FFF5F5; border-left:4px solid #EF4444; padding:10px; border-radius:4px;">
              <strong>Bài phát biểu tham khảo:</strong><br>
              <span style="white-space:pre-wrap;">${escapeHtml(q.speakingSample || 'Chưa thiết lập mẫu nói')}</span>
            </div>
          </div>
        `;
      }

      qCard.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; border-bottom:1px solid #F1F5F9; padding-bottom:8px;">
          <h4 style="margin:0; font-size:0.95rem; font-weight:700; color:#315EFB;">Câu ${q.questionNo}</h4>
          <span style="font-size:0.75rem; font-weight:700; background:#EFF6FF; color:#315EFB; padding:2px 8px; border-radius:4px;">${q.points} điểm</span>
        </div>
        <p style="margin:0 0 12px 0; font-size:0.9rem; font-weight:600; color:#1E293B;">${escapeHtml(q.prompt || 'Chưa có tiêu đề câu hỏi')}</p>
        ${qImageHtml}
        <div>
          ${optionsHtml}
        </div>
        ${q.explanationVi ? `
          <div style="margin-top:14px; padding:10px; background:#F0FDF4; border:1px solid #DCFCE7; border-radius:8px; font-size:0.8rem; color:#166534;">
            <strong>Giải thích đáp án:</strong> ${escapeHtml(q.explanationVi)}
          </div>
        ` : ''}
      `;
      questionsStack.appendChild(qCard);
    });
  }
}

export function closePreviewModal() {
  const audioPlayer = document.getElementById('preview-group-audio');
  if (audioPlayer) audioPlayer.pause();
  const modal = document.getElementById('preview-modal');
  if (modal) modal.style.display = 'none';
}

// Custom Event Listeners to decouple tree rendering from action handling
window.addEventListener('practice-editor:set-correct-option', (e) => {
  setCorrectOption(e.detail.idx);
});
window.addEventListener('practice-editor:save-options', () => {
  saveOptions();
});
window.addEventListener('practice-editor:remove-option-row', (e) => {
  removeOptionRow(e.detail.idx);
});
window.addEventListener('practice-editor:remove-image', (e) => {
  removeImage(e.detail.type);
});
window.addEventListener('practice-editor:move-question', (e) => {
  moveQuestion(e.detail.sIdx, e.detail.gIdx, e.detail.qIdx, e.detail.dir, e);
});
window.addEventListener('practice-editor:delete-question', (e) => {
  deleteQuestion(e.detail.sIdx, e.detail.gIdx, e.detail.qIdx, e);
});
