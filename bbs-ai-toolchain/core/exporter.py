#
# BBS AI Studio 外部工具链
#
# 导出器：动作数据写出到 config/bbs/imports/（游戏内统一导入管理器自动发现），
# 并提供根运动提取等后处理。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import json
import os
import time
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from .motion_data import MotionData, MotionFrame


def default_imports_folder() -> str:
    """默认导出目录：<游戏目录>/config/bbs/imports/

    优先从当前目录推断 Minecraft 游戏目录（存在 config/bbs 则认定），否则使用相对目录。
    """
    candidates = [
        os.path.join(os.getcwd(), "config", "bbs"),
        os.path.join(os.getcwd(), "..", "config", "bbs"),
        os.path.join(os.getcwd(), "..", "..", "config", "bbs"),
    ]

    for candidate in candidates:
        if os.path.isdir(candidate):
            return os.path.normpath(os.path.join(candidate, "imports"))

    folder = os.environ.get("BBS_AI_IMPORTS")

    if folder:
        return folder

    return os.path.abspath("bbs_imports")


def export_motion(data: MotionData, output_path: Optional[str] = None, folder: Optional[str] = None) -> str:
    """导出动作数据 JSON

    :param data: 动作数据
    :param output_path: 完整输出路径（优先）
    :param folder: 输出目录（与 output_path 二选一，默认 config/bbs/imports/）
    :return: 输出文件路径
    """

    if output_path is None:
        target_folder = folder or default_imports_folder()
        os.makedirs(target_folder, exist_ok=True)

        base = os.path.splitext(os.path.basename(data.metadata.source_file or "motion"))[0] or "motion"
        output_path = os.path.join(target_folder, "%s_%d.json" % (base, int(time.time())))

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    data.save(output_path)

    return output_path


def extract_root_motion(data: MotionData) -> List[Tuple[int, float]]:
    """根运动提取（近似）：以躯干旋转的 X 分量前向速度积分估计整体位移

    返回 [(tick, 前向位移格数)]。真正的根运动需要深度信息，此处提供
    可调的近似估计，供摄像机跟随等用途参考。
    """

    stride = 0.75  # 每度前向摆动的近似步幅（格），可按角色身高调整
    result: List[Tuple[int, float]] = []
    travelled = 0.0
    previous_tick = None
    previous_swing = None

    for frame in data.keyframes:
        body = frame.bones.get("body")
        swing = body.rotation[0] if body else 0.0

        if previous_tick is not None and previous_swing is not None:
            delta = (swing - previous_swing) * stride / 180.0 * np.pi

            travelled += abs(np.sin(delta)) * stride

        result.append((frame.tick, travelled))
        previous_tick = frame.tick
        previous_swing = swing

    return result


def append_camera_shot(data: MotionData, shot: Dict[str, Any]) -> None:
    """附加镜头运动数据"""

    data.camera_shots.append(shot)


def append_action(data: MotionData, action: Dict[str, Any]) -> None:
    """附加动作剪辑数据"""

    data.actions.append(action)


def summarize(data: MotionData) -> str:
    """生成摘要文本（GUI 展示）"""

    duration = 0.0

    if data.keyframes:
        duration = data.keyframes[-1].tick / 20.0

    bones = set()

    for frame in data.keyframes:
        bones.update(frame.bones.keys())

    return (
        "帧数: %d | 时长: %.1f 秒 | 骨骼: %d/%d | 模型: %s | 来源: %s"
        % (
            len(data.keyframes),
            duration,
            len(bones),
            6,
            data.metadata.model or "未知",
            data.metadata.source,
        )
    )
