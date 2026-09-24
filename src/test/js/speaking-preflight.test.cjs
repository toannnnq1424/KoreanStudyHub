const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function setup(getUserMedia, {meterFails = false, recorderHangs = false} = {}) {
  const nodes = new Map(), timers = new Map();
  let serial = 0;
  function node(selector) {
    if (!nodes.has(selector)) nodes.set(selector, {listeners:{},style:{setProperty(){}},classList:{toggle(){}},
      checked:false,disabled:false,pause(){},addEventListener(event, fn){this.listeners[event] = fn;}});
    return nodes.get(selector);
  }
  const root = {dataset:{uploadEnabled:'true'},querySelector:node,classList:{toggle(){}}};
  class Recorder {
    constructor(){this.state='inactive';this.mimeType='audio/webm';this.listeners={};}
    static isTypeSupported(){return true;}
    addEventListener(event,fn){this.listeners[event]=fn;}
    start(){this.state='recording';}
    stop(){this.state='inactive';if(recorderHangs)return;this.listeners.dataavailable({data:new Blob(['audio'])});this.listeners.stop();}
  }
  const context = {document:{querySelector:()=>root},navigator:{mediaDevices:{getUserMedia}},MediaRecorder:Recorder,
    Blob,URL:{createObjectURL:()=> 'blob:test',revokeObjectURL(){}},
    setTimeout(fn,delay){const id=++serial;timers.set(id,{fn,delay});return id;},clearTimeout(id){timers.delete(id);},
    cancelAnimationFrame(){},requestAnimationFrame(){return 1;},addEventListener(){}};
  if(meterFails)context.AudioContext = class {constructor(){throw Error('meter unavailable');}};
  context.window=context;
  vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/practice/speaking-preflight.js','utf8'), context);
  return {node,click:()=>node('[data-record-sample]').listeners.click(),
    fire(delay){for(const [id,timer] of timers){if(timer.delay===delay){timers.delete(id);timer.fn();break;}}}};
}
function stream(){const track={stopped:false,stop(){this.stopped=true;}};return {track,getTracks:()=>[track],getAudioTracks:()=>[track]};}

test('unanswered permission prompt unlocks retry and releases late stream',async()=>{
  let grant;const mic=stream();const ui=setup(()=>new Promise(resolve=>{grant=resolve;}));
  const pending=ui.click();assert.equal(ui.node('[data-record-sample]').disabled,true);
  ui.fire(15000);await pending;
  assert.equal(ui.node('[data-record-sample]').disabled,false);
  assert.equal(ui.node('[data-start-speaking]').disabled,true);
  grant(mic);await Promise.resolve();assert.equal(mic.track.stopped,true);
});
test('meter failure does not prevent sample completion',async()=>{
  const mic=stream();const ui=setup(async()=>mic,{meterFails:true});await ui.click();ui.fire(5000);
  assert.equal(ui.node('[data-playback]').hidden,false);
  assert.equal(ui.node('[data-record-sample]').disabled,false);
  assert.equal(mic.track.stopped,true);
});
test('recorder missing stop event times out and releases mic',async()=>{
  const mic=stream();const ui=setup(async()=>mic,{recorderHangs:true});await ui.click();ui.fire(5000);ui.fire(10000);
  assert.equal(ui.node('[data-record-sample]').disabled,false);
  assert.equal(mic.track.stopped,true);
  assert.equal(ui.node('[data-start-speaking]').disabled,true);
});
test('denied permission shows error and enables retry',async()=>{
  const ui=setup(async()=>{throw {name:'NotAllowedError'};});await ui.click();
  assert.equal(ui.node('[data-record-sample]').disabled,false);
  assert.match(ui.node('[data-check-error]').textContent,/từ chối/);
});
