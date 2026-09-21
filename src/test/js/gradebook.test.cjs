const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function average(values) {
  const control = {value:'ALL',checked:false,addEventListener(){}};
  const result = {};
  const headers = values.map((_,i) => ({dataset:{columnKey:String(i)},addEventListener(){}}));
  const cells = values.map((value,i) => ({dataset:{columnKey:String(i),...(value === undefined ? {} : {normalized:value})}}));
  const row = {querySelectorAll:()=>cells,querySelector:()=>result};
  const root = {
    querySelector:()=>control,
    querySelectorAll(selector) {
      if (selector.startsWith('th')) return headers;
      if (selector === 'tbody tr') return [row];
      return [];
    }
  };
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/gradebook.js','utf8'), {
    document:{querySelector:()=>root}, CSS:{escape:value=>value}
  });
  return result.textContent;
}
test('missing and invalid scores do not poison available grades', () => {
  assert.equal(average([undefined,'','null','NaN','Infinity','10','0.9','0.9']), '3.93');
});
test('no valid grades shows a dash, not zero or NaN', () => {
  assert.equal(average([undefined,'','NaN']), '—');
});
test('real zero grades still count', () => {
  assert.equal(average(['0','10']), '5');
});
