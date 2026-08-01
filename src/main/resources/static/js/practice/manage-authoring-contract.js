(function () {
  'use strict';

  const CONTENT_SCHEMA = 'question-content-v1';
  const SPEAKING_CONTENT_SCHEMA = 'question-content-v2';
  const LANGUAGE_REGION_CONTENT_SCHEMA = 'question-content-v3';
  const ANSWER_SCHEMA = 'answer-spec-v1';
  const SECTION_DELIVERY_SCHEMA = 'practice-section-delivery-v1';
  const WRITING_BLANK_RESPONSE_SCHEMA = 'writing-blanks.v1';
  const WRITING_BLANK_AUTHORITY_SCHEMA = 'writing-blank-authority.v1';
  const WRITING_BLANK_RESPONSE_MODE = 'STRUCTURED_BLANKS';
  const WRITING_LEGACY_READ_ONLY_MODE = 'LEGACY_ESSAY_READ_ONLY';

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
    const canonicalContent = q.questionContent && typeof q.questionContent === 'object'
      ? q.questionContent
      : {};
    // Editable drafts must carry an explicit region authority. Historical
    // published versions remain compatible in their read-only presenters, but
    // opening an editable legacy question upgrades its prompt region to Korean
    // unless the lecturer has explicitly selected Vietnamese.
    q.promptLanguageTag = canonicalContent.languageTag || q.promptLanguageTag || 'ko';
    const sourceOptions = Array.isArray(q.options) && q.options.length > 0
      ? q.options
      : (Array.isArray(canonicalContent.options) ? canonicalContent.options : []);
    q.options = sourceOptions.map(option => normalizeOption(option, makeId));
    q.imageUrl = q.imageUrl || canonicalContent.imageReference || '';
    q.audioUrl = q.audioUrl || canonicalContent.audioReference || '';
    const delivery = canonicalContent.speakingDelivery;
    if (delivery && typeof delivery === 'object') {
      const hasPromptAudioReference = Object.prototype.hasOwnProperty.call(
        delivery, 'promptAudioReference');
      q.speakingPromptAudioUrl = hasPromptAudioReference
        ? (delivery.promptAudioReference || '')
        : (q.speakingPromptAudioUrl || q.audioUrl || '');
      const hasPromptPlayLimit = Object.prototype.hasOwnProperty.call(
        delivery, 'promptPlayLimit');
      q.speakingPromptPlayLimit = hasPromptPlayLimit
        && delivery.promptPlayLimit == null
        ? 0
        : positiveInteger(delivery.promptPlayLimit, 1);
      q.prepTimeSeconds = nonNegativeInteger(delivery.preparationSeconds, 30);
      q.respTimeSeconds = positiveInteger(delivery.responseSeconds, 60);
      if (!q.speakingPromptAuthoring || typeof q.speakingPromptAuthoring !== 'object') {
        q.speakingPromptAuthoring = {
          inputType: delivery.inputType || 'audio_upload',
          ttsEnabled: delivery.inputType === 'manual_text'
            && delivery.audioOrigin === 'ai_tts'
        };
      }
    }

    if (q.questionType === 'FILL_BLANK' && (!Array.isArray(q.fillBlanks) || q.fillBlanks.length === 0)) {
      const canonicalBlanks = Array.isArray(canonicalContent.blanks) ? canonicalContent.blanks : [];
      const canonicalAnswers = q.answerSpec && Array.isArray(q.answerSpec.blanks)
        ? q.answerSpec.blanks
        : [];
      if (canonicalBlanks.length > 0) {
        q.fillBlanks = canonicalBlanks.map(blank => {
          const answer = canonicalAnswers.find(candidate => candidate.blankId === blank.id);
          return {
            id: blank.id || makeId('blank'),
            prompt: blank.prompt || '',
            acceptedValues: answer && Array.isArray(answer.acceptedValues)
              ? Array.from(answer.acceptedValues)
              : []
          };
        });
      } else {
        const legacy = String((q.answer && q.answer.value) || q.answerKey || '').trim();
        q.fillBlanks = [{
          id: makeId('blank'),
          prompt: '',
          acceptedValues: legacy ? [legacy] : []
        }];
      }
    }
    if (q.questionType === 'MULTIPLE_ANSWER') {
      const correctIds = new Set(Array.isArray(q.answerSpec && q.answerSpec.correctOptionIds)
        ? q.answerSpec.correctOptionIds.map(String)
        : []);
      const existing = String((q.answer && q.answer.value) || q.answerKey || '').trim();
      if (!existing && correctIds.size > 0) {
        const indexes = q.options.map((option, index) => correctIds.has(String(option.id))
          ? String(index + 1)
          : null).filter(Boolean);
        q.answer = { type: 'MULTIPLE', value: indexes.join(',') };
        q.answerKey = indexes.join(',');
      }
    }
    if (q.questionType === 'MATCHING'
        && (!Array.isArray(q.matchingTargets) || q.matchingTargets.length === 0)) {
      const canonicalBlanks = Array.isArray(canonicalContent.blanks)
        ? canonicalContent.blanks
        : [];
      const canonicalAnswers = q.answerSpec && Array.isArray(q.answerSpec.blanks)
        ? q.answerSpec.blanks
        : [];
      q.matchingTargets = canonicalBlanks.map(blank => {
        const answer = canonicalAnswers.find(candidate => candidate.blankId === blank.id);
        const accepted = answer && Array.isArray(answer.acceptedValues)
          ? answer.acceptedValues
          : [];
        return {
          id: blank.id || makeId('match'),
          prompt: blank.prompt || '',
          candidateOptionId: accepted[0] || ''
        };
      });
      if (q.matchingTargets.length === 0) {
        q.matchingTargets = [
          { id: makeId('match'), prompt: 'Nội dung cần ghép 1', candidateOptionId: '' },
          { id: makeId('match'), prompt: 'Nội dung cần ghép 2', candidateOptionId: '' }
        ];
      }
    }
    syncQuestionContract(q);
    return q;
  }

  function syncSectionContract(section) {
    const sec = section && typeof section === 'object' ? section : {};
    const previous = sec.sectionDelivery && typeof sec.sectionDelivery === 'object'
      ? sec.sectionDelivery
      : {};
    const previousListening = previous.listeningDelivery && typeof previous.listeningDelivery === 'object'
      ? previous.listeningDelivery
      : {};
    const delivery = { schemaVersion: SECTION_DELIVERY_SCHEMA };
    if (sec.skill === 'LISTENING') {
      const checkAudioReference = sec.listeningCheckAudioUrl !== undefined
        ? (sec.listeningCheckAudioUrl || null)
        : (previousListening.checkAudioReference || null);
      delivery.listeningDelivery = { checkAudioReference };
      sec.listeningCheckAudioUrl = checkAudioReference || '';
    } else {
      delete sec.listeningCheckAudioUrl;
    }
    sec.sectionDelivery = delivery;
    return sec;
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
    const languageTag = ['ko', 'vi'].includes(q.promptLanguageTag)
      ? q.promptLanguageTag
      : null;
    if (languageTag) {
      content.schemaVersion = LANGUAGE_REGION_CONTENT_SCHEMA;
      content.languageTag = languageTag;
    }
    if (previousContent.writingResponse
        && typeof previousContent.writingResponse === 'object') {
      content.writingResponse = previousContent.writingResponse;
    }
    const answer = {
      schemaVersion: ANSWER_SCHEMA,
      questionType: type,
      correctOptionIds: [],
      correctValue: null,
      blanks: [],
      scoringPolicyCode: scoringPolicy(type)
    };

    if (type === 'ESSAY' && isWritingBlankTask(q)) {
      const writingResponse = previousContent.writingResponse;
      const writingAuthority = previousSpec.writingBlankAuthority;
      if (writingResponse && typeof writingResponse === 'object') {
        content.writingResponse = writingResponse;
      }
      if (writingAuthority && typeof writingAuthority === 'object') {
        answer.writingBlankAuthority = writingAuthority;
      }
      q.writingCompatibilityMode = content.writingResponse
          && answer.writingBlankAuthority
        ? WRITING_BLANK_RESPONSE_MODE
        : WRITING_LEGACY_READ_ONLY_MODE;
    } else {
      delete content.writingResponse;
      delete answer.writingBlankAuthority;
      delete q.writingCompatibilityMode;
    }

    if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_ANSWER') {
      content.options = (q.options || []).map(option => ({
        id: option.id,
        text: option.text || '',
        imageReference: option.imageReference || null
      }));
      const selectedIndexes = String((q.answer && q.answer.value) || q.answerKey || '')
        .split(',')
        .map(value => Number.parseInt(value.trim(), 10) - 1)
        .filter(index => Number.isInteger(index) && index >= 0 && index < content.options.length);
      answer.correctOptionIds = selectedIndexes.length > 0
        ? selectedIndexes.map(index => content.options[index].id)
        : (Array.isArray(previousSpec.correctOptionIds)
          ? previousSpec.correctOptionIds.filter(id => content.options.some(option => option.id === id))
          : []);
      const correctIndex = content.options.findIndex(option => answer.correctOptionIds.includes(option.id));
      const legacyValue = type === 'MULTIPLE_ANSWER'
        ? content.options.map((option, index) => answer.correctOptionIds.includes(option.id)
          ? String(index + 1)
          : null).filter(Boolean).join(',')
        : (correctIndex >= 0 ? String(correctIndex + 1) : '');
      q.answer = { type: type === 'MULTIPLE_ANSWER' ? 'MULTIPLE' : 'SINGLE', value: legacyValue };
      q.answerKey = legacyValue;
    } else if (type === 'TRUE_FALSE_NOT_GIVEN') {
      answer.correctValue = String(
        (q.answer && q.answer.value) || q.answerKey || previousSpec.correctValue || ''
      ).trim() || null;
      q.answer = { type: 'TFNG', value: answer.correctValue || '' };
      q.answerKey = answer.correctValue || '';
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
      content.options = (q.options || []).map(option => ({
        id: option.id,
        text: option.text || '',
        imageReference: option.imageReference || null
      }));
      const targets = Array.isArray(q.matchingTargets) ? q.matchingTargets : [];
      content.blanks = targets.map(target => ({
        id: target.id,
        prompt: target.prompt || ''
      }));
      answer.blanks = targets.map(target => ({
        blankId: target.id,
        acceptedValues: target.candidateOptionId
          && content.options.some(option => option.id === target.candidateOptionId)
          ? [target.candidateOptionId]
          : []
      }));
      q.answer = { type: 'MATCHING', value: '' };
      q.answerKey = '';
    }

    content.imageReference = q.imageUrl !== undefined
      ? (q.imageUrl || null)
      : (previousContent.imageReference || null);
    content.audioReference = q.audioUrl !== undefined
      ? (q.audioUrl || null)
      : (previousContent.audioReference || null);
    if (type === 'SPEAKING') {
      const authoring = q.speakingPromptAuthoring
        && typeof q.speakingPromptAuthoring === 'object'
        ? q.speakingPromptAuthoring
        : {};
      const inputType = authoring.inputType === 'manual_text'
        ? 'manual_text'
        : 'audio_upload';
      const ttsEnabled = inputType === 'manual_text'
        && authoring.ttsEnabled === true;
      const promptAudioReference = Object.prototype.hasOwnProperty.call(
        q, 'speakingPromptAudioUrl')
        ? (q.speakingPromptAudioUrl || null)
        : (previousDelivery.promptAudioReference || q.audioUrl || null);
      content.schemaVersion = languageTag
        ? LANGUAGE_REGION_CONTENT_SCHEMA
        : SPEAKING_CONTENT_SCHEMA;
      content.speakingDelivery = {
        inputType,
        deliveryMode: inputType === 'audio_upload'
          ? 'audio_only'
          : (ttsEnabled ? 'text_and_audio' : 'text_only'),
        promptAudioReference,
        audioOrigin: inputType === 'audio_upload'
          ? 'teacher_upload'
          : (ttsEnabled ? 'ai_tts' : 'none'),
        promptPlayLimit: inputType === 'manual_text' && !ttsEnabled
          ? null
          : positiveInteger(q.speakingPromptPlayLimit || previousDelivery.promptPlayLimit, 1),
        preparationSeconds: nonNegativeInteger(q.prepTimeSeconds ?? previousDelivery.preparationSeconds, 30),
        responseSeconds: positiveInteger(q.respTimeSeconds || previousDelivery.responseSeconds, 60)
      };
      q.speakingPromptAudioUrl = promptAudioReference || '';
      q.speakingPromptPlayLimit = content.speakingDelivery.promptPlayLimit || 0;
      q.prepTimeSeconds = content.speakingDelivery.preparationSeconds;
      q.respTimeSeconds = content.speakingDelivery.responseSeconds;
    }
    delete q.canonicalQuestionType;
    q.questionContent = content;
    q.answerSpec = answer;
    return q;
  }

  function isWritingBlankTask(q) {
    return q && q.questionType === 'ESSAY'
      && (q.essayTaskType === 'Q51' || q.essayTaskType === 'Q52');
  }

  /**
   * Explicit editor conversion boundary for historical Q51/Q52.
   *
   * This function never parses prompt, answerKey or learner prose. In
   * particular '/' and ';' are ordinary answer characters, not delimiters.
   */
  function initializeWritingBlanks(q, definitions) {
    if (!isWritingBlankTask(q)) {
      throw new Error('Structured Writing blanks are only valid for Q51/Q52.');
    }
    const taskType = q.essayTaskType;
    const requested = Array.isArray(definitions) ? definitions : [];
    const blanks = [1, 2].map(ordinal => {
      const definition = requested[ordinal - 1] || {};
      return {
        blankId: taskType.toLowerCase() + '-b' + ordinal,
        ordinal,
        context: String(
          definition.context || ('Ngữ cảnh ô ' + ordinal)
        ).normalize('NFC')
      };
    });
    const authorityBlanks = blanks.map((blank, index) => ({
      blankId: blank.blankId,
      ordinal: blank.ordinal,
      acceptedAnswers: normalizeWritingAcceptedAnswers(
        requested[index] && requested[index].acceptedAnswers)
    }));
    const previousContent = q.questionContent
      && typeof q.questionContent === 'object' ? q.questionContent : {};
    const previousSpec = q.answerSpec
      && typeof q.answerSpec === 'object' ? q.answerSpec : {};
    q.questionContent = {
      ...previousContent,
      schemaVersion: LANGUAGE_REGION_CONTENT_SCHEMA,
      languageTag: q.promptLanguageTag || previousContent.languageTag || 'ko',
      options: [],
      blanks: [],
      writingResponse: {
        responseSchemaVersion: WRITING_BLANK_RESPONSE_SCHEMA,
        responseMode: WRITING_BLANK_RESPONSE_MODE,
        taskType,
        blanks
      }
    };
    q.answerSpec = {
      ...previousSpec,
      schemaVersion: ANSWER_SCHEMA,
      questionType: 'ESSAY',
      correctOptionIds: [],
      correctValue: null,
      blanks: [],
      scoringPolicyCode: 'PROFILE_BASED',
      writingBlankAuthority: {
        contractVersion: WRITING_BLANK_AUTHORITY_SCHEMA,
        taskType,
        normalization: 'NFC',
        whitespacePolicy: 'TRIM_COLLAPSE',
        blanks: authorityBlanks
      }
    };
    q.writingCompatibilityMode = WRITING_BLANK_RESPONSE_MODE;
    return syncQuestionContract(q);
  }

  function normalizeWritingAcceptedAnswers(values) {
    if (!Array.isArray(values)) return [];
    const seen = new Set();
    return values.map(value => {
      const source = value && typeof value === 'object'
        ? value
        : { text: value };
      const text = String(source.text == null ? '' : source.text)
        .normalize('NFC')
        .trim()
        .replace(/\s+/g, ' ');
      if (!text || seen.has(text)) return null;
      seen.add(text);
      return {
        text,
        equivalence: source.equivalence === 'SEMANTIC_EQUIVALENT'
          ? 'SEMANTIC_EQUIVALENT'
          : 'EXACT',
        reason: source.reason || null,
        evidenceIds: Array.isArray(source.evidenceIds)
          ? Array.from(source.evidenceIds)
          : []
      };
    }).filter(Boolean);
  }

  function scoringPolicy(type) {
    if (type === 'FILL_BLANK' || type === 'MATCHING') return 'NORMALIZED_EXACT';
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
    syncSectionContract,
    normalizeQuestion,
    syncQuestionContract,
    initializeWritingBlanks,
    isWritingBlankTask,
    applyTemplateMetadata
  };
})();
