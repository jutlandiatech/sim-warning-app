"""Master v2 audio to 1.8x v1 RMS using smooth saturation, with 5% peak headroom.

This is a signal-level target, not a guarantee of perceived/acoustic loudness.
Soft saturation adds harmonics and makes the tones fuller; it is not transparent gain.
Original approved files are preserved. Python standard library only.
"""
from array import array
from pathlib import Path
import hashlib
import json
import math
import sys
import wave

root = Path(__file__).resolve().parent
results = []
for name, resource in [('connection-loss', 'connection_loss'), ('status-unavailable', 'status_unavailable')]:
    source = root / f'{name}-louder-preview.wav'
    output = root / f'{name}-v2.wav'
    with wave.open(str(source), 'rb') as audio:
        params = audio.getparams()
        assert params.sampwidth == 2 and params.nchannels == 1 and params.comptype == 'NONE'
        pcm = array('h', audio.readframes(params.nframes))
    if sys.byteorder != 'little':
        pcm.byteswap()
    samples = [v / 32767 for v in pcm]
    rms = lambda xs: math.sqrt(sum(v*v for v in xs) / len(xs))
    original_rms = rms(samples)
    target = original_rms * 1.8
    peak = max(abs(v) for v in samples)
    def master(drive):
        scale = .95 / math.tanh(drive * peak)
        return [math.tanh(drive * v) * scale for v in samples]
    low, high = .0001, 1024.0
    if rms(master(high)) < target:
        raise ValueError('Requested RMS is not achievable within headroom')
    for _ in range(32):
        mid = (low + high) / 2
        if rms(master(mid)) < target:
            low = mid
        else:
            high = mid
    processed = master((low+high)/2)
    result_pcm = array('h', (round(v*32767) for v in processed))
    output_rms = rms(result_pcm) / 32767
    clipped = sum(abs(v) >= 32767 for v in result_pcm)
    assert clipped == 0 and abs(output_rms / original_rms - 1.8) < .0001
    assert all(result_pcm[i] == 0 for i, v in enumerate(pcm) if v == 0)
    measured_peak = max(abs(v) for v in result_pcm) / 32767
    if sys.byteorder != 'little':
        result_pcm.byteswap()
    with wave.open(str(output), 'wb') as audio:
        audio.setparams(params)
        audio.writeframes(result_pcm.tobytes())
    results.append(dict(source=source.name, output=output.name,
        sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        outputSha256=hashlib.sha256(output.read_bytes()).hexdigest(),
        rmsRatio=output_rms/original_rms, rmsGainDb=20*math.log10(output_rms/original_rms),
        peak=measured_peak, clippedSamples=clipped,
        durationSeconds=params.nframes/params.framerate, sampleRate=params.framerate,
        method='Smooth tanh saturation; brighter timbre; 95% peak cap', drive=(low+high)/2))
    (root.parent / 'app/src/main/res/raw' / f'{resource}.wav').write_bytes(output.read_bytes())
(root / 'v2-audio-measurements.json').write_text(json.dumps(results, indent=2)+'\n')
print(json.dumps(results, indent=2))
