# BBS AI Studio 外部工具链（bbs-ai-toolchain）

独立的 Python 工具链：把视频批量转换为 BBS 动作数据（`bbs_ai_studio_motion_v1`），
与游戏内 Mod 通过 JSON 文件 + 加密配置共享互通。

## 目录结构

```
bbs-ai-toolchain/
├── main.py                      # Gradio GUI 入口（深色主题，python main.py）
├── cli.py                       # 命令行入口（convert / batch / test-connection）
├── core/                        # 全部业务逻辑（无 UI 依赖，可独立复用）
│   ├── motion_data.py           # ★ 数据契约：与 Java 端 format 包字段级一致，改动必须两侧同步
│   ├── video_reader.py          # OpenCV 视频读取 + 抽帧降采样
│   ├── pose_estimator.py        # 姿态估计三后端：mediapipe / yolov8 / rtmpose(ONNX)
│   ├── skeleton_mapper.py       # COCO-17 → BBS 6 骨骼 + OneEuro 平滑 + Foot Lock
│   ├── exporter.py              # 导出到 config/bbs/imports/（游戏内自动发现）
│   ├── config_loader.py         # 解密游戏内共享配置（AES-GCM 契约，见 Java EncryptionUtil）
│   ├── api_provider.py          # API 抽象 + openai/claude/deepseek/glm 四适配器
│   └── batch_processor.py       # 批量队列（暂停/继续/取消，单工作线程）
├── requirements.txt             # 运行依赖（mediapipe 钉 0.10.21：新版移除了 Solutions API）
├── requirements-build.txt       # 打包依赖（exe 不内置 mediapipe，推理走 rtmpose）
└── bbs_ai_toolchain.spec        # PyInstaller：GUI + CLI 双 exe
```

## 常用命令

```bash
pip install -r requirements.txt
python main.py                                              # GUI
python cli.py convert 视频.mp4 --backend mediapipe --mirror  # 单视频
python cli.py batch 视频目录/ --output 导出目录/              # 批量
python cli.py test-connection --uuid <Minecraft UUID>       # 测 API 连接
```

## 打包 Windows exe

```bash
pip install -r requirements-build.txt
pyinstaller --noconfirm --clean bbs_ai_toolchain.spec
# 产物：dist/bbs-ai-toolchain-windows-x64/（GUI + CLI 双 exe）
```

注意：打包版**不内置 mediapipe**（与 PyInstaller 冻结环境不兼容），
推理用 `--backend rtmpose --model <ONNX 模型>`；Python 版无此限制。

## 维护约定

- `core/motion_data.py` 的字段名是跨端契约，任何改动必须同步 Java 端
  `mchorse.bbs_ai.format` 包，并跑 `test_roundtrip.py` 与 CI 契约测试；
- 新增姿态后端：在 `pose_estimator.py` 实现 `PoseEstimator` 子类并注册进
  `create_estimator` 工厂即可，CLI/GUI 自动可见；
- 提交前 `python -m compileall -q core main.py cli.py` 过一遍语法。
