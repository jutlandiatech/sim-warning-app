"""Create louder review copies without changing approved or packaged sounds."""

from array import array
import hashlib
import json
import math
from pathlib import Path
import sys
import wave


def generate():
    directory = Path(__file__).resolve().parent
    results = []
    for name in ('connection-loss', 'status-unavailable'):
        source = directory / f'{name}-candidate.wav'
        output = directory / f'{name}-louder-preview.wav'
        with wave.open(str(source), 'rb') as audio:
            params = audio.getparams()
            if params.sampwidth != 2 or params.comptype != 'NONE':
                raise ValueError('Expected 16-bit PCM')
            samples = array('h', audio.readframes(params.nframes))
        if sys.byteorder != 'little':
            samples.byteswap()
        peak = max(abs(sample) for sample in samples)
        gain = (0.95 * 32767) / peak
        louder = array('h', (round(sample * gain) for sample in samples))
        output_peak = max(abs(sample) for sample in louder)
        assert output_peak < 32767
        assert len(louder) == len(samples)
        rms = lambda values: math.sqrt(sum(value * value for value in values) / len(values))
        result = {
            'source': source.name,
            'preview': output.name,
            'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'gain_db': round(20 * math.log10(gain), 2),
            'source_peak_dbfs': round(20 * math.log10(peak / 32767), 2),
            'preview_peak_dbfs': round(20 * math.log10(output_peak / 32767), 2),
            'source_rms_dbfs': round(20 * math.log10(rms(samples) / 32767), 2),
            'preview_rms_dbfs': round(20 * math.log10(rms(louder) / 32767), 2),
            'duration_seconds': params.nframes / params.framerate,
            'clipped_samples': sum(abs(sample) >= 32767 for sample in louder),
        }
        if sys.byteorder != 'little':
            louder.byteswap()
        with wave.open(str(output), 'wb') as audio:
            audio.setparams(params)
            audio.writeframes(louder.tobytes())
        result['preview_sha256'] = hashlib.sha256(output.read_bytes()).hexdigest()
        results.append(result)
    report = directory / 'louder-preview-measurements.json'
    report.write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print(report.read_text(encoding='utf-8'))


if __name__ == '__main__':
    generate()
