# BBS AI Studio

**BBS AI Studio** is a fork of [BBS FS](https://github.com/Wemppy4/bbs-fs) — a Minecraft animation mod (Fabric 1.20.1) — that adds **AI-driven animation creation** on top of the full original plugin ecosystem, plus a reworked UI/UX layer.

> 中文说明见 [README_zh.md](README_zh.md)。

## Highlights

- **Dual-track × dual-mode architecture** — in-game (realtime / lightweight) and an external Python toolchain (high-precision / batch), each with local ONNX or cloud API modes.
- **Unified data contract** — every generator (in-game AI, IK, storyboards, external tool) emits the same `bbs_ai_studio_motion_v1` JSON, feeding one shared preview + bake pipeline.
- **Pre-baked preview system** — AI output lands in a memory cache with onion-skin rendering and conflict markers; nothing touches the real Film until you confirm **Bake** (overwrite / insert-blend).
- **Blender-grade IK** — a full port of the Blender IK constraint semantics: target & pole targets, pole angle, chain length, use-tail, anchor-follow (Follow), influence, per-bone rotation limits, stretch + stretch limit, target rotation and weight falloff.
- **Encrypted config sharing** — API keys are stored with AES/GCM (key derived from the Minecraft UUID) and decrypted by the Python toolchain with the exact same scheme.

## Modules

| # | Module | What it does |
|---|--------|--------------|
| 1 | AI core config | Provider config (OpenAI / Claude / DeepSeek / GLM / custom), encrypted storage, `SettingsBuilder` integration |
| 2 | In-game local AI | FFmpeg frame extraction, ONNX pose estimation (YOLOv8-pose / RTMPose), COCO-17 → 6-bone mapping, OneEuro smoothing, foot lock |
| 3 | In-game API mode | OkHttp client, OpenAI-compatible + Anthropic protocols, vision input, full error mapping |
| 4 | Cloud storyboards | Strict-JSON storyboard DSL (camera / actor / transition shots) → BBS `Film` conversion |
| 5 | External toolchain | Python + Gradio GUI + CLI: MediaPipe / YOLO / RTMPose backends, batch queue, config decryption |
| 6 | Import manager | Watches `config/bbs/ai_cache/` and `config/bbs/imports/`, distinguishes in-game vs external entries |
| 7 | Preview system | Stage → cache (FloatBuffer) → onion-skin HUD → bake confirmation dialog → Film |
| 8 | Blender IK | Complete constraint data model + CCD solver with anchor-follow |
| 9 | UI themes | Classic BBS / Blender dark / Mine-imator layouts, theme resource packs |
| 10 | Operation modes | BBS-compatible (default), Blender G/R/S + viewport navigation, Mine-imator gizmo style |
| 11 | Hotkeys | 50+ registered hotkeys, custom rebinding with conflict detection, status bar, F1 cheat sheet, first-time guide |
| 12 | Languages | English / 简体中文 / 繁體中文, CJK-aware UI metrics |
| 13 | Integration | Fabric entrypoints, events, `/bbs_ai` command tree |

## Building

Requirements: JDK 21 (Gradle toolchain), Fabric Loom 1.15.

```bash
./gradlew build
```

The mod jar lands in `build/libs/`. OkHttp and ONNX Runtime are nested into the jar automatically.

## Using the AI features

1. Install the mod into a Fabric 1.20.1 profile and start the game.
2. Open the BBS dashboard (`0` by default) → **AI 工具 / AI Tools** panel.
3. **AI 设置 / AI Settings**: pick a provider, paste your API key (stored AES-encrypted in `config/bbs/settings/ai_providers.json`), press *Test connection*.
4. **分镜生成 / Storyboard**: describe a scene, generate → a Film is created with camera clips and actor keyframes.
5. **视频识别 / Video recognition**: point at a `.mp4/.avi/.mov/.webm` file. You need:
   - FFmpeg on PATH (or `-Dbbs_ai.ffmpeg=/path/to/ffmpeg`),
   - an ONNX pose model placed in `config/bbs/ai_cache/models/` (`yolov8n-pose.onnx` or `rtmpose-m.onnx`).
6. Results always land in **preview** first — review with onion skin, then *Bake* (Ctrl+Enter) or discard (Ctrl+Esc).

### External toolchain

```bash
cd bbs-ai-toolchain
pip install -r requirements.txt
python main.py            # Gradio GUI (dark theme)
python cli.py convert dance.mp4 --backend mediapipe --mirror
python cli.py test-connection --uuid <your Minecraft UUID>
```

Exported JSON lands in `config/bbs/imports/` and appears in the in-game **Import manager** automatically.

## `/bbs_ai` command

```
/bbs_ai generate storyboard <json>     convert a storyboard JSON into a film
/bbs_ai preview enter|exit|bake        control the preview pipeline
/bbs_ai ik mode <native|blender>       switch IK mode
/bbs_ai theme <classic|blender|mineimator>
/bbs_ai language <en_us|zh_cn|zh_tw>
/bbs_ai version
```

## Data contract

```json
{
  "format": "bbs_ai_studio_motion_v1",
  "metadata": { "source": "video", "fps": 30, "model": "mediapipe_pose" },
  "actor": { "target_form": "minecraft:player", "skeleton_type": "standard_6bone" },
  "keyframes": [
    { "tick": 0, "bones": { "head": { "rotation": [0.0, 0.0, 0.0], "interpolation": "linear" } } }
  ],
  "camera_shots": [],
  "actions": []
}
```

`tick` uses Minecraft time (20 ticks = 1 second); rotations are Euler degrees `[x, y, z]`.

## Project layout

```
bbs-ai-studio/
├── src/main/java/mchorse/
│   ├── bbs_mod/        # original BBS FS code (untouched interfaces)
│   └── bbs_ai/         # the AI module (this fork's addition)
├── src/client/java/mchorse/bbs_ai/   # client-only UI (panels, themes, hotkeys...)
├── src/main/resources/assets/bbs/
│   ├── strings/        # bbs_ai_*.json language files
│   └── ai_themes/      # bundled theme templates
├── bbs-ai-toolchain/   # external Python toolchain
└── .github/workflows/  # CI
```

## License

The original BBS FS is MIT-licensed — see [LICENSE.md](LICENSE.md). This fork keeps MIT.

## Acknowledgements

- [McHorse](https://github.com/mchorse) & [Wemppy](https://github.com/Wemppy4) — BBS FS
- Blender Foundation — IK constraint semantics reference
- MediaPipe / Ultralytics / RTMPose authors — pose estimation models
