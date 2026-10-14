// Microphone capture for the transcription views. Captures on an AudioWorklet thread
// and downsamples to 16 kHz mono. Two modes, selected by start() options:
//   stream: true  — energy VAD trims silence and PCM16 chunks stream to the server
//                   as they're captured (onAudioChunk / onSpeechPause / onAudioEnd).
//   stream: false — all audio is buffered, encoded as one WAV file, and handed over
//                   once on stop (onAudioCaptured).
// Loaded via vaadin-js-loader, so it attaches its API to window.

(function () {
  const OUT_RATE = 16000;
  const FLUSH_MS = 250;

  const VAD_ABS_FLOOR = 0.004;  // RMS below this is always silence
  const VAD_NOISE_FACTOR = 3.0; // voiced when RMS exceeds the tracked noise floor × this
  const VAD_NOISE_EMA = 0.05;   // noise-floor adaptation rate (only while not speaking)
  const VAD_HANGOVER_MS = 250;  // keep sending this long after speech dips (word tails)
  const VAD_PREROLL_MS = 200;   // replay this much pre-speech so word onsets aren't clipped
  const VAD_PAUSE_MS = 150;     // sustained silence past the hangover that counts as a sentence end

  function rms(samples) {
    let sum = 0;
    for (let i = 0; i < samples.length; i++) {
      sum += samples[i] * samples[i];
    }
    return Math.sqrt(sum / samples.length);
  }

  const PROCESSOR_SRC = `
    class RecProcessor extends AudioWorkletProcessor {
      constructor() { super(); this._buf = new Float32Array(2048); this._n = 0; }
      process(inputs) {
        const input = inputs[0];
        if (input && input[0]) {
          const ch = input[0];
          for (let i = 0; i < ch.length; i++) {
            this._buf[this._n++] = ch[i];
            if (this._n === this._buf.length) {
              this.port.postMessage(this._buf, [this._buf.buffer]);
              this._buf = new Float32Array(2048);
              this._n = 0;
            }
          }
        }
        return true;
      }
    }
    registerProcessor('rec-processor', RecProcessor);
  `;

  function downsample(buffer, inRate, outRate) {
    if (outRate >= inRate) {
      return buffer;
    }
    const ratio = inRate / outRate;
    const newLen = Math.round(buffer.length / ratio);
    const result = new Float32Array(newLen);
    let offsetResult = 0;
    let offsetBuffer = 0;
    while (offsetResult < newLen) {
      const nextOffset = Math.round((offsetResult + 1) * ratio);
      let acc = 0;
      let count = 0;
      for (let i = offsetBuffer; i < nextOffset && i < buffer.length; i++) {
        acc += buffer[i];
        count++;
      }
      result[offsetResult] = count > 0 ? acc / count : 0;
      offsetResult++;
      offsetBuffer = nextOffset;
    }
    return result;
  }

  function toPcm16(samples) {
    const out = new DataView(new ArrayBuffer(samples.length * 2));
    for (let i = 0; i < samples.length; i++) {
      const s = Math.max(-1, Math.min(1, samples[i]));
      out.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return out.buffer;
  }

  function encodeWav(samples, sampleRate) {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);
    const writeString = (offset, str) => {
      for (let i = 0; i < str.length; i++) {
        view.setUint8(offset + i, str.charCodeAt(i));
      }
    };
    writeString(0, 'RIFF');
    view.setUint32(4, 36 + samples.length * 2, true);
    writeString(8, 'WAVE');
    writeString(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true); // PCM
    view.setUint16(22, 1, true); // mono
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeString(36, 'data');
    view.setUint32(40, samples.length * 2, true);
    let offset = 44;
    for (let i = 0; i < samples.length; i++, offset += 2) {
      const s = Math.max(-1, Math.min(1, samples[i]));
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return buffer;
  }

  function toBase64(arrayBuffer) {
    let binary = '';
    const bytes = new Uint8Array(arrayBuffer);
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  function flush(el) {
    const s = el._rec;
    if (!s || s.pending.length === 0) {
      return;
    }
    let len = 0;
    s.pending.forEach((c) => (len += c.length));
    const merged = new Float32Array(len);
    let off = 0;
    s.pending.forEach((c) => {
      merged.set(c, off);
      off += c.length;
    });
    s.pending = [];
    el.$server.onAudioChunk(toBase64(toPcm16(merged)));
  }

  function gate(el, batch) {
    const rec = el._rec;
    const vad = rec.vad;
    const level = rms(batch);
    const threshold = Math.max(VAD_ABS_FLOOR, vad.noiseFloor * VAD_NOISE_FACTOR);
    const rawVoiced = level > threshold;

    let sending;
    if (rawVoiced) {
      vad.hangover = vad.hangoverBatches;
      sending = true;
    } else {
      vad.noiseFloor = vad.noiseFloor === 0 ? level : vad.noiseFloor * (1 - VAD_NOISE_EMA) + level * VAD_NOISE_EMA;
      sending = vad.hangover > 0;
      if (sending) {
        vad.hangover--;
      }
    }

    const down = downsample(batch, rec.ctx.sampleRate, OUT_RATE);
    if (sending) {
      if (!vad.wasSending) {
        vad.preRoll.forEach((p) => rec.pending.push(p));
        vad.preRoll = [];
      }
      rec.pending.push(down);
      vad.silenceBatches = 0;
      vad.spokeSincePause = true;
    } else {
      vad.silenceBatches++;
      if (vad.spokeSincePause && vad.silenceBatches >= vad.pauseBatches) {
        vad.spokeSincePause = false;
        flush(el);
        el.$server.onSpeechPause();
      }
      vad.preRoll.push(down);
      if (vad.preRoll.length > vad.preRollMax) {
        vad.preRoll.shift();
      }
    }
    vad.wasSending = sending;
  }

  window.recorder = {
    start: async function (el, opts) {
      const stream = (opts && opts.stream) !== false;
      const mic = await navigator.mediaDevices.getUserMedia({ audio: true });
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const moduleUrl = URL.createObjectURL(new Blob([PROCESSOR_SRC], { type: 'application/javascript' }));
      await ctx.audioWorklet.addModule(moduleUrl);
      URL.revokeObjectURL(moduleUrl);

      const source = ctx.createMediaStreamSource(mic);
      const node = new AudioWorkletNode(ctx, 'rec-processor');
      el._rec = { stream, mic, ctx, source, node, pending: [], timer: null };

      if (stream) {
        const batchMs = (2048 / ctx.sampleRate) * 1000;
        el._rec.vad = {
          noiseFloor: 0,
          hangover: 0,
          hangoverBatches: Math.ceil(VAD_HANGOVER_MS / batchMs),
          preRoll: [],
          preRollMax: Math.ceil(VAD_PREROLL_MS / batchMs),
          wasSending: false,
          silenceBatches: 0,
          pauseBatches: Math.ceil(VAD_PAUSE_MS / batchMs),
          spokeSincePause: false
        };
        node.port.onmessage = (e) => gate(el, new Float32Array(e.data));
        el._rec.timer = setInterval(() => flush(el), FLUSH_MS);
      } else {
        // One-shot: buffer every batch, downsampled, for a single WAV on stop.
        node.port.onmessage = (e) =>
          el._rec.pending.push(downsample(new Float32Array(e.data), ctx.sampleRate, OUT_RATE));
      }

      source.connect(node);
      node.connect(ctx.destination);
    },

    stop: function (el) {
      const s = el._rec;
      if (!s) {
        return;
      }
      if (s.timer) {
        clearInterval(s.timer);
      }
      s.node.port.onmessage = null;
      s.node.disconnect();
      s.source.disconnect();
      s.mic.getTracks().forEach((t) => t.stop());

      if (s.stream) {
        flush(el);
        s.ctx.close();
        el._rec = null;
        el.$server.onAudioEnd();
      } else {
        let len = 0;
        s.pending.forEach((c) => (len += c.length));
        const merged = new Float32Array(len);
        let off = 0;
        s.pending.forEach((c) => {
          merged.set(c, off);
          off += c.length;
        });
        s.ctx.close();
        el._rec = null;
        el.$server.onAudioCaptured(toBase64(encodeWav(merged, OUT_RATE)));
      }
    }
  };
})();
