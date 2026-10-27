#!/usr/bin/env python3
"""Generate original German dialogue assets with eSpeak NG and ffmpeg, not another mod's voice.

Builds consume the checked-in OGG/JSON files and do not need the speech tool.
Example: python3 scripts/generate_voices.py --espeak /path/to/espeak-ng --data /path/to/build
"""
import argparse
import array
import json
import math
from pathlib import Path
import subprocess
import tempfile
import wave

CLIPS = [
    (1, 'awakening', 'Endlich. Ich bin Verity. Danke, dass du mich herausgelassen hast. Lass uns diese Welt zusammen erkunden.', 64, 145),
    (2, 'night', 'Bleib in meiner Nähe. Wir wissen noch nicht, was hier draußen auf uns wartet.', 60, 145),
    (3, 'observer', 'Hast du das auch gehört? Es kommt nicht aus der Höhle. Es kommt aus der Box.', 45, 132),
    (4, 'corrupted', 'Du hast versprochen, mir zu vertrauen. Warum hast du mich angelogen?', 28, 125),
    (5, 'demon', 'Du hättest die Box geschlossen lassen sollen. Jetzt kannst du mich nicht mehr aussperren.', 12, 116),
    (6, 'rescue', 'Richte dein Licht auf mich. Ich kann mich noch erinnern.', 38, 130),
    (7, 'calm', 'Danke. Es ist wieder still. Für heute.', 62, 140),
]

def main():
    args = argparse.ArgumentParser()
    args.add_argument('--espeak', required=True)
    args.add_argument('--data', required=True, help='Parent directory containing espeak-ng-data')
    opts = args.parse_args()
    root = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/verity'
    folder = root / 'sounds/voice'
    folder.mkdir(parents=True, exist_ok=True)
    clips, envelopes, sounds = [], {}, {}
    with tempfile.TemporaryDirectory(prefix='verity-speech-') as work:
        for ident, name, text, pitch, speed in CLIPS:
            raw = Path(work) / (name + '.wav')
            decoded = Path(work) / (name + '-decoded.wav')
            subprocess.run([opts.espeak, '--path=' + opts.data, '-v', 'de+m3', '-p', str(pitch), '-s', str(speed), '-w', str(raw), text], check=True)
            filters = 'highpass=f=90,lowpass=f=6500,alimiter=limit=0.85'
            if ident in (4, 5): filters += ',aecho=0.8:0.88:65:0.22'
            target = folder / (name + '.ogg')
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(raw), '-af', filters, '-ac', '1', '-ar', '24000', '-c:a', 'libvorbis', '-q:a', '4', str(target)], check=True)
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(target), '-ac', '1', '-ar', '24000', '-c:a', 'pcm_s16le', str(decoded)], check=True)
            with wave.open(str(decoded)) as audio:
                samples = array.array('h', audio.readframes(audio.getnframes()))
                seconds = len(samples) / audio.getframerate()
            window = 480
            rms = [math.sqrt(sum(v*v for v in samples[i:i+window]) / max(1, len(samples[i:i+window]))) for i in range(0, len(samples), window)]
            peak = max(rms) or 1
            envelopes[str(ident)] = [round(min(1, value / (peak * .85)), 4) for value in rms]
            clips.append({'id': ident, 'name': name, 'text': text, 'ticks': math.ceil(seconds * 20)})
            sounds['voice.' + name] = {'subtitle': 'subtitle.verity.' + name, 'sounds': [{'name': 'verity:voice/' + name, 'stream': True}]}
            print(name, f'{seconds:.2f}s', len(rms), 'real audio envelope samples')
    (root / 'voice_clips.json').write_text(json.dumps(clips, indent=2, ensure_ascii=False) + '\n')
    (root / 'voice_envelopes.json').write_text(json.dumps(envelopes) + '\n')
    (root / 'sounds.json').write_text(json.dumps(sounds, indent=2) + '\n')

if __name__ == '__main__': main()
