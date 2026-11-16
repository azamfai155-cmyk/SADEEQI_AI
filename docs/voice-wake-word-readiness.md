# SADEEQI Arabic wake-word readiness

## Current truthful behavior

The current Android voice service is a user-started, visible microphone session. It uses Android `SpeechRecognizer` for Arabic transcription while that session is active. It does **not** provide an always-on wake-word detector while the service is stopped. The phrase matcher for `صديقي` / `يا صديقي` is evaluated only after SpeechRecognizer has returned text; it is not a wake-word engine.

Do not describe the current implementation as standby wake-word listening.

## Why the public AraSpot checkpoint is not a drop-in wake-word model

The official AraSpot repository exposes a PyTorch ConformerGRU command classifier and a checkpoint download. Its published class mapper contains English labels such as `open`, `stop`, `yes`, and `zoom in`, plus `silence`; it does not contain `صديقي` or `يا صديقي`. Its documented runtime uses PyTorch/Torchaudio and the sample configuration defaults to CUDA. Therefore the reported benchmark accuracy does not validate our desired trigger phrase, Yemeni/Ibb pronunciation, Android CPU runtime, or false-accept rate. Do not bundle that checkpoint as the SADEEQI wake model.

Reference implementation: https://github.com/msalhab96/AraSpot
Reference paper: https://arxiv.org/abs/2303.16621

## Required model contract before enabling standby listening

1. Train a dedicated Arabic KWS model for the positive variants `صديقي` and `يا صديقي`, including natural Yemeni/Ibb pronunciation variation.
2. Include a `background / unknown` negative class and hard negatives from ordinary Arabic conversation, TV, music, and similar-sounding phrases.
3. Evaluate on speakers and recordings excluded from training. Report false accepts per hour, false rejects, latency, CPU use, and battery use; do not rely on aggregate accuracy alone.
4. Export and validate a mobile-compatible ONNX or TFLite model, including a documented sample rate, channel count, window size, feature pipeline, tensor shapes, labels, threshold, and model checksum.
5. Bundle the model under the app's assets with its license and model card. Verify inference on Android arm64 before enabling the standby microphone path.
6. Only after model validation, add a dedicated opt-in microphone foreground mode. It must show a persistent notification, expose an immediate stop action, survive screen lock only while Android permits it, and never restart itself after process death or reboot without a fresh user action.
7. On detection, transition through WAKE_DETECTED -> GREETING -> ACTIVE_LISTENING and keep the existing conversation session open until explicit end command or idle timeout.

## Privacy and safety gates

- No microphone capture in standby unless the user explicitly enables the feature.
- No audio upload or retention for wake detection; inference should be on-device.
- If permission is revoked, the service cannot start, the model is missing, or Android blocks microphone foreground startup, report standby wake detection as unavailable. Never silently fall back to cloud ASR or claim that wake listening is active.
- Keep ordinary conversation content separate from the immutable founder identity.
- Keep the existing manually started voice session available while the dedicated wake model is being prepared.

## Acceptance tests

- Positive samples for both wake phrases across multiple speakers, distances, noise levels, and Yemeni pronunciations.
- Negative samples containing unrelated Arabic conversation and similar-sounding words.
- Measured false accepts per hour and false rejects on a held-out set.
- Repeated wake, multi-turn conversation without repeating the wake phrase, end command, and idle timeout.
- Screen-lock/background behavior on supported Android versions, permission revocation, notification stop action, and process death.
- Battery/CPU profiling during at least a representative 30-minute standby run.
