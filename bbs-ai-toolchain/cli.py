#
# BBS AI Studio 外部工具链 —— 命令行入口
#
# 用法示例：
#   python cli.py convert 视频.mp4 -o 动作.json --backend mediapipe --mirror
#   python cli.py batch 视频目录/ --output 导出目录/ --sample 3
#   python cli.py test-connection --uuid <Minecraft UUID>
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core import (  # noqa: E402
    MotionData,
    create_estimator,
    export_motion,
    keypoints_to_frames,
    load_config,
    summarize,
)
from core.skeleton_mapper import MappingTweaks  # noqa: E402
from core.video_reader import VideoReader  # noqa: E402


def convert(args) -> int:
    """单个视频转换"""

    if not os.path.isfile(args.video):
        print("视频不存在：%s" % args.video)

        return 1

    print("读取视频：%s" % args.video)

    reader = VideoReader(args.video, sample_every=args.sample)

    frames = []

    for _, frame in reader.read_frames(progress=lambda p: print("\r抽帧 %d%%" % int(p * 100), end="")):
        frames.append(frame)

    print("\n共 %d 帧（源 %d 帧，%s FPS）" % (len(frames), reader.info.total_frames, reader.info.fps))

    if not frames:
        return 1

    print("姿态估计（%s）..." % args.backend)

    estimator_kwargs = {"model_path": args.model} if args.backend == "rtmpose" else {}

    keypoints = []
    estimator = create_estimator(args.backend, **estimator_kwargs)

    with estimator:
        for index, frame in enumerate(frames):
            keypoints.append(estimator.estimate(frame[:, :, ::-1], source_frame=index))

            if (index + 1) % 10 == 0:
                print("\r估计 %d/%d" % (index + 1, len(frames)), end="")

    print("\n骨骼映射与平滑...")

    tweaks = MappingTweaks(
        mirror=args.mirror,
        rotation_offset=(args.offset_x, args.offset_y, args.offset_z),
        scale=args.scale,
    )

    motion_frames = keypoints_to_frames(
        keypoints,
        source_fps=reader.info.fps,
        sample_every=args.sample,
        min_confidence=args.min_confidence,
        smoothing=args.smoothing,
        foot_lock=not args.no_foot_lock,
        tweaks=tweaks,
    )

    data = MotionData()
    data.keyframes = motion_frames
    data.stamp(source="video", source_file=os.path.basename(args.video),
               fps=int(reader.info.fps), total_frames=len(keypoints), model=args.backend)

    output = export_motion(data, output_path=args.output)

    print("完成：%s" % output)
    print(summarize(data))

    return 0


def batch(args) -> int:
    """文件夹批量转换"""

    if not os.path.isdir(args.folder):
        print("目录不存在：%s" % args.folder)

        return 1

    extensions = (".mp4", ".avi", ".mov", ".webm", ".mkv")
    videos = [name for name in sorted(os.listdir(args.folder)) if name.lower().endswith(extensions)]

    if not videos:
        print("目录中没有视频文件")

        return 1

    print("共 %d 个视频" % len(videos))

    failed = 0

    for index, name in enumerate(videos):
        print("\n[%d/%d] %s" % (index + 1, len(videos), name))

        result = _run_convert(os.path.join(args.folder, name), args)

        if result != 0:
            failed += 1

    print("\n批量完成：%d 成功，%d 失败" % (len(videos) - failed, failed))

    return 0 if failed == 0 else 1


def _run_convert(video: str, args) -> int:
    """批量内部复用 convert 逻辑（静默）"""

    class _Args:
        pass

    inner = _Args()

    inner.video = video
    inner.output = None
    inner.sample = args.sample
    inner.backend = args.backend
    inner.model = getattr(args, "model", None)
    inner.mirror = args.mirror
    inner.smoothing = args.smoothing
    inner.min_confidence = args.min_confidence
    inner.no_foot_lock = args.no_foot_lock
    inner.offset_x = inner.offset_y = inner.offset_z = 0.0
    inner.scale = 1.0

    try:
        return convert(inner)
    except Exception as exc:  # noqa: BLE001
        print("失败：%s" % exc)

        return 1


def test_connection(args) -> int:
    """测试 API 连接"""

    config = load_config(args.uuid, args.config)

    print("厂商：%s | 模型：%s | Key：%s" % (
        config.provider,
        config.model or "(默认)",
        (config.api_key[:4] + "****") if config.api_key else "未读取到",
    ))

    if not config.api_key:
        print("未读取到 API Key：请确认 UUID 正确且游戏端已配置")

        return 1

    adapter_module = {
        "OPENAI": "core.openai_adapter.OpenAIAdapter",
        "CLAUDE": "core.claude_adapter.ClaudeAdapter",
        "DEEPSEEK": "core.deepseek_adapter.DeepSeekAdapter",
        "GLM": "core.glm_adapter.GLMAdapter",
        "CUSTOM": "core.openai_adapter.OpenAIAdapter",
    }.get(config.provider, "core.openai_adapter.OpenAIAdapter")

    module_path, class_name = adapter_module.rsplit(".", 1)

    module = __import__(module_path, fromlist=[class_name])
    adapter = getattr(module, class_name)(
        api_key=config.api_key, base_url=config.base_url, model=config.model,
        temperature=config.temperature, max_tokens=config.max_tokens,
    )

    try:
        reply = adapter.generate("You are a connection tester. Reply with exactly: pong", "ping")
    except Exception as exc:  # noqa: BLE001
        print("连接失败：%s" % exc)

        return 1

    print("连接成功，模型回复：%s" % reply[:80])

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="bbs-ai-toolchain", description="BBS AI Studio 外部工具链")
    sub = parser.add_subparsers(dest="command", required=True)

    # convert
    convert_parser = sub.add_parser("convert", help="单个视频转换")
    convert_parser.add_argument("video", help="输入视频路径")
    convert_parser.add_argument("-o", "--output", help="输出 JSON 路径（默认 config/bbs/imports/）")
    convert_parser.add_argument("--sample", type=int, default=2, help="抽帧间隔（默认 2）")
    convert_parser.add_argument("--backend", default="mediapipe", choices=["mediapipe", "yolov8", "rtmpose"])
    convert_parser.add_argument("--model", help="RTMPose 的 ONNX 模型路径（--backend rtmpose 时必填）")
    convert_parser.add_argument("--mirror", action="store_true", help="镜像左右")
    convert_parser.add_argument("--smoothing", type=float, default=0.5, help="平滑强度 0~1")
    convert_parser.add_argument("--min-confidence", type=float, default=0.3, help="关键点最低置信度")
    convert_parser.add_argument("--no-foot-lock", action="store_true", help="禁用脚部锁定")
    convert_parser.add_argument("--offset-x", type=float, default=0.0, help="旋转偏移 X（度）")
    convert_parser.add_argument("--offset-y", type=float, default=0.0, help="旋转偏移 Y（度）")
    convert_parser.add_argument("--offset-z", type=float, default=0.0, help="旋转偏移 Z（度）")
    convert_parser.add_argument("--scale", type=float, default=1.0, help="旋转缩放")
    convert_parser.set_defaults(func=convert)

    # batch
    batch_parser = sub.add_parser("batch", help="文件夹批量转换")
    batch_parser.add_argument("folder", help="视频文件夹")
    batch_parser.add_argument("--output", help="输出目录（默认 config/bbs/imports/）")
    batch_parser.add_argument("--sample", type=int, default=2)
    batch_parser.add_argument("--backend", default="mediapipe", choices=["mediapipe", "yolov8", "rtmpose"])
    batch_parser.add_argument("--model", help="RTMPose 的 ONNX 模型路径（--backend rtmpose 时必填）")
    batch_parser.add_argument("--mirror", action="store_true")
    batch_parser.add_argument("--smoothing", type=float, default=0.5)
    batch_parser.add_argument("--min-confidence", type=float, default=0.3)
    batch_parser.add_argument("--no-foot-lock", action="store_true")
    batch_parser.set_defaults(func=batch)

    # test-connection
    connection_parser = sub.add_parser("test-connection", help="测试 API 连接")
    connection_parser.add_argument("--uuid", required=True, help="Minecraft 玩家 UUID（解密共享配置）")
    connection_parser.add_argument("--config", help="ai_providers.json 路径（默认自动查找）")
    connection_parser.set_defaults(func=test_connection)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
