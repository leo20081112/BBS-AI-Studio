#
# BBS AI Studio 外部工具链
#
# 核心数据模型：统一导出格式 bbs_ai_studio_motion_v1 的 Python 实现。
# 与游戏内 Java 端（mchorse.bbs_ai.format.MotionData）保持字段级一致。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

FORMAT = "bbs_ai_studio_motion_v1"

# BBS 标准 6 骨骼
ALL_BONES = ["head", "body", "left_arm", "right_arm", "left_leg", "right_leg"]


@dataclass
class MotionMetadata:
    """动作数据元信息（metadata + actor 节点）"""

    source: str = "video"                    # video / text / ik_manual / external_tool
    source_file: str = ""                    # 原始文件名
    fps: int = 30                            # 原始视频帧率
    total_frames: int = 0                    # 总帧数
    model: str = ""                          # 使用的 AI 模型
    generated_at: str = ""                   # ISO 8601 时间戳
    tool_version: str = "1.0.0"              # 生成工具版本
    generator: str = "bbs_ai_toolchain"      # 生成工具标识
    target_form: str = "minecraft:player"    # 目标 Form 类型
    skeleton_type: str = "standard_6bone"    # 骨骼类型

    def to_dict(self) -> Dict[str, Any]:
        return {
            "metadata": {
                "source": self.source,
                "source_file": self.source_file,
                "fps": self.fps,
                "total_frames": self.total_frames,
                "model": self.model,
                "generated_at": self.generated_at,
                "tool_version": self.tool_version,
                "generator": self.generator,
            },
            "actor": {
                "target_form": self.target_form,
                "skeleton_type": self.skeleton_type,
            },
        }

    @classmethod
    def from_dict(cls, root: Dict[str, Any]) -> "MotionMetadata":
        metadata = root.get("metadata", {}) or {}
        actor = root.get("actor", {}) or {}

        return cls(
            source=metadata.get("source", "video"),
            source_file=metadata.get("source_file", ""),
            fps=int(metadata.get("fps", 30)),
            total_frames=int(metadata.get("total_frames", 0)),
            model=metadata.get("model", ""),
            generated_at=metadata.get("generated_at", ""),
            tool_version=metadata.get("tool_version", "1.0.0"),
            generator=metadata.get("generator", "bbs_ai_toolchain"),
            target_form=actor.get("target_form", "minecraft:player"),
            skeleton_type=actor.get("skeleton_type", "standard_6bone"),
        )


@dataclass
class BonePose:
    """单骨骼姿态：欧拉旋转角（度）+ 插值类型"""

    rotation: List[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    interpolation: str = "linear"

    def to_dict(self) -> Dict[str, Any]:
        return {"rotation": list(self.rotation), "interpolation": self.interpolation}

    @classmethod
    def from_dict(cls, data: Any) -> "BonePose":
        if not isinstance(data, dict):
            return cls()

        rotation = data.get("rotation", [0.0, 0.0, 0.0])

        if not isinstance(rotation, list) or len(rotation) < 3:
            rotation = [0.0, 0.0, 0.0]

        return cls(rotation=[float(rotation[0]), float(rotation[1]), float(rotation[2])],
                   interpolation=data.get("interpolation", "linear"))


@dataclass
class MotionFrame:
    """单关键帧：tick（20 ticks = 1 秒）+ 骨骼姿态表"""

    tick: int = 0
    bones: Dict[str, BonePose] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "tick": self.tick,
            "bones": {name: pose.to_dict() for name, pose in self.bones.items()},
        }

    @classmethod
    def from_dict(cls, data: Any) -> "MotionFrame":
        if not isinstance(data, dict):
            return cls()

        frame = cls(tick=int(data.get("tick", 0)))

        bones = data.get("bones", {}) or {}

        for name, pose in bones.items():
            frame.bones[name] = BonePose.from_dict(pose)

        return frame


@dataclass
class MotionData:
    """统一导出格式 bbs_ai_studio_motion_v1 根对象"""

    metadata: MotionMetadata = field(default_factory=MotionMetadata)
    keyframes: List[MotionFrame] = field(default_factory=list)
    camera_shots: List[Dict[str, Any]] = field(default_factory=list)
    actions: List[Dict[str, Any]] = field(default_factory=list)

    def stamp(self, source: str, source_file: str, fps: int, total_frames: int, model: str) -> None:
        """填充元信息（生成时调用）"""
        self.metadata.source = source
        self.metadata.source_file = source_file or ""
        self.metadata.fps = max(1, int(fps))
        self.metadata.total_frames = int(total_frames)
        self.metadata.model = model
        self.metadata.generated_at = datetime.now(timezone.utc).isoformat()

    def sort(self) -> None:
        """按 tick 升序排序"""
        self.keyframes.sort(key=lambda f: f.tick)

    def to_dict(self) -> Dict[str, Any]:
        root: Dict[str, Any] = {"format": FORMAT}
        root.update(self.metadata.to_dict())
        root["keyframes"] = [frame.to_dict() for frame in self.keyframes]
        root["camera_shots"] = self.camera_shots
        root["actions"] = self.actions

        return root

    def to_json(self, pretty: bool = True) -> str:
        if pretty:
            return json.dumps(self.to_dict(), ensure_ascii=False, indent=2)

        return json.dumps(self.to_dict(), ensure_ascii=False)

    def save(self, path: str) -> None:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(self.to_json(pretty=True))

    @classmethod
    def from_dict(cls, root: Dict[str, Any]) -> "MotionData":
        fmt = root.get("format", "")

        if fmt and fmt != FORMAT:
            print("[bbs-ai-toolchain] 警告：格式标识异常 %r，仍尝试解析" % fmt)

        data = cls(metadata=MotionMetadata.from_dict(root))

        for item in root.get("keyframes", []) or []:
            data.keyframes.append(MotionFrame.from_dict(item))

        data.camera_shots = [s for s in (root.get("camera_shots", []) or []) if isinstance(s, dict)]
        data.actions = [a for a in (root.get("actions", []) or []) if isinstance(a, dict)]
        data.sort()

        return data

    @classmethod
    def load(cls, path: str) -> "MotionData":
        with open(path, "r", encoding="utf-8") as handle:
            return cls.from_dict(json.load(handle))
