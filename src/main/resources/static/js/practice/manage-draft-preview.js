(function () {
  'use strict';

  function mapResponse(response) {
    const sections = response && Array.isArray(response.sections) ? response.sections : [];
    const groups = [];
    let totalPoints = 0;
    sections.forEach((section, sectionIndex) => {
      (section.groups || []).forEach((group, groupIndex) => {
        const questions = (group.questions || []).map((question, questionIndex) => {
          const points = Number(question.points) || 0;
          const content = question.content && typeof question.content === 'object'
            ? question.content
            : {};
          totalPoints += points;
          return {
            questionNo: Number(question.questionNo) || questionIndex + 1,
            questionType: question.questionType,
            prompt: question.prompt || '',
            questionContent: content,
            options: Array.isArray(content.options) ? content.options : [],
            imageUrl: content.imageReference || '',
            audioUrl: content.audioReference || '',
            prepTimeSeconds: Number(question.prepTimeSeconds) || 0,
            respTimeSeconds: Number(question.respTimeSeconds) || 0,
            points
          };
        });
        groups.push({
          secTitle: section.title || '',
          skill: section.skill || 'READING',
          sIdx: sectionIndex,
          gIdx: groupIndex,
          points: questions.reduce((sum, question) => sum + question.points, 0),
          grp: {
            label: group.label || '',
            instruction: group.instruction || '',
            passageText: group.passageText || '',
            audioUrl: group.mediaReference || '',
            imageUrl: group.imageReference || '',
            questions
          }
        });
      });
    });
    return { title: response && response.title || 'Đề luyện tập', groups, totalPoints };
  }

  window.PracticeDraftPreview = { mapResponse };
})();
