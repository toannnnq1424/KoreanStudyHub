const root = document.querySelector('[data-practice-editor-root]');
const bootstrapElement = document.getElementById('practice-editor-data');

export const editorState = {
  draftId: Number(root?.dataset?.draftId || 0),
  version: Number(root?.dataset?.draftVersion || 0),
  draft: JSON.parse(bootstrapElement?.textContent || '{}'),
  currentNode: null,
  latestValidation: null,
  validationFilter: 'ALL',
  expandedNodes: { "sec-0": true }
};

export function makeClientId(prefix = 'node') {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID();
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function defaultQuestionTypeForSkill(skill) {
  if (skill === 'WRITING') return 'ESSAY';
  if (skill === 'SPEAKING') return 'SPEAKING';
  return 'SINGLE_CHOICE';
}

export function defaultOptionsForQuestionType(type) {
  if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE' || type === 'MCQ') {
    return ["Phương án A", "Phương án B", "Phương án C", "Phương án D"];
  }
  return [];
}

export function normalizeDraftTree(draft) {
  if (!draft || typeof draft !== 'object') draft = {};
  if (!draft.document || typeof draft.document !== 'object') draft.document = {};
  if (!Array.isArray(draft.sections)) draft.sections = [];

  draft.sections = Array.from(draft.sections).map((section, sIdx) => {
    const sec = section && typeof section === 'object' ? section : {};
    sec.clientId = sec.clientId || makeClientId('sec');
    sec.title = sec.title || `Phần thi ${sIdx + 1}`;
    sec.skill = ['READING', 'LISTENING', 'WRITING', 'SPEAKING'].includes(sec.skill) ? sec.skill : 'READING';
    sec.durationMinutes = parseInt(sec.durationMinutes, 10) || (sec.skill === 'WRITING' ? 50 : 40);
    sec.groups = Array.isArray(sec.groups) ? Array.from(sec.groups) : [];
    let questionCounter = 1;

    sec.groups = sec.groups.map((group, gIdx) => {
      const grp = group && typeof group === 'object' ? group : {};
      grp.clientId = grp.clientId || makeClientId('grp');
      grp.label = grp.label || `Nhóm ${gIdx + 1}`;
      grp.instruction = grp.instruction || '';
      grp.passageText = grp.passageText || '';
      grp.audioUrl = grp.audioUrl || '';
      grp.imageUrl = grp.imageUrl || '';
      grp.questions = Array.isArray(grp.questions) ? Array.from(grp.questions) : [];

      grp.questions = grp.questions.map((question, qIdx) => {
        const q = question && typeof question === 'object' ? question : {};
        q.clientId = q.clientId || makeClientId('q');
        q.questionNo = questionCounter++;
        q.questionType = q.questionType || defaultQuestionTypeForSkill(sec.skill);
        q.prompt = q.prompt || '';
        q.explanationVi = q.explanationVi || '';
        q.points = parseFloat(q.points) || 2.0;
        q.answerKey = q.answerKey || '';
        q.options = Array.isArray(q.options) ? Array.from(q.options) : defaultOptionsForQuestionType(q.questionType);
        if (!q.answer && q.answerKey) {
          q.answer = { type: q.questionType === 'MULTIPLE_CHOICE' ? 'MULTIPLE' : 'SINGLE', value: q.answerKey };
        }
        return q;
      });

      updateGroupQuestionRange(grp);
      return grp;
    });

    return sec;
  });

  return draft;
}

editorState.draft = normalizeDraftTree(editorState.draft);

export function updateGroupQuestionRange(grp) {
  if (!grp.questions || grp.questions.length === 0) {
    grp.questionFrom = null;
    grp.questionTo = null;
    return;
  }
  let minNo = Infinity;
  let maxNo = -Infinity;
  grp.questions.forEach(q => {
    const no = parseInt(q.questionNo, 10);
    if (!isNaN(no)) {
      if (no < minNo) minNo = no;
      if (no > maxNo) maxNo = no;
    }
  });
  if (minNo === Infinity) {
    grp.questionFrom = null;
    grp.questionTo = null;
  } else {
    grp.questionFrom = minNo;
    grp.questionTo = maxNo;
  }
}
