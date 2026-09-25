#
# BBS AI Studio 外部工具链
#
# 骨骼映射：COCO 17 关键点 → BBS 标准 6 骨骼旋转（度）。
# 与游戏内 SkeletonMapper 保持同一套映射规则，并支持用户微调：
# 旋转偏移、缩放、镜像、根骨骼偏移。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple

import numpy as np

from .motion_data import ALL_BONES, BonePose, MotionFrame
from .pose_estimator import PoseKeypoints

HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG = ALL_BONES


@dataclass
class MappingTweaks:
    """用户微调参数"""

    mirror: bool = False                 # 镜像（前置摄像头视频）
    rotation_offset: Tuple[float, float, float] = (0.0, 0.0, 0.0)  # 全局旋转偏移（度）
    scale: float = 1.0                   # 旋转角度缩放
    root_offset: Tuple[float, float, float] = (0.0, 0.0, 0.0)      # 根骨骼偏移（写入 camera_shots 或调试）


def _limb_swing(x1: float, y1: float, x2: float, y2: float) -> float:
    """肢体摆角（弧度）：相对竖直向下方向的偏角，左右带符号"""

    dx = x2 - x1
    dy = y2 - y1

    return float(np.arctan2(dx, abs(dy) + 1e-4))


def _angle_of(x1: float, y1: float, x2: float, y2: float) -> float:
    """两点连线相对竖直方向的夹角（弧度，无符号）"""

    dx = x2 - x1
    dy = y2 - y1

    return float(np.arctan2(abs(dx), abs(dy) + 1e-4))


def _clamp_roll(roll: float) -> float:
    return max(-30.0, min(30.0, roll))


class SkeletonMapper:
    """COCO 17 → BBS 6 骨骼映射器"""

    def __init__(self, tweaks: Optional[MappingTweaks] = None):
        self.tweaks = tweaks or MappingTweaks()

    def map(self, keypoints: PoseKeypoints, min_confidence: float = 0.3) -> Dict[str, Tuple[float, float, float]]:
        """单帧关键点 → 骨骼旋转（度）"""

        result: Dict[str, Tuple[float, float, float]] = {}

        if keypoints is None:
            return result

        kp = keypoints
        mirror = self.tweaks.mirror

        # 躯干参考点
        has_neck = kp.valid(5, min_confidence) and kp.valid(6, min_confidence)
        has_hip = kp.valid(11, min_confidence) and kp.valid(12, min_confidence)

        if has_neck and has_hip:
            neck_x = (kp.x[5] + kp.x[6]) / 2.0
            neck_y = (kp.y[5] + kp.y[6]) / 2.0
            hip_x = (kp.x[11] + kp.x[12]) / 2.0
            hip_y = (kp.y[11] + kp.y[12]) / 2.0

            lean = float(np.degrees(_angle_of(neck_x, neck_y, hip_x, hip_y)))
            roll = float(np.degrees(np.arctan2(kp.y[6] - kp.y[5], abs(kp.x[6] - kp.x[5]) + 1e-4)))

            result[BODY] = (lean * (-1.0 if mirror else 1.0), 0.0, _clamp_roll(roll))

        # 头部：鼻相对颈部 → pitch，眼距收缩 → yaw
        if has_neck and kp.valid(0, min_confidence):
            neck_x = (kp.x[5] + kp.x[6]) / 2.0
            neck_y = (kp.y[5] + kp.y[6]) / 2.0

            head_pitch = float(np.degrees(_angle_of(kp.x[0], kp.y[0], neck_x, neck_y)))
            yaw = 0.0

            if kp.valid(1, min_confidence) and kp.valid(2, min_confidence):
                eye_spread = abs(kp.x[1] - kp.x[2])
                head_width = self._head_width(kp, min_confidence)

                if head_width > 1e-3:
                    ratio = max(0.0, min(1.0, eye_spread / head_width))
                    sign = 1.0 if (kp.x[1] + kp.x[2]) / 2.0 < neck_x else -1.0
                    yaw = sign * float(np.degrees(np.arccos(ratio)))

            result[HEAD] = (head_pitch * (-1.0 if mirror else 1.0) - 10.0, yaw, 0.0)

        # 四肢
        self._map_limb(kp, min_confidence, result, 5, 7, 9, LEFT_ARM, mirror)
        self._map_limb(kp, min_confidence, result, 6, 8, 10, RIGHT_ARM, mirror)
        self._map_limb(kp, min_confidence, result, 11, 13, 15, LEFT_LEG, mirror)
        self._map_limb(kp, min_confidence, result, 12, 14, 16, RIGHT_LEG, mirror)

        return self._apply_tweaks(result)

    def _head_width(self, kp: PoseKeypoints, min_confidence: float) -> float:
        if kp.valid(3, min_confidence) and kp.valid(4, min_confidence):
            return abs(kp.x[3] - kp.x[4])

        if kp.valid(1, min_confidence) and kp.valid(2, min_confidence):
            return abs(kp.x[1] - kp.x[2]) * 1.6

        return 0.0

    def _map_limb(self, kp: PoseKeypoints, min_confidence: float, result: Dict,
                  root: int, mid: int, end: int, bone: str, mirror: bool) -> None:
        if not (kp.valid(root, min_confidence) and kp.valid(mid, min_confidence)):
            return

        upper = _limb_swing(kp.x[root], kp.y[root], kp.x[mid], kp.y[mid])
        swing = upper

        if kp.valid(end, min_confidence):
            lower = _limb_swing(kp.x[mid], kp.y[mid], kp.x[end], kp.y[end])
            swing = (upper + lower) / 2.0

        if mirror:
            swing = -swing

        z_spread = -2.0 if bone == LEFT_LEG else 2.0 if bone == RIGHT_LEG else 0.0

        result[bone] = (float(np.degrees(swing)), 0.0, z_spread)

    def _apply_tweaks(self, rotations: Dict[str, Tuple[float, float, float]]) -> Dict[str, Tuple[float, float, float]]:
        ox, oy, oz = self.tweaks.rotation_offset
        scale = self.tweaks.scale

        adjusted = {}

        for bone, (x, y, z) in rotations.items():
            adjusted[bone] = (
                (x + ox) * scale,
                (y + oy) * scale,
                (z + oz) * scale,
            )

        return adjusted


class OneEuroFilter:
    """OneEuro 自适应低通滤波器（逐通道）"""

    def __init__(self, min_cutoff: float = 1.0, beta: float = 0.7, d_cutoff: float = 1.0):
        self.min_cutoff = max(0.01, min_cutoff)
        self.beta = beta
        self.d_cutoff = d_cutoff
        self.prev_value: Optional[float] = None
        self.prev_derivative: Optional[float] = None

    @staticmethod
    def _alpha(cutoff: float, dt: float) -> float:
        tau = 1.0 / (2.0 * np.pi * cutoff)

        return 1.0 / (1.0 + tau / dt)

    def filter(self, value: float, dt: float) -> float:
        derivative = 0.0 if self.prev_value is None else (value - self.prev_value) / max(dt, 1e-4)

        alpha_d = self._alpha(self.d_cutoff, dt)
        filtered_d = derivative if self.prev_derivative is None else alpha_d * derivative + (1 - alpha_d) * self.prev_derivative

        cutoff = self.min_cutoff + self.beta * abs(filtered_d)
        alpha = self._alpha(cutoff, dt)
        filtered = value if self.prev_value is None else alpha * value + (1 - alpha) * self.prev_value

        self.prev_value = filtered
        self.prev_derivative = filtered_d

        return float(filtered)


class FootLocker:
    """简易脚部锁定：踝部竖直位移极小视为触地，冻结该侧腿部旋转更新"""

    THRESHOLD = 0.004

    def __init__(self):
        self.prev_left_y: Optional[float] = None
        self.prev_right_y: Optional[float] = None
        self.left_locked = False
        self.right_locked = False

    def update(self, keypoints: PoseKeypoints, min_confidence: float) -> None:
        if keypoints.valid(15, min_confidence):
            y = float(keypoints.y[15])
            self.left_locked = self.prev_left_y is not None and abs(y - self.prev_left_y) < self.THRESHOLD
            self.prev_left_y = y

        if keypoints.valid(16, min_confidence):
            y = float(keypoints.y[16])
            self.right_locked = self.prev_right_y is not None and abs(y - self.prev_right_y) < self.THRESHOLD
            self.prev_right_y = y

    def is_locked(self, bone: str) -> bool:
        if bone == LEFT_LEG:
            return self.left_locked

        if bone == RIGHT_LEG:
            return self.right_locked

        return False


def keypoints_to_frames(
    keypoints_list: List[PoseKeypoints],
    source_fps: float,
    sample_every: int,
    min_confidence: float = 0.3,
    smoothing: float = 0.5,
    foot_lock: bool = True,
    tweaks: Optional[MappingTweaks] = None,
    progress=None,
) -> List[MotionFrame]:
    """关键点序列 → 平滑后的关键帧列表（tick 已换算）"""

    mapper = SkeletonMapper(tweaks)
    dt = max(1.0 / (source_fps / max(1, sample_every)), 1.0 / 60.0)

    # 平滑强度 → OneEuro 参数
    clamped = max(0.0, min(1.0, smoothing))
    min_cutoff = 3.0 * (1.0 - clamped) + 0.3
    beta = 0.5 + 3.0 * clamped

    channels: Dict[str, List[OneEuroFilter]] = {
        bone: [OneEuroFilter(min_cutoff, beta) for _ in range(3)] for bone in ALL_BONES
    }

    previous: Dict[str, Tuple[float, float, float]] = {}
    locker = FootLocker()
    frames: List[MotionFrame] = []

    for index, keypoints in enumerate(keypoints_list):
        mapped = mapper.map(keypoints, min_confidence)

        tick = int(round(index * sample_every * 20.0 / max(1.0, source_fps)))
        frame = MotionFrame(tick=tick)

        if foot_lock:
            locker.update(keypoints, min_confidence)

        for bone in ALL_BONES:
            rotation = mapped.get(bone)

            # 置信度门控：本帧不可信保持上一帧
            if rotation is None:
                rotation = previous.get(bone)

                if rotation is None:
                    continue

            # Foot Lock
            if foot_lock and locker.is_locked(bone) and bone in previous:
                rotation = previous[bone]

            filtered = tuple(
                channels[bone][axis].filter(rotation[axis], dt) for axis in range(3)
            )

            previous[bone] = filtered
            frame.bones[bone] = BonePose(rotation=list(filtered))

        if frame.bones:
            frames.append(frame)

        if progress is not None and index % 10 == 0:
            progress((index + 1) / float(len(keypoints_list)))

    return frames
