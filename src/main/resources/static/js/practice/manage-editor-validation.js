import { editorState, normalizeDraftTree, WRITING_TASK_TYPES } from './manage-editor-state.js';

export function calculateDraftValidation(draftData) {
  draftData = normalizeDraftTree(draftData);
  const messages = [];
  let sectionCount = 0;
  let groupCount = 0;
  let questionCount = 0;
  let totalPoints = 0;

  if (!draftData || !Array.isArray(draftData.sections) || draftData.sections.length === 0) {
    messages.push({
      type: "BLOCKING",
      content: "Đề thi bắt buộc phải có ít nhất một Phần thi (Section).",
      sIdx: null,
      gIdx: null,
      qIdx: null
    });
  } else {
    sectionCount = draftData.sections.length;
    draftData.sections.forEach((sec, sIdx) => {
      const sTitle = sec.title || `Phần thi ${sIdx + 1}`;
      const skill = sec.skill || "";

      if (!skill) {
        messages.push({
          type: "BLOCKING",
          content: `Phần thi '${sTitle}' chưa được cấu hình kỹ năng (Reading/Listening...).`,
          sIdx: sIdx,
          gIdx: null,
          qIdx: null
        });
      }

      if (!sec.groups || !Array.isArray(sec.groups) || sec.groups.length === 0) {
        messages.push({
          type: "BLOCKING",
          content: `Phần thi '${sTitle}' trống rỗng, không chứa Nhóm câu hỏi nào.`,
          sIdx: sIdx,
          gIdx: null,
          qIdx: null
        });
      } else {
        groupCount += sec.groups.length;
        sec.groups.forEach((grp, gIdx) => {
          const gLabel = grp.label || `Nhóm ${gIdx + 1}`;
          
          if (!grp.questions || !Array.isArray(grp.questions) || grp.questions.length === 0) {
            messages.push({
              type: "BLOCKING",
              content: `Nhóm '${gLabel}' trong phần '${sTitle}' chưa có câu hỏi nào.`,
              sIdx: sIdx,
              gIdx: gIdx,
              qIdx: null
            });
          } else {
            questionCount += grp.questions.length;
            grp.questions.forEach((q, qIdx) => {
              const qNo = q.questionNo || (qIdx + 1);
              const type = q.questionType || "SINGLE_CHOICE";
              const prompt = q.prompt || "";
              const points = parseFloat(q.points) || 1.0;
              totalPoints += points;

              if (!prompt.trim()) {
                messages.push({
                  type: "WARNING",
                  content: `Câu hỏi số ${qNo} trong nhóm '${gLabel}' trống tiêu đề (prompt).`,
                  sIdx: sIdx,
                  gIdx: gIdx,
                  qIdx: qIdx
                });
              }

              const options = q.options || [];
              const answerVal = (q.answer && q.answer.value) || q.answerKey || "";

              if (type === "SINGLE_CHOICE" || type === "MULTIPLE_CHOICE" || type === "MCQ") {
                if (!Array.isArray(options) || options.length < 2) {
                  messages.push({
                    type: "BLOCKING",
                    content: `Câu hỏi trắc nghiệm số ${qNo} bắt buộc có ít nhất 2 đáp án lựa chọn.`,
                    sIdx: sIdx,
                    gIdx: gIdx,
                    qIdx: qIdx
                  });
                }
                if (!answerVal.trim()) {
                  messages.push({
                    type: "BLOCKING",
                    content: `Câu hỏi số ${qNo} chưa chọn đáp án đúng.`,
                    sIdx: sIdx,
                    gIdx: gIdx,
                    qIdx: qIdx
                  });
                }
              }

              if (skill === "WRITING" && type === "ESSAY") {
                if (!Object.prototype.hasOwnProperty.call(q, 'essayTaskType') || q.essayTaskType === null) {
                  messages.push({
                    type: "WARNING",
                    content: "Câu Writing này chưa có loại bài rõ ràng. Kết quả chấm có thể tiếp tục dùng cơ chế tương thích cũ.",
                    sIdx: sIdx,
                    gIdx: gIdx,
                    qIdx: qIdx
                  });
                } else if (typeof q.essayTaskType !== 'string' || q.essayTaskType.trim() !== q.essayTaskType || !WRITING_TASK_TYPES.includes(q.essayTaskType)) {
                  const isBlankTask = typeof q.essayTaskType === 'string' && q.essayTaskType.trim() === '';
                  messages.push({
                    type: "BLOCKING",
                    content: isBlankTask
                      ? "Vui lòng chọn loại bài Writing cho câu tự luận."
                      : "Loại bài Writing không hợp lệ.",
                    sIdx: sIdx,
                    gIdx: gIdx,
                    qIdx: qIdx
                  });
                }
              }

              const explanation = q.explanationVi || "";
              if (!explanation.trim()) {
                messages.push({
                  type: "WARNING",
                  content: `Câu hỏi số ${qNo} chưa có bài dịch hoặc giải thích tiếng Việt.`,
                  sIdx: sIdx,
                  gIdx: gIdx,
                  qIdx: qIdx
                });
              }
            });
          }
        });
      }
    });
  }

  const hasBlocking = messages.some(m => m.type === "BLOCKING");
  return {
    hasBlocking,
    messages,
    sectionCount,
    groupCount,
    questionCount,
    totalPoints
  };
}

export function validateDraft() {
  const validation = calculateDraftValidation(editorState.draft);
  editorState.latestValidation = validation;
  updateValidationPanel(validation);
  updatePublishEligibilityBanner();
  return validation;
}

export function updatePublishEligibilityBanner() {
  const banner = document.getElementById('publish-status-banner');
  if (!banner) return;
  
  if (editorState.latestValidation && !editorState.latestValidation.hasBlocking) {
    banner.style.display = 'none';
    banner.textContent = '';
  } else {
    const blockingCount = editorState.latestValidation && Array.isArray(editorState.latestValidation.messages)
      ? editorState.latestValidation.messages.filter(m => m.type === 'BLOCKING').length
      : 0;
    banner.style.display = 'flex';
    banner.style.background = '#FFF7ED';
    banner.style.color = '#C2410C';
    banner.style.border = '1px solid #FED7AA';
    banner.textContent = blockingCount > 0
      ? `Còn ${blockingCount} lỗi nghiêm trọng cần sửa trước khi xuất bản.`
      : 'Bộ đề cần được kiểm tra trước khi xuất bản.';
  }
}

export function updateValidationPanel(res) {
  if (!res) return;
  if (!Array.isArray(res.messages)) res.messages = [];

  document.getElementById('stat-sections').innerText = res.sectionCount;
  document.getElementById('stat-groups').innerText = res.groupCount;
  document.getElementById('stat-questions').innerText = res.questionCount;
  document.getElementById('stat-points').innerText = res.totalPoints + "đ";

  const totalSections = res.sectionCount;
  const totalErrors = res.messages.filter(m => m.type === 'BLOCKING').length;
  const progressFill = document.getElementById('progress-fill');
  const progressText = document.getElementById('progress-text');
  
  let percent = 0;
  if (totalSections > 0) {
    const issuesWeight = totalErrors * 15;
    percent = Math.max(10, Math.min(100, 100 - issuesWeight));
  }
  if (progressFill) progressFill.style.width = percent + "%";
  if (progressText) progressText.innerText = `${percent}% hoàn thành`;

  const vList = document.getElementById('validation-list');
  if (!vList) return;
  vList.innerHTML = '';

  const btnPublish = document.getElementById('btn-publish');
  if (btnPublish) {
    if (res.hasBlocking) {
      btnPublish.disabled = true;
      btnPublish.title = "Bộ đề còn lỗi nghiêm trọng cần khắc phục trước khi xuất bản.";
    } else {
      btnPublish.disabled = false;
      btnPublish.title = "";
    }
  }

  const validationFilter = editorState.validationFilter || 'ALL';
  const filtered = res.messages.filter(m => {
    if (validationFilter === 'ALL') return true;
    return m.type === validationFilter;
  });

  if (filtered.length === 0) {
    vList.innerHTML = `
      <div style="text-align:center; padding:24px; color:var(--pp-success); font-size:0.85rem; font-weight:700;">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="margin-right:6px;"><polyline points="20 6 9 17 4 12"/></svg> Đề thi hợp lệ! Sẵn sàng xuất bản.
      </div>
    `;
    return;
  }

  filtered.forEach(msg => {
    const card = document.createElement('div');
    card.className = `val-card ${msg.type.toLowerCase()}`;
    card.innerHTML = `
      <span class="val-card-icon">${msg.type === 'BLOCKING' ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#EF4444" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="2.5"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>'}</span>
      <div>
        <div style="font-weight:700;">${msg.type === 'BLOCKING' ? 'Lỗi nghiêm trọng' : 'Cảnh báo'}</div>
        <div>${msg.content}</div>
      </div>
    `;
    
    card.onclick = () => {
      if (msg.sIdx !== null && msg.sIdx !== undefined) {
        let type = 'section';
        if (msg.qIdx !== null && msg.qIdx !== undefined) type = 'question';
        else if (msg.gIdx !== null && msg.gIdx !== undefined) type = 'group';
        
        window.dispatchEvent(
          new CustomEvent('practice-editor:navigate', {
            detail: {
              type,
              sIdx: msg.sIdx,
              gIdx: msg.gIdx,
              qIdx: msg.qIdx
            }
          })
        );
      }
    };

    vList.appendChild(card);
  });
}
