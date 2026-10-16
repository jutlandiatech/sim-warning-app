"""Generate original review candidates; not final production audio.

Python standard library only. Run from any directory; output stays beside this file.
"""

import math
from pathlib import Path
import struct
import wave


def connection_loss_candidate():
    sample_rate = 44100
    duration = 2.1
    # A rounded, lightly wooden tone: a falling pair followed by an upward call.
    notes = [(0.08, 523.25, 0.46), (0.60, 440.00, 0.46), (1.14, 587.33, 0.76)]
    samples = []
    for index in range(round(duration * sample_rate)):
        time = index / sample_rate
        value = 0.0
        for start, frequency, length in notes:
            age = time - start
            if not 0 <= age < length:
                continue
            attack = min(1.0, age / 0.035)
            release = min(1.0, (length - age) / 0.16)
            envelope = math.sin(attack * math.pi / 2) ** 2 * release ** 2
            envelope *= math.exp(-2.4 * age)
            phase = 2 * math.pi * frequency * age
            tone = (math.sin(phase) + 0.18 * math.sin(2 * phase)
                    + 0.055 * math.sin(3 * phase))
            value += envelope * tone
        samples.append(value)
    peak = max(abs(value) for value in samples)
    gain = 0.55 / peak
    output = Path(__file__).resolve().parent / 'connection-loss-candidate.wav'
    with wave.open(str(output), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(sample_rate)
        audio.writeframes(b''.join(struct.pack('<h', round(value * gain * 32767))
                                   for value in samples))
    print(f'{output.name}: {duration:.1f}s, mono PCM, {sample_rate} Hz; peak 55%, no clipping')


def status_unavailable_candidate():
    sample_rate = 44100
    duration = 1.8
    # A lower two-note descending response with a soft, hollow timbre.
    notes = [(0.08, 392.00, 0.58), (0.78, 329.63, 0.80)]
    samples = []
    for index in range(round(duration * sample_rate)):
        time = index / sample_rate
        value = 0.0
        for start, frequency, length in notes:
            age = time - start
            if not 0 <= age < length:
                continue
            attack = min(1.0, age / 0.045)
            release = min(1.0, (length - age) / 0.20)
            envelope = math.sin(attack * math.pi / 2) ** 2 * release ** 2
            envelope *= math.exp(-1.9 * age)
            phase = 2 * math.pi * frequency * age
            tone = math.sin(phase) + 0.16 * math.sin(3 * phase)
            value += envelope * tone
        samples.append(value)
    gain = 0.55 / max(abs(value) for value in samples)
    output = Path(__file__).resolve().parent / 'status-unavailable-candidate.wav'
    with wave.open(str(output), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(sample_rate)
        audio.writeframes(b''.join(struct.pack('<h', round(value * gain * 32767))
                                   for value in samples))
    print(f'{output.name}: {duration:.1f}s, mono PCM, {sample_rate} Hz; peak 55%, no clipping')


if __name__ == '__main__':
    connection_loss_candidate()
    status_unavailable_candidate()
