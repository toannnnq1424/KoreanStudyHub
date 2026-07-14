(function () {
  'use strict';

  const CONTENT_SCHEMA = 'question-content-v1';
  const ANSWER_SCHEMA = 'answer-spec-v1';

  function template(catalog, code) {
    const templates = catalog && Array.isArray(catalog.templates) ? catalog.templates : [];
    return templates.find(item => item.code === code) || templates[0] || null;
  }

  function currentTemplate(catalog, draft) {
    return template(catalog, null);
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

    const delivery = q.questionContent && q.questionContent.speakingDelivery;
    if (delivery && typeof delivery === 'object') {
      q.speakingPromptAudioUrl = delivery.promptAudioReference || q.speakingPromptAudioUrl || q.audioUrl || '';
      q.speakingPromptPlayLimit = positiveInteger(delivery.promptPlayLimit, 1);
      q.prepTimeSeconds = nonNegativeInteger(delivery.preparationSeconds, 30);
      q.respTimeSeconds = positiveInteger(delivery.responseSeconds, 60);
    }

    if (q.questionType === 'FILL_BLANK' && (!Array.isArray(q.fillBlanks) || q.fillBlanks.length === 0)) {
      const legacy = String((q.answer && q.answer.value) || q.answerKey || '').trim();
      q.fillBlanks = [{
        id: makeId('blank'),
        prompt: '',
        acceptedValues: legacy ? [legacy] : []
      }];
    }
    syncQuestionContract(q);
    return q;
  }

  function syncQuestionContract(q) {
    const type = q.questionType || 'SINGLE_CHOICE';
    const previousSpec = q.answerSpec && typeof q.answerSpec === 'object' ? q.answerSpec : {};
    const previousContent = q.questionContent && typeof q.questionContent === 'object' ? q.questionContent : {};
    const previousDelivery = previousContent.speakingDelivery && typeof previousContent.speakingDelivery === 'object'
      ? previousContent.speakingDelivery
      : {};
    const content = {
      schemaVersion: CONTENT_SCHEMA,
      options: [],
      blanks: []
    };
    const answer = {
      schemaVersion: ANSWER_SCHEMA,
      questionType: type,
      correctOptionIds: [],
      correctValue: null,
      blanks: [],
      scoringPolicyCode: scoringPolicy(type)
    };

    if (type === 'SINGLE_CHOICE') {
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
    }

    content.imageReference = q.imageUrl || content.imageReference || null;
    content.audioReference = q.audioUrl || content.audioReference || null;
    if (type === 'SPEAKING') {
      const promptAudioReference = q.speakingPromptAudioUrl
        || previousDelivery.promptAudioReference
        || q.audioUrl
        || null;
      content.speakingDelivery = {
        promptAudioReference,
        promptPlayLimit: positiveInteger(q.speakingPromptPlayLimit || previousDelivery.promptPlayLimit, 1),
        preparationSeconds: nonNegativeInteger(q.prepTimeSeconds ?? previousDelivery.preparationSeconds, 30),
        responseSeconds: positiveInteger(q.respTimeSeconds || previousDelivery.responseSeconds, 60)
      };
      q.speakingPromptAudioUrl = promptAudioReference || '';
      q.speakingPromptPlayLimit = content.speakingDelivery.promptPlayLimit;
      q.prepTimeSeconds = content.speakingDelivery.preparationSeconds;
      q.respTimeSeconds = content.speakingDelivery.responseSeconds;
    }
    delete q.canonicalQuestionType;
    q.questionContent = content;
    q.answerSpec = answer;
    return q;
  }

  function scoringPolicy(type) {
    if (type === 'FILL_BLANK') return 'NORMALIZED_EXACT';
    if (type === 'ESSAY' || type === 'SPEAKING') return 'PROFILE_BASED';
    return 'ALL_OR_NOTHING';
  }

  function positiveInteger(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
  }

  function nonNegativeInteger(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
  }

  function applyTemplateMetadata(catalog, draft, templateCode) {
    const selected = template(catalog, templateCode);
    if (!selected) return null;
    if (!draft.document) draft.document = {};
    draft.schemaVersion = 'practice-draft-v3';
    delete draft.document.examTemplateCode;
    delete draft.document.detectedCategory;
    delete draft.document.assessmentProgramCode;
    delete draft.document.assessmentProgramVersionId;
    delete draft.document.assessmentProgramVersion;
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
