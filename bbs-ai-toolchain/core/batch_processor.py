#
# BBS AI Studio 外部工具链
#
# 批量处理器：文件夹批量导入、任务队列（暂停/继续/取消）、多线程执行。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import os
import threading
import time
import traceback
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable, List, Optional

from .exporter import export_motion
from .motion_data import MotionData
from .skeleton_mapper import MappingTweaks
from .video_reader import VideoReader


class TaskState(Enum):
    PENDING = "等待中"
    RUNNING = "处理中"
    DONE = "完成"
    FAILED = "失败"
    CANCELLED = "已取消"


@dataclass
class BatchTask:
    """单个批量任务"""

    video_path: str
    output_dir: Optional[str] = None
    sample_every: int = 2
    backend: str = "mediapipe"
    tweaks: MappingTweaks = field(default_factory=MappingTweaks)
    min_confidence: float = 0.3
    smoothing: float = 0.5
    foot_lock: bool = True
    state: TaskState = TaskState.PENDING
    error: str = ""
    output_path: str = ""
    progress: float = 0.0


class BatchProcessor:
    """批量处理器（单工作线程 + 队列）"""

    def __init__(self, on_updated: Optional[Callable[[], None]] = None):
        self.tasks: List[BatchTask] = []
        self._lock = threading.Lock()
        self._pause_event = threading.Event()

        # Event 默认 set=True 表示运行；False 表示暂停
        self._pause_event.set()
        self._cancel_current = False
        self._worker: Optional[threading.Thread] = None
        self._on_updated = on_updated

    # ------------------------------------------------------------------
    # 队列管理
    # ------------------------------------------------------------------

    def add_folder(self, folder: str, **task_kwargs) -> int:
        """批量添加目录中的视频文件，返回添加数量"""

        added = 0
        extensions = (".mp4", ".avi", ".mov", ".webm", ".mkv")

        for name in sorted(os.listdir(folder)):
            path = os.path.join(folder, name)

            if os.path.isfile(path) and name.lower().endswith(extensions):
                self.add_video(path, **task_kwargs)
                added += 1

        return added

    def add_video(self, video_path: str, **task_kwargs) -> BatchTask:
        task = BatchTask(video_path=video_path, **task_kwargs)

        with self._lock:
            self.tasks.append(task)

        self._notify()

        return task

    def remove(self, index: int) -> None:
        with self._lock:
            if 0 <= index < len(self.tasks) and self.tasks[index].state == TaskState.PENDING:
                self.tasks.pop(index)

        self._notify()

    def clear_finished(self) -> None:
        with self._lock:
            self.tasks = [t for t in self.tasks if t.state in (TaskState.PENDING, TaskState.RUNNING)]

        self._notify()

    # ------------------------------------------------------------------
    # 控制
    # ------------------------------------------------------------------

    def start(self) -> None:
        if self._worker is not None and self._worker.is_alive():
            self.resume()

            return

        self._worker = threading.Thread(target=self._run, daemon=True, name="bbs-ai-batch")
        self._worker.start()

    def pause(self) -> None:
        self._pause_event.clear()

    def resume(self) -> None:
        self._pause_event.set()

    def cancel_current(self) -> None:
        self._cancel_current = True

    # ------------------------------------------------------------------
    # 工作线程
    # ------------------------------------------------------------------

    def _run(self) -> None:
        while True:
            self._pause_event.wait()

            task = self._next_pending()

            if task is None:
                time.sleep(0.5)

                continue

            self._process(task)

    def _next_pending(self) -> Optional[BatchTask]:
        with self._lock:
            for task in self.tasks:
                if task.state == TaskState.PENDING:
                    task.state = TaskState.RUNNING

                    return task

        return None

    def _process(self, task: BatchTask) -> None:
        backend = task.backend

        # 容错：历史笔误 mediapiape → mediapipe
        if backend == "mediapiape":
            backend = "mediapipe"

        self._cancel_current = False

        try:
            reader = VideoReader(task.video_path, sample_every=task.sample_every)

            from .pose_estimator import create_estimator

            keypoints = []

            with create_estimator(backend) as estimator:
                for index, frame in reader.read_frames(progress=lambda p: self._set_progress(task, p * 0.6)):
                    self._check_pause_cancel(task)

                    keypoints.append(estimator.estimate(frame[:, :, ::-1], source_frame=index))

            self._check_pause_cancel(task)

            frames = keypoints_to_frames(
                keypoints,
                source_fps=reader.info.fps,
                sample_every=task.sample_every,
                min_confidence=task.min_confidence,
                smoothing=task.smoothing,
                foot_lock=task.foot_lock,
                tweaks=task.tweaks,
                progress=lambda p: self._set_progress(task, 0.6 + p * 0.4),
            )

            data = MotionData()
            data.keyframes = frames
            data.stamp(
                source="video",
                source_file=os.path.basename(task.video_path),
                fps=int(reader.info.fps),
                total_frames=len(keypoints),
                model=backend,
            )

            task.output_path = export_motion(data, folder=task.output_dir)
            task.state = TaskState.DONE
            task.progress = 1.0
        except _Cancelled:
            task.state = TaskState.CANCELLED
        except Exception as exc:  # noqa: BLE001 - 批处理需要吞掉单任务错误继续队列
            task.state = TaskState.FAILED
            task.error = str(exc)
            traceback.print_exc()

        self._notify()

    def _check_pause_cancel(self, task: BatchTask) -> None:
        if self._cancel_current:
            raise _Cancelled()

        self._pause_event.wait()

        if self._cancel_current:
            raise _Cancelled()

    def _set_progress(self, task: BatchTask, value: float) -> None:
        task.progress = max(0.0, min(1.0, value))
        self._notify()

    def _notify(self) -> None:
        if self._on_updated is not None:
            try:
                self._on_updated()
            except Exception:
                pass


class _Cancelled(Exception):
    """内部取消信号"""
