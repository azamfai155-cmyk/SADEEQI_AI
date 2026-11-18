# Wake phrase and 3D avatar foundation

## Arabic wake phrase

The app now exposes a separate **user-started** "انتظر نداء صديقي" action. It starts the visible microphone foreground service and uses Android SpeechRecognizer to recognize the phrase "صديقي". The service stays in wake-phrase mode until the phrase is recognized, then greets the user and continues the normal voice conversation.

Important limitations:
- This is not an always-on hardware/DSP hotword detector.
- It is not an offline wake-word model; Android's configured speech recognition provider may require network access.
- It only listens after the user explicitly starts the foreground microphone session and stops when the user stops the session.
- Screen-off/background behavior, battery use, recognition latency and false-positive rate still require tests on physical Android devices.

## 3D avatar foundation

The app now includes a SceneView-powered GLB viewport and an animated male sample model from Khronos' CesiumMan asset.

Attribution: CesiumMan by Cesium, licensed under Creative Commons Attribution 4.0 International (CC BY 4.0). See https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/CesiumMan

Important limitations:
- CesiumMan is a technical sample character, not the requested photorealistic custom human avatar.
- Skeletal animation is enabled; facial expressions, visemes and speech lip-sync are not implemented.
- The model is currently fetched from the upstream public URL, so first display requires internet access.
- Device rendering, memory use, frame rate and model loading still require emulator and physical-device tests.

## Acceptance tests still required

1. Verify wake phrase recognition in Arabic on a real device, including noise and false-positive tests.
2. Verify notification/stop controls and permission denial behavior.
3. Test foreground session while app is backgrounded and screen is off, within Android platform restrictions.
4. Verify 3D model loads and animates on API 34 emulator and the target phone.
5. Replace the sample with a user-approved photorealistic male GLB/GLTF rig that includes facial blend-shapes, then implement and test lip-sync.
