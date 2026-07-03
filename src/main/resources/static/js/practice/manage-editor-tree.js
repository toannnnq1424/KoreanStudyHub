import { editorState, normalizeDraftTree } from './manage-editor-state.js';
import { updatePublishEligibilityBanner } from './manage-editor-validation.js';
import { triggerAutosave } from './manage-editor-autosave.js';

const FOLDER_SVG = `<svg class="lucide-ico folder-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z"/></svg>`;
const LISTTREE_SVG = `<svg class="lucide-ico group-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M21 12H7"/><path d="M21 6H7"/><path d="M21 18H7"/><path d="M3 6v12a2 2 0 0 0 2 2h2"/></svg>`;
const CIRCLEHELP_SVG = `<svg class="lucide-ico question-ico" viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`;
const CHEVRON_DOWN_SVG = `<svg class="lucide-ico chevron-ico" viewBox="0 0 24 24" width="14" height="14"><path d="m6 9 6 6 6-6"/></svg>`;
const CHEVRON_RIGHT_SVG = `<svg class="lucide-ico chevron-ico" viewBox="0 0 24 24" width="14" height="14"><path d="m9 18 6-6-6-6"/></svg>`;
const ELLIPSIS_SVG = `<svg class="lucide-ico ellipsis-ico" viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>`;

const LISTENING_SVG = `<svg class="lucide-ico skill-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z"/><path d="M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"/></svg>`;
const READING_SVG = `<svg class="lucide-ico skill-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>`;
const WRITING_SVG = `<svg class="lucide-ico skill-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>`;
const SPEAKING_SVG = `<svg class="lucide-ico skill-ico" viewBox="0 0 24 24" width="16" height="16"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>`;

const STATUS_VALID_SVG = `<svg class="status-ico valid" viewBox="0 0 24 24" width="14" height="14" stroke="#10B981" stroke-width="2.5" fill="none"><polyline points="20 6 9 17 4 12"/></svg>`;
const STATUS_WARNING_SVG = `<svg class="status-ico warning" viewBox="0 0 24 24" width="14" height="14" stroke="#F59E0B" stroke-width="2.5" fill="none"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`;
const STATUS_ERROR_SVG = `<svg class="status-ico error" viewBox="0 0 24 24" width="14" height="14" stroke="#EF4444" stroke-width="2.5" fill="none"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;
const STATUS_DRAFT_SVG = `<span class="status-dot draft" title="Nháp"></span>`;

export function getSkillSvg(skill) {
  switch (skill) {
    case 'READING': return READING_SVG;
    case 'LISTENING': return LISTENING_SVG;
    case 'WRITING': return WRITING_SVG;
    case 'SPEAKING': return SPEAKING_SVG;
    default: return FOLDER_SVG;
  }
}

export function getSkillName(skill) {
  switch (skill) {
    case 'READING': return 'Đọc';
    case 'LISTENING': return 'Nghe';
    case 'WRITING': return 'Viết';
    case 'SPEAKING': return 'Nói';
    default: return 'Tổng hợp';
  }
}

export function getQuestionStatusIcon(sIdx, gIdx, qIdx) {
  let hasError = false;
  let isWarning = false;
  let errorMsg = '';
  
  if (editorState.latestValidation && editorState.latestValidation.messages) {
    const err = editorState.latestValidation.messages.find(m => m.sIdx === sIdx && m.gIdx === gIdx && m.qIdx === qIdx && m.type === 'BLOCKING');
    if (err) {
      hasError = true;
      errorMsg = err.message;
    } else {
      const warn = editorState.latestValidation.messages.find(m => m.sIdx === sIdx && m.gIdx === gIdx && m.qIdx === qIdx && m.type === 'WARNING');
      if (warn) {
        isWarning = true;
        errorMsg = warn.message;
      }
    }
  }
  
  if (hasError) {
    return `<span class="validation-status-wrapper" title="${escapeHtml(errorMsg)}">${STATUS_ERROR_SVG}</span>`;
  }
  if (isWarning) {
    return `<span class="validation-status-wrapper" title="${escapeHtml(errorMsg)}">${STATUS_WARNING_SVG}</span>`;
  }
  
  const q = editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx] || {};
  if (q.prompt && q.prompt.trim() !== '' && q.points > 0) {
    return `<span class="validation-status-wrapper" title="Hợp lệ">${STATUS_VALID_SVG}</span>`;
  }
  
  return `<span class="validation-status-wrapper" title="Nháp chưa hoàn thành">${STATUS_DRAFT_SVG}</span>`;
}

export function toggleExpand(key, event) {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
    if (typeof event.stopImmediatePropagation === 'function') {
      event.stopImmediatePropagation();
    }
  }
  const currentlyExpanded = editorState.expandedNodes[key] !== false;
  editorState.expandedNodes[key] = !currentlyExpanded;
  try {
    renderTree();
  } catch (err) {
    console.error('[PracticeEditor] Toggle render failed:', err);
  }
}

export function getGroupName(grp, gIdx) {
  if (grp.questions && grp.questions.length > 0) {
    const nos = grp.questions.map(q => parseInt(q.questionNo) || 0).filter(no => no > 0);
    if (nos.length > 0) {
      const minNo = Math.min(...nos);
      const maxNo = Math.max(...nos);
      if (minNo === maxNo) return `Nhóm ${minNo}`;
      return `Nhóm ${minNo}–${maxNo}`;
    }
  }
  return grp.label || `Nhóm ${gIdx + 1}`;
}

export function openTreeContextMenu(type, sIdx, gIdx, qIdx, event) {
  event.stopPropagation();
  event.preventDefault();
  editorState.contextNode = { type, sIdx, gIdx, qIdx };
  
  const menu = document.getElementById('tree-context-menu');
  const rect = event.currentTarget.getBoundingClientRect();
  
  menu.style.left = `${rect.left - 130}px`;
  menu.style.top = `${rect.bottom + window.scrollY + 4}px`;
  menu.style.display = 'block';

  const addGrp = document.getElementById('context-add-group');
  const addQ = document.getElementById('context-add-question');
  const moveToGrp = document.getElementById('context-move-to-group');
  const delText = document.getElementById('context-delete-text');
  const moveUp = document.getElementById('context-move-up');
  const moveDown = document.getElementById('context-move-down');
  
  if (type === 'section') {
    addGrp.style.display = 'flex';
    addQ.style.display = 'none';
    moveToGrp.style.display = 'none';
    delText.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:8px;"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Xóa phần`;
    
    moveUp.style.display = sIdx === 0 ? 'none' : 'flex';
    moveDown.style.display = sIdx === editorState.draft.sections.length - 1 ? 'none' : 'flex';
  } else if (type === 'group') {
    addGrp.style.display = 'none';
    addQ.style.display = 'flex';
    moveToGrp.style.display = 'none';
    delText.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:8px;"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Xóa nhóm`;
    
    const grps = editorState.draft.sections[sIdx].groups;
    moveUp.style.display = gIdx === 0 ? 'none' : 'flex';
    moveDown.style.display = gIdx === grps.length - 1 ? 'none' : 'flex';
  } else if (type === 'question') {
    addGrp.style.display = 'none';
    addQ.style.display = 'none';
    moveToGrp.style.display = 'flex';
    delText.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:8px;"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> Xóa câu hỏi`;
    
    const qs = editorState.draft.sections[sIdx].groups[gIdx].questions;
    moveUp.style.display = qIdx === 0 ? 'none' : 'flex';
    moveDown.style.display = qIdx === qs.length - 1 ? 'none' : 'flex';
  }
}

export function renderTree() {
  const container = document.getElementById('tree-container');
  if (!container) return;

  editorState.draft = normalizeDraftTree(editorState.draft);
  const nextTree = document.createDocumentFragment();

  try {
    updatePublishEligibilityBanner();

    editorState.draft.sections.forEach((sec, sIdx) => {
    const secKey = `sec-${sIdx}`;
    if (editorState.expandedNodes[secKey] === undefined) editorState.expandedNodes[secKey] = true;
    const isSecExpanded = editorState.expandedNodes[secKey];
    
    const isSecActive = editorState.currentNode && editorState.currentNode.type === 'section' && editorState.currentNode.sIdx === sIdx;
    const isSecActivePath = editorState.currentNode && (
      (editorState.currentNode.type === 'section' && editorState.currentNode.sIdx === sIdx) ||
      (editorState.currentNode.type === 'group' && editorState.currentNode.sIdx === sIdx) ||
      (editorState.currentNode.type === 'question' && editorState.currentNode.sIdx === sIdx)
    );

    const grpCount = sec.groups ? sec.groups.length : 0;
    let qCount = 0;
    if (sec.groups) {
      sec.groups.forEach(g => { if (g.questions) qCount += g.questions.length; });
    }

    const sectionWrapper = document.createElement('div');
    sectionWrapper.className = 'section-container';

    const secRow = document.createElement('div');
    secRow.className = `tree-row section-row ${isSecActive ? 'active' : ''} ${isSecActivePath ? 'active-path' : ''}`;
    
    const contentEl = document.createElement('div');
    contentEl.className = 'tree-row-content';
    contentEl.addEventListener('click', () => selectNode('section', sIdx));
    
    const chevronEl = document.createElement('div');
    chevronEl.className = `tree-row-chevron ${isSecExpanded ? '' : 'collapsed'}`;
    chevronEl.addEventListener('click', (e) => toggleExpand(secKey, e));
    chevronEl.innerHTML = isSecExpanded ? CHEVRON_DOWN_SVG : CHEVRON_RIGHT_SVG;
    
    contentEl.appendChild(chevronEl);
    
    const iconSpan = document.createElement('span');
    iconSpan.className = 'tree-icon-wrapper';
    iconSpan.innerHTML = getSkillSvg(sec.skill);
    contentEl.appendChild(iconSpan);
    
    const textDiv = document.createElement('div');
    textDiv.style.display = 'flex';
    textDiv.style.flexDirection = 'column';
    textDiv.style.minWidth = '0';
    textDiv.style.marginLeft = '4px';
    textDiv.innerHTML = `
      <span class="tree-text-title">${escapeHtml(sec.title)}</span>
      <span class="tree-meta-text">${grpCount} nhóm · ${qCount} câu</span>
    `;
    contentEl.appendChild(textDiv);
    
    secRow.appendChild(contentEl);

    const actionsDiv = document.createElement('div');
    actionsDiv.className = 'tree-actions';
    const actionBtn = document.createElement('button');
    actionBtn.className = 'tree-ellipsis-btn';
    actionBtn.title = 'Hành động';
    actionBtn.innerHTML = ELLIPSIS_SVG;
    actionBtn.addEventListener('click', (e) => openTreeContextMenu('section', sIdx, null, null, e));
    actionsDiv.appendChild(actionBtn);
    secRow.appendChild(actionsDiv);
    
    sectionWrapper.appendChild(secRow);

    if (isSecExpanded && sec.groups && sec.groups.length > 0) {
      const sectionChildren = document.createElement('div');
      sectionChildren.className = 'section-children-container';

      sec.groups.forEach((grp, gIdx) => {
        const grpKey = `grp-${sIdx}-${gIdx}`;
        if (editorState.expandedNodes[grpKey] === undefined) editorState.expandedNodes[grpKey] = true;
        const isGrpExpanded = editorState.expandedNodes[grpKey];
        
        const isGrpActive = editorState.currentNode && editorState.currentNode.type === 'group' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx;
        const isGrpActivePath = editorState.currentNode && (
          (editorState.currentNode.type === 'group' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx) ||
          (editorState.currentNode.type === 'question' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx)
        );
        
        const gqCount = grp.questions ? grp.questions.length : 0;
        let grpPoints = 0;
        if (grp.questions) {
          grp.questions.forEach(q => { grpPoints += parseFloat(q.points) || 0; });
        }
        const groupName = getGroupName(grp, gIdx);

        const groupWrapper = document.createElement('div');
        groupWrapper.className = 'group-container';

        const grpRow = document.createElement('div');
        grpRow.className = `tree-row group-row ${isGrpActive ? 'active' : ''} ${isGrpActivePath ? 'active-path' : ''}`;
        
        const grpContent = document.createElement('div');
        grpContent.className = 'tree-row-content';
        grpContent.addEventListener('click', () => selectNode('group', sIdx, gIdx));
        
        const grpChevron = document.createElement('div');
        grpChevron.className = `tree-row-chevron ${isGrpExpanded ? '' : 'collapsed'}`;
        grpChevron.addEventListener('click', (e) => toggleExpand(grpKey, e));
        grpChevron.innerHTML = isGrpExpanded ? CHEVRON_DOWN_SVG : CHEVRON_RIGHT_SVG;
        grpContent.appendChild(grpChevron);
        
        const grpIcon = document.createElement('span');
        grpIcon.className = 'tree-icon-wrapper';
        grpIcon.innerHTML = LISTTREE_SVG;
        grpContent.appendChild(grpIcon);
        
        const grpText = document.createElement('div');
        grpText.style.display = 'flex';
        grpText.style.flexDirection = 'column';
        grpText.style.minWidth = '0';
        grpText.style.marginLeft = '4px';
        grpText.innerHTML = `
          <span class="tree-text-title">${escapeHtml(groupName)}</span>
          <span class="tree-meta-text">${gqCount} câu · ${grpPoints}đ</span>
        `;
        grpContent.appendChild(grpText);
        grpRow.appendChild(grpContent);

        const grpActions = document.createElement('div');
        grpActions.className = 'tree-actions';
        const grpActionBtn = document.createElement('button');
        grpActionBtn.className = 'tree-ellipsis-btn';
        grpActionBtn.title = 'Hành động';
        grpActionBtn.innerHTML = ELLIPSIS_SVG;
        grpActionBtn.addEventListener('click', (e) => openTreeContextMenu('group', sIdx, gIdx, null, e));
        grpActions.appendChild(grpActionBtn);
        grpRow.appendChild(grpActions);
        
        groupWrapper.appendChild(grpRow);

        if (isGrpExpanded && grp.questions && grp.questions.length > 0) {
          const groupChildren = document.createElement('div');
          groupChildren.className = 'group-children-container';

          grp.questions.forEach((q, qIdx) => {
            const isQActive = editorState.currentNode && editorState.currentNode.type === 'question' && editorState.currentNode.sIdx === sIdx && editorState.currentNode.gIdx === gIdx && editorState.currentNode.qIdx === qIdx;

            const qRow = document.createElement('div');
            qRow.className = `tree-row question-row ${isQActive ? 'active' : ''}`;
            
            const qContent = document.createElement('div');
            qContent.className = 'tree-row-content';
            qContent.addEventListener('click', () => selectNode('question', sIdx, gIdx, qIdx));
            qContent.innerHTML = `
              <div class="question-badge">${q.questionNo}</div>
              <span class="tree-text-title" style="margin-left:4px;">Câu hỏi</span>
              ${getQuestionStatusIcon(sIdx, gIdx, qIdx)}
            `;
            qRow.appendChild(qContent);

            const qActions = document.createElement('div');
            qActions.className = 'tree-actions';
            const qActionBtn = document.createElement('button');
            qActionBtn.className = 'tree-ellipsis-btn';
            qActionBtn.title = 'Hành động';
            qActionBtn.innerHTML = ELLIPSIS_SVG;
            qActionBtn.addEventListener('click', (e) => openTreeContextMenu('question', sIdx, gIdx, qIdx, e));
            qActions.appendChild(qActionBtn);
            qRow.appendChild(qActions);
            
            groupChildren.appendChild(qRow);
          });
          groupWrapper.appendChild(groupChildren);
        }
        sectionChildren.appendChild(groupWrapper);
      });
      sectionWrapper.appendChild(sectionChildren);
    }
      nextTree.appendChild(sectionWrapper);
    });
    container.replaceChildren(nextTree);
    updateDraftSkillDisplay();
  } catch (err) {
    console.error('[PracticeEditor] Tree render failed:', err);
    if (container.children.length === 0) {
      container.innerHTML = `
        <div style="margin:12px; padding:14px; border:1px solid #FCA5A5; border-radius:12px; background:#FFF1F2; color:#BE123C; font-size:0.84rem; font-weight:700; line-height:1.5;">
          Không thể dựng cây cấu trúc. Hãy kiểm tra dữ liệu nhóm/câu hỏi trong bản nháp.
        </div>
      `;
    }
  }
}

export function selectNode(type, sIdx = null, gIdx = null, qIdx = null) {
  editorState.currentNode = { type, sIdx, gIdx, qIdx };
  renderTree();
  window.dispatchEvent(new CustomEvent('practice-editor:node-selected'));

  // Hide all editor panels
  document.getElementById('editor-placeholder').style.display = 'none';
  document.getElementById('editor-draft-card').style.display = 'none';
  document.getElementById('editor-section-card').style.display = 'none';
  document.getElementById('editor-group-card').style.display = 'none';
  document.getElementById('editor-question-card').style.display = 'none';

  if (type === 'draft') {
    document.getElementById('editor-draft-card').style.display = 'block';
    document.getElementById('draft-category').value = (editorState.draft.document && editorState.draft.document.detectedCategory) ? editorState.draft.document.detectedCategory : 'UNCLASSIFIED';
    document.getElementById('draft-description').value = editorState.draft.document ? editorState.draft.document.description || '' : '';
    updateDraftSkillDisplay();
  } else if (type === 'section') {
    const sec = editorState.draft.sections[sIdx];
    document.getElementById('editor-section-card').style.display = 'block';
    document.getElementById('sec-title').value = sec.title;
    document.getElementById('sec-skill').value = sec.skill || 'READING';
    document.getElementById('sec-duration').value = sec.durationMinutes || 40;
  } else if (type === 'group') {
    const grp = editorState.draft.sections[sIdx].groups[gIdx];
    document.getElementById('editor-group-card').style.display = 'block';
    document.getElementById('grp-label').value = grp.label;
    document.getElementById('grp-audio').value = grp.audioUrl || '';
    document.getElementById('grp-passage').value = grp.passageText || '';
    document.getElementById('grp-meta-info').innerText = `Phần: ${getSkillName(editorState.draft.sections[sIdx].skill)} · ${grp.questions ? grp.questions.length : 0} câu`;
    
    // Select segmented tab
    if (!grp.passageText) {
      setGroupTab('none');
    } else {
      setGroupTab('passage');
    }
    
    // Audio preview player
    updateAudioPreview(grp.audioUrl);
    
    // Image preview
    updateImagePreview(grp.imageUrl || '', 'group');
    document.getElementById('group-image-upload-status').style.display = 'none';
    
    // Render questions list
    renderGroupQuestions(sIdx, gIdx);
  } else if (type === 'question') {
    const q = editorState.draft.sections[sIdx].groups[gIdx].questions[qIdx];
    document.getElementById('editor-question-card').style.display = 'block';
    document.getElementById('q-eyebrow').innerText = `Câu hỏi số ${q.questionNo} · ${editorState.draft.sections[sIdx].title}`;
    document.getElementById('q-no').value = q.questionNo;
    document.getElementById('q-type').value = q.questionType;
    document.getElementById('q-prompt').value = q.prompt || '';
    document.getElementById('q-points').value = q.points || 2.0;
    document.getElementById('q-explanation').value = q.explanationVi || '';
    
    // Image preview
    updateImagePreview(q.imageUrl || '', 'question');
    document.getElementById('question-image-upload-status').style.display = 'none';
    
    // Load specific fields
    handleQuestionTypeChange();
  }
}

// Helper to set group tab
function setGroupTab(tab) {
  document.querySelectorAll('.segmented-tab').forEach(el => el.classList.remove('active'));
  const tabEl = document.getElementById('tab-' + tab);
  if (tabEl) tabEl.classList.add('active');

  const passageArea = document.getElementById('grp-passage-area');
  if (passageArea) {
    if (tab === 'none') {
      passageArea.style.display = 'none';
    } else {
      passageArea.style.display = 'block';
      const label = document.getElementById('grp-passage-label');
      if (label) label.innerText = tab === 'passage' ? "Văn bản đọc hiểu" : "Lời thoại nghe (Transcript)";
    }
  }
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
    delBtn.addEventListener('click', () => {
      window.dispatchEvent(new CustomEvent('practice-editor:remove-image', {
        detail: { type }
      }));
    });
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
    
    card.addEventListener('click', () => selectNode('question', sIdx, gIdx, qIdx));
    
    const upBtn = card.querySelector('.move-up-btn');
    upBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      window.dispatchEvent(new CustomEvent('practice-editor:move-question', {
        detail: { sIdx, gIdx, qIdx, dir: -1 }
      }));
    });
    
    const downBtn = card.querySelector('.move-down-btn');
    downBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      window.dispatchEvent(new CustomEvent('practice-editor:move-question', {
        detail: { sIdx, gIdx, qIdx, dir: 1 }
      }));
    });
    
    const delBtn = card.querySelector('.delete-q-btn');
    delBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      window.dispatchEvent(new CustomEvent('practice-editor:delete-question', {
        detail: { sIdx, gIdx, qIdx }
      }));
    });

    wrapper.appendChild(card);
  });
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
    const isCorrect = (q.answer && (q.answer.value === String(idx + 1) || q.answerKey === String(idx + 1)));

    const card = document.createElement('div');
    card.className = 'opt-card';
    
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
    checkbox.addEventListener('change', () => {
      window.dispatchEvent(new CustomEvent('practice-editor:set-correct-option', {
        detail: { idx }
      }));
    });
    container.appendChild(checkbox);

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'opt-text-input';
    input.value = val;
    input.addEventListener('input', () => {
      window.dispatchEvent(new CustomEvent('practice-editor:save-options'));
    });
    container.appendChild(input);

    const delBtn = document.createElement('button');
    delBtn.className = 'opt-delete-btn';
    delBtn.innerText = '✕';
    delBtn.addEventListener('click', () => {
      window.dispatchEvent(new CustomEvent('practice-editor:remove-option-row', {
        detail: { idx }
      }));
    });
    container.appendChild(delBtn);

    card.appendChild(container);
    list.appendChild(card);
  });
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

export function updateDraftSkillDisplay() {
  const displayEl = document.getElementById('draft-skill-display');
  if (!displayEl) return;
  
  const uniqueSkills = new Set();
  if (editorState.draft && editorState.draft.sections) {
    editorState.draft.sections.forEach(sec => {
      if (sec.skill) uniqueSkills.add(sec.skill);
    });
  }
  
  let targetSkillLabel = "Chưa xác định";
  if (uniqueSkills.size === 1) {
    const sk = uniqueSkills.values().next().value;
    targetSkillLabel = sk === 'READING' ? 'Đọc' :
                        sk === 'LISTENING' ? 'Nghe' :
                        sk === 'WRITING' ? 'Viết' : 'Nói';
  } else if (uniqueSkills.size > 1) {
    targetSkillLabel = "Tổng hợp (MIXED)";
  }
  
  displayEl.textContent = targetSkillLabel;
}

