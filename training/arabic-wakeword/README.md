# Arabic wake-word training for SADEEQI

This directory contains a starter configuration for the external `JeezLuis/wakeword-trainer` project.

## Target phrase
- Wake phrase: **صديقي**
- Language: Arabic (`ar`)
- Synthetic seed voice: Piper `ar_JO-kareem-medium` (male, Jordanian Arabic)
- Output expected from trainer: `sadeeqi_ar.onnx`

## Training procedure
1. Use a Linux computer with Node.js 24+, at least 4 GB free disk space, and several hours of CPU time.
2. Clone https://github.com/JeezLuis/wakeword-trainer and follow its README setup instructions.
3. Copy `sadeeqi.json` into the trainer's `configs/` directory.
4. Run `npm run setup`, then `npm run generate -- --config configs/sadeeqi.json`, `npm run features -- --config configs/sadeeqi.json`, and `npm run train -- --config configs/sadeeqi.json`.
5. Inspect `out/sadeeqi_ar.report.json`. Do not ship the model unless the held-out detection and false-alarm results are acceptable.
6. Add real recordings from the target phone and speaker: about 30 varied positive recordings saying only «صديقي», and 20 negative recordings of ordinary speech without the wake phrase. Retrain and retest in quiet, street noise, music, and with the phone screen off.

## Important limitations
- The Piper seed voice is Jordanian Arabic, not Yemeni/Ibb dialect. Synthetic training alone is not sufficient to claim robust Yemeni recognition.
- The trainer's generated model uses the openWakeWord feature format. It is **not** compatible with the separate Voicute ONNX wake-word engine.
- No model is included yet. This configuration is a training starting point, not an implemented or validated always-on feature.
- Android integration still needs a streaming feature extractor/runtime matching this trainer's model format, foreground microphone lifecycle, wake-lock policy, and physical-device acceptance tests.
- Android may restrict launching an activity directly over the lock screen. The wake event can be detected and the notification updated even when the OS defers opening the app.

## Sources
- Trainer: https://github.com/JeezLuis/wakeword-trainer
- Piper Arabic voice catalog: https://piperhub.org/voices/language/ar/
- openWakeWord training background: https://github.com/dscripka/openWakeWord
