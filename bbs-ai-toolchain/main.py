#
# BBS AI Studio 外部工具链 —— Gradio GUI 入口
#
# 深色主题（类 Blender）：左侧上传 + 参数，中间预览，右侧导出 + 队列。
#
# 用法：python main.py
# 作者：BBS AI Studio
#

from __future__ import annotations

import base64
import os
import sys

import gradio as gr
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core import (  # noqa: E402
    AIConfig,
    BatchProcessor,
    MotionData,
    TaskState,
    create_estimator,
    export_motion,
    keypoints_to_frames,
    load_config,
    summarize,
)
from core.video_reader import VideoReader  # noqa: E402

# ----------------------------------------------------------------------
# 全局状态
# ----------------------------------------------------------------------

_pipeline_state = {"config": None, "last_data": None}
_batch_processor = BatchProcessor()


def resolve_config(uuid: str) -> AIConfig:
    """读取（解密）共享配置"""

    if _pipeline_state["config"] is None or _pipeline_state.get("uuid") != uuid:
        _pipeline_state["config"] = load_config(uuid)
        _pipeline_state["uuid"] = uuid

    return _pipeline_state["config"]


# ----------------------------------------------------------------------
# 处理流水线
# ----------------------------------------------------------------------

def process_video(video, sample_every, backend, mirror, smoothing, foot_lock,
                  min_confidence, uuid, offset_x, offset_y, offset_z, scale):
    """单视频处理：抽帧 → 姿态估计 → 骨骼映射 → 导出"""

    if video is None:
        return "请先上传视频", None

    sample_every = max(1, int(sample_every))

    try:
        reader = VideoReader(video, sample_every=sample_every)

        frames = []
        for _, frame in reader.read_frames(progress=lambda p: None):
            frames.append(frame)

        if not frames:
            return "视频读取失败：没有帧", None

        # 姿态估计
        keypoints = []
        estimator = create_estimator(backend)

        with estimator:
            for index, frame in enumerate(frames):
                keypoints.append(estimator.estimate(frame[:, :, ::-1], source_frame=index))

        # 骨骼映射 + 平滑
        from core.skeleton_mapper import MappingTweaks

        tweaks = MappingTweaks(mirror=bool(mirror),
                               rotation_offset=(offset_x, offset_y, offset_z),
                               scale=float(scale))

        motion_frames = keypoints_to_frames(
            keypoints,
            source_fps=reader.info.fps,
            sample_every=sample_every,
            min_confidence=float(min_confidence),
            smoothing=float(smoothing),
            foot_lock=bool(foot_lock),
            tweaks=tweaks,
        )

        data = MotionData()
        data.keyframes = motion_frames
        data.stamp(source="video",
                   source_file=os.path.basename(video),
                   fps=int(reader.info.fps),
                   total_frames=len(keypoints),
                   model=backend)

        _pipeline_state["last_data"] = data

        output = export_motion(data)
        summary = summarize(data)

        return "处理完成！\n" + summary + "\n已导出：" + output, output
    except Exception as exc:  # noqa: BLE001 - GUI 需要完整吞错展示
        return "处理失败：%s" % exc, None


def test_connection(uuid):
    """测试 API 连接（读取共享加密配置）"""

    try:
        config = resolve_config(uuid)

        if not config.api_key:
            return "未读取到 API Key（需要 Minecraft UUID 解密共享配置）"

        adapter = _create_adapter(config)
        reply = adapter.generate("You are a connection tester. Reply with exactly: pong", "ping")

        return "连接成功，模型回复：" + reply[:50]
    except Exception as exc:  # noqa: BLE001
        return "连接失败：%s" % exc


def _create_adapter(config: AIConfig):
    from core.openai_adapter import OpenAIAdapter
    from core.claude_adapter import ClaudeAdapter
    from core.deepseek_adapter import DeepSeekAdapter
    from core.glm_adapter import GLMAdapter

    kwargs = dict(api_key=config.api_key, base_url=config.base_url, model=config.model,
                  temperature=config.temperature, max_tokens=config.max_tokens)

    adapters = {
        "OPENAI": OpenAIAdapter,
        "CLAUDE": ClaudeAdapter,
        "DEEPSEEK": DeepSeekAdapter,
        "GLM": GLMAdapter,
        "CUSTOM": OpenAIAdapter,
    }

    return adapters.get(config.provider, OpenAIAdapter)(**kwargs)


# ----------------------------------------------------------------------
# 批量队列
# ----------------------------------------------------------------------

def batch_add(folder, sample_every, backend, smoothing):
    if not folder or not os.path.isdir(folder):
        return "目录不存在：" + str(folder)

    count = _batch_processor.add_folder(folder, sample_every=int(sample_every), backend=backend, smoothing=float(smoothing))
    _batch_processor.start()

    return "已添加 %d 个视频任务" % count


def batch_table():
    rows = []

    for task in _batch_processor.tasks:
        rows.append([
            os.path.basename(task.video_path),
            task.state.value,
            "%d%%" % int(task.progress * 100),
            task.error or task.output_path,
        ])

    return rows


def batch_refresh():
    return batch_table()


# ----------------------------------------------------------------------
# 界面
# ----------------------------------------------------------------------

def build_ui() -> gr.Blocks:
    theme = gr.themes.Soft(primary_hue="blue", neutral_hue="slate").set(
        body_background_fill="#232323",
        block_background_fill="#2d2d2d",
        block_label_text_color="#e5e5e5",
        body_text_color="#e5e5e5",
    )

    with gr.Blocks(title="BBS AI Studio 工具链", theme=theme) as demo:
        gr.Markdown("# BBS AI Studio 外部工具链\n把视频转换为 Minecraft BBS 动作数据（bbs_ai_studio_motion_v1）")

        with gr.Tab("视频 → 动作"):
            with gr.Row():
                with gr.Column():
                    video_input = gr.Video(label="输入视频（mp4/avi/mov/webm）")
                    sample_every = gr.Slider(1, 10, value=2, step=1, label="抽帧间隔（每 N 帧取 1 帧）")
                    backend = gr.Radio(["mediapipe", "yolov8", "rtmpose"], value="mediapipe", label="姿态估计后端")
                    mirror = gr.Checkbox(value=False, label="镜像（前置摄像头视频）")
                    smoothing = gr.Slider(0.0, 1.0, value=0.5, step=0.05, label="平滑强度")
                    foot_lock = gr.Checkbox(value=True, label="脚部锁定 (Foot Lock)")
                    min_confidence = gr.Slider(0.0, 1.0, value=0.3, step=0.05, label="关键点最低置信度")

                    with gr.Accordion("骨骼映射微调", open=False):
                        offset_x = gr.Slider(-30.0, 30.0, value=0.0, step=0.5, label="旋转偏移 X（度）")
                        offset_y = gr.Slider(-30.0, 30.0, value=0.0, step=0.5, label="旋转偏移 Y（度）")
                        offset_z = gr.Slider(-30.0, 30.0, value=0.0, step=0.5, label="旋转偏移 Z（度）")
                        scale = gr.Slider(0.5, 1.5, value=1.0, step=0.05, label="旋转缩放")

                    with gr.Accordion("共享配置（与游戏内一致，AES 加密）", open=False):
                        uuid_box = gr.Textbox(label="Minecraft UUID（用于解密 ai_providers.json）", placeholder="登录 Minecraft 后的玩家 UUID")
                        test_button = gr.Button("测试 API 连接")
                        test_result = gr.Textbox(label="连接状态", interactive=False)

                    process_button = gr.Button("开始转换", variant="primary")

                with gr.Column():
                    result_box = gr.Textbox(label="处理结果", lines=8, interactive=False)
                    output_file = gr.File(label="导出文件（放入 config/bbs/imports/ 后游戏内自动发现）")

            process_button.click(
                process_video,
                inputs=[video_input, sample_every, backend, mirror, smoothing, foot_lock,
                        min_confidence, uuid_box, offset_x, offset_y, offset_z, scale],
                outputs=[result_box, output_file],
            )

            test_button.click(test_connection, inputs=[uuid_box], outputs=[test_result])

        with gr.Tab("批量队列"):
            with gr.Row():
                with gr.Column():
                    folder_box = gr.Textbox(label="视频文件夹")
                    batch_sample = gr.Slider(1, 10, value=2, step=1, label="抽帧间隔")
                    batch_backend = gr.Radio(["mediapipe", "yolov8"], value="mediapipe", label="姿态估计后端")
                    batch_smoothing = gr.Slider(0.0, 1.0, value=0.5, step=0.05, label="平滑强度")
                    add_button = gr.Button("加入队列", variant="primary")

                    with gr.Row():
                        pause_button = gr.Button("暂停")
                        resume_button = gr.Button("继续")
                        cancel_button = gr.Button("取消当前")

                with gr.Column():
                    queue_table = gr.Dataframe(headers=["文件", "状态", "进度", "输出"], label="任务队列")
                    refresh_button = gr.Button("刷新队列")

            add_button.click(batch_add, inputs=[folder_box, batch_sample, batch_backend, batch_smoothing], outputs=[result_box])
            refresh_button.click(batch_refresh, outputs=[queue_table])
            pause_button.click(_batch_processor.pause)
            resume_button.click(_batch_processor.resume)
            cancel_button.click(_batch_processor.cancel_current)

        gr.Markdown("---\n导出的 JSON 复制/保存到 `<游戏目录>/config/bbs/imports/`，游戏内 AI 工具面板即可自动发现并预览烘焙。")

    return demo


def main() -> None:
    demo = build_ui()

    demo.queue().launch(server_name="127.0.0.1", server_port=7860, inbrowser=True)


if __name__ == "__main__":
    main()
