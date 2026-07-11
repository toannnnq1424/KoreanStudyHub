(function () {
  'use strict';

  const CONTENT_SCHEMA = 'question-content-v1';
  const ANSWER_SCHEMA = 'answer-spec-v1';

  function template(catalog, code) {
    const templates = catalog && Array.isArray(catalog.templates) ? catalog.templates : [];
    return templates.find(item => item.code === code) || templates[0] || null;
  }

  function currentTemplate(catalog, draft) {
    const code = draft && draft.document ? draft.document.examTemplateCode : null;
    return template(catalog, code);
  }

  function allowedSkills(catalog, draft) {
    const selected = currentTemplate(catalog, draft);
    return selected && selected.skills ? Object.keys(selected.skills) : [];
  }

  function skillPolicy(catalog, draft, skill) {
    const selected = currentTemplate(catalog, draft);
    return selected && selected.skills ? selected.skills[skill] || null : null;
  }

  function questionPolicy(catalog, draft, skill, questionType) {
    const policy = skillPolicy(catalog, draft, skill);
    return policy && policy.questionPolicies ? policy.questionPolicies[questionType] || null : null;
  }

  function normalizeOption(option, makeId) {
    if (option && typeof option === 'object') {
      return {
        id: option.id || makeId('opt'),
        text: option.text || '',
        imageReference: option.imageReference || option.imageUrl || ''
      };
    }
    return { id: makeId('opt'), text: option == null ? '' : String(option), imageReference: '' };
  }

  function normalizeQuestion(question, makeId) {
    const q = question && typeof question === 'object' ? question : {};
    if (q.questionType === 'GAP_FILL') q.questionType = 'FILL_BLANK';
    if (q.questionType === 'MCQ') q.questionType = 'SINGLE_CHOICE';
    q.options = Array.isArray(q.options) ? q.options.map(option => normalizeOption(option, makeId)) : [];

    if (q.questionType === 'FILL_BLANK' && (!Array.isArray(q.fillBlanks) || q.fillBlanks.length === 0)) {
      const legacy = String((q.answer && q.answer.value) || q.answerKey || '').trim();
      q.fillBlanks = [{
        id: makeId('blank'),
        prompt: '',
        acceptedValues: legacy ? [legacy] : []
      }];
    }
    if (q.questionType === 'MATCHING' && !Array.isArray(q.matchingPairs)) {
      q.matchingPairs = [];
    }
    syncQuestionContract(q);
    return q;
  }

  function syncQuestionContract(q) {
    const type = q.questionType || 'SINGLE_CHOICE';
    const previousSpec = q.answerSpec && typeof q.answerSpec === 'object' ? q.answerSpec : {};
    const content = {
      schemaVersion: CONTENT_SCHEMA,
      options: [],
      matchingLeftItems: [],
      matchingRightItems: [],
      blanks: []
    };
    const answer = {
      schemaVersion: ANSWER_SCHEMA,
      questionType: type,
      correctOptionIds: [],
      correctValue: null,
      blanks: [],
      matchingPairs: {},
      scoringPolicyCode: q.scoringPolicyCode || previousSpec.scoringPolicyCode || scoringPolicy(type),
      scoringProfileCode: q.scoringProfileCode || previousSpec.scoringProfileCode || null,
      promptProfileCode: q.promptProfileCode || previousSpec.promptProfileCode || null,
      rubricProfileCode: q.rubricProfileCode || previousSpec.rubricProfileCode || null,
      scoringProfileVersion: q.scoringProfileVersion || previousSpec.scoringProfileVersion || null,
      promptProfileVersion: q.promptProfileVersion || previousSpec.promptProfileVersion || null,
      rubricProfileVersion: q.rubricProfileVersion || previousSpec.rubricProfileVersion || null
    };

    if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
      content.options = (q.options || []).map(option => ({
        id: option.id,
        text: option.text || '',
        imageReference: option.imageReference || null
      }));
      const selectedIndexes = String((q.answer && q.answer.value) || q.answerKey || '')
        .split(',')
        .map(value => Number.parseInt(value.trim(), 10) - 1)
        .filter(index => Number.isInteger(index) && index >= 0 && index < content.options.length);
      answer.correctOptionIds = selectedIndexes.map(index => content.options[index].id);
    } else if (type === 'TRUE_FALSE_NOT_GIVEN') {
      answer.correctValue = String((q.answer && q.answer.value) || q.answerKey || '').trim() || null;
    } else if (type === 'FILL_BLANK') {
      const blanks = Array.isArray(q.fillBlanks) ? q.fillBlanks : [];
      content.blanks = blanks.map(blank => ({ id: blank.id, prompt: blank.prompt || '' }));
      answer.blanks = blanks.map(blank => ({
        blankId: blank.id,
        acceptedValues: Array.from(new Set((blank.acceptedValues || []).map(value => String(value).trim()).filter(Boolean)))
      }));
      const firstValue = answer.blanks[0] && answer.blanks[0].acceptedValues[0] || '';
      q.answer = { type: 'FILL', value: firstValue };
      q.answerKey = firstValue;
    } else if (type === 'MATCHING') {
      const pairs = Array.isArray(q.matchingPairs) ? q.matchingPairs : [];
      content.matchingLeftItems = pairs.map(pair => ({ id: pair.leftId, text: pair.leftText || '' }));
      const rightById = new Map();
      pairs.forEach(pair => rightById.set(pair.rightId, { id: pair.rightId, text: pair.rightText || '' }));
      content.matchingRightItems = Array.from(rightById.values());
      pairs.forEach(pair => { answer.matchingPairs[pair.leftId] = pair.rightId; });
      q.answerKey = pairs.map((pair, index) => `${index + 1}-${index + 1}`).join(',');
    }

    content.imageReference = q.imageUrl || content.imageReference || null;
    content.audioReference = q.audioUrl || content.audioReference || null;
    q.canonicalQuestionType = type;
    q.questionContent = content;
    q.answerSpec = answer;
    return q;
  }

  function scoringPolicy(type) {
    if (type === 'FILL_BLANK') return 'NORMALIZED_EXACT';
    if (type === 'MATCHING') return 'PER_PAIR';
    if (type === 'ESSAY' || type === 'SPEAKING') return 'PROFILE_BASED';
    return 'ALL_OR_NOTHING';
  }

  function applyTemplateMetadata(catalog, draft, templateCode) {
    const selected = template(catalog, templateCode);
    if (!selected) return null;
    if (!draft.document) draft.document = {};
    draft.schemaVersion = 'practice-draft-v3';
    draft.document.examTemplateCode = selected.code;
    draft.document.detectedCategory = selected.categoryCode;
    draft.document.assessmentProgramCode = selected.programCode;
    draft.document.assessmentProgramVersionId = selected.programVersionId;
    draft.document.assessmentProgramVersion = selected.programVersion;
    return selected;
  }

  window.PracticeAuthoringContract = {
    template,
    currentTemplate,
    allowedSkills,
    skillPolicy,
    questionPolicy,
    normalizeQuestion,
    syncQuestionContract,
    applyTemplateMetadata
  };
})();
