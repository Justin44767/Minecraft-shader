# Third-party components

- Gradle wrapper: Gradle authors, Apache License 2.0. Wrapper files originate from the official FabricMC example project, branch `26.3`. Gradle 9.8.2 distribution SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`.
- Vosk 0.3.45: Alpha Cephei and contributors, Apache License 2.0. Project: https://github.com/alphacep/vosk-api. The published Java artifact, including its native components and notices, is nested unmodified.
- JNA 5.17.0: JNA contributors, dual licensed under Apache License 2.0 / LGPL 2.1. Project: https://github.com/java-native-access/jna. The unmodified artifact is nested; see its included licenses. This project uses the Apache License 2.0 option.
- Simple Voice Chat API 2.6.25 is a compile-only dependency. Neither the API jar nor the Simple Voice Chat mod is bundled. Project: https://github.com/henkelmax/simple-voice-chat.
- Fabric Loader/API and Minecraft are separate runtime dependencies. Minecraft and its game assets are not redistributed in the mod or source archive.
- OS narration uses Minecraft's existing `com.mojang:text2speech` library and the user's installed OS voice. No third-party character voice or voice model is bundled.
- Seven original German story recordings were generated from this project's dialogue with eSpeak NG 1.52.0 (`de+m3`) and processed with FFmpeg. Projects: https://github.com/espeak-ng/espeak-ng and https://ffmpeg.org/. Only the resulting mono OGG recordings and measured amplitude curves are included; neither synthesis engine, FFmpeg binary nor eSpeak voice data is redistributed. The synthetic voice does not imitate or copy a voice from the referenced video.
- Optional lighting integration compiles against the API from the official LambDynamicLights 4.13.0+26.3 release: https://github.com/LambdAurora/LambDynamicLights. Neither its API nor mod jar is bundled. Users install the official mod separately. The project and its API retain their respective upstream licenses; this repository does not grant permission to redistribute their binaries. The original cone math and integration classes belong to this implementation.

The VERITY geometry, item geometry and PNG textures in this project were created specifically for this implementation. No assets from the existing Verity mod were copied. The German warning guide is based on the user-supplied specification.

No Vosk language model is bundled. Model licensing, download and installation must be checked before redistributing a language-model archive. The attempted official German model download was blocked by the environment's HTTP proxy, so no model files or model license claims are included here.
