const fs = require('node:fs');
const path = require('node:path');

const sampleRate = 44100;
const durationSeconds = 1.8;
const frameCount = Math.floor(sampleRate * durationSeconds);
const pcm = Buffer.alloc(frameCount * 2);
const notes = [523.25, 659.25, 783.99];

for (let frame = 0; frame < frameCount; frame += 1) {
  const time = frame / sampleRate;
  const noteIndex = Math.min(notes.length - 1, Math.floor(time / 0.6));
  const noteTime = time - noteIndex * 0.6;
  const active = noteTime < 0.44;
  const attack = Math.min(1, noteTime / 0.025);
  const release = Math.min(1, Math.max(0, (0.44 - noteTime) / 0.06));
  const envelope = active ? Math.min(attack, release) : 0;
  const value = Math.sin(2 * Math.PI * notes[noteIndex] * time)
    * envelope * 0.22;
  pcm.writeInt16LE(Math.round(value * 32767), frame * 2);
}

const wav = Buffer.alloc(44 + pcm.length);
wav.write('RIFF', 0);
wav.writeUInt32LE(36 + pcm.length, 4);
wav.write('WAVE', 8);
wav.write('fmt ', 12);
wav.writeUInt32LE(16, 16);
wav.writeUInt16LE(1, 20);
wav.writeUInt16LE(1, 22);
wav.writeUInt32LE(sampleRate, 24);
wav.writeUInt32LE(sampleRate * 2, 28);
wav.writeUInt16LE(2, 32);
wav.writeUInt16LE(16, 34);
wav.write('data', 36);
wav.writeUInt32LE(pcm.length, 40);
pcm.copy(wav, 44);

const output = path.resolve(
  __dirname,
  '../../src/main/resources/static/audio/practice/listening-speaker-check.wav'
);
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, wav);
console.log(`Generated ${output} (${wav.length} bytes)`);
