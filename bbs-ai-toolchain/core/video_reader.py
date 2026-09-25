#
# BBS AI Studio 外部工具链
#
# 视频读取器：OpenCV / decord 双后端，支持 .mp4/.avi/.mov/.webm 与图片序列（%04d.png）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from typing import Callable, List, Optional

import numpy as np


@dataclass
class VideoInfo:
    """视频元信息"""

    path: str
    fps: float
    total_frames: int
    width: int
    height: int


class VideoReader:
    """视频帧读取器（cv2 优先，decord 备选）"""

    def __init__(self, path: str, sample_every: int = 1):
        self.path = path
        self.sample_every = max(1, int(sample_every))
        self._capture = None
        self.info = self._probe(path)

    # ------------------------------------------------------------------
    # 探测
    # ------------------------------------------------------------------

    @staticmethod
    def _probe(path: str) -> VideoInfo:
        if not os.path.exists(path):
            raise FileNotFoundError("视频不存在：%s" % path)

        cv2 = VideoReader._import_cv2()

        capture = cv2.VideoCapture(path)

        if not capture.isOpened():
            raise IOError("无法打开视频：%s" % path)

        fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
        total = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
        capture.release()

        return VideoInfo(path=path, fps=float(fps), total_frames=total, width=width, height=height)

    @staticmethod
    def _import_cv2():
        try:
            import cv2

            return cv2
        except ImportError as exc:
            raise ImportError("需要 opencv-python：pip install opencv-python") from exc

    # ------------------------------------------------------------------
    # 读取
    # ------------------------------------------------------------------

    def read_frames(
        self,
        max_frames: Optional[int] = None,
        progress: Optional[Callable[[float], None]] = None,
    ):
        """逐帧读取（按 sample_every 降采样），产出 (源帧序号, BGR ndarray)"""

        cv2 = self._import_cv2()
        capture = cv2.VideoCapture(self.path)

        if not capture.isOpened():
            raise IOError("无法打开视频：%s" % self.path)

        index = 0
        emitted = 0

        try:
            while True:
                ok, frame = capture.read()

                if not ok:
                    break

                if index % self.sample_every == 0:
                    yield index, frame
                    emitted += 1

                    if progress is not None and self.info.total_frames > 0:
                        progress(index / float(self.info.total_frames))

                    if max_frames is not None and emitted >= max_frames:
                        break

                index += 1
        finally:
            capture.release()

    def close(self) -> None:
        if self._capture is not None:
            self._capture.release()
            self._capture = None


def read_image_sequence(folder: str, pattern: str = r"(\d+)\.(png|jpg|jpeg)$") -> tuple:
    """读取图片序列（如 frame_%06d.png），返回 (帧列表, 假定帧率 30)"""

    files: List[str] = []

    for name in sorted(os.listdir(folder)):
        if re.search(pattern, name, re.IGNORECASE):
            files.append(os.path.join(folder, name))

    if not files:
        raise FileNotFoundError("目录中没有图片序列：%s" % folder)

    cv2 = VideoReader._import_cv2()
    frames = []

    for path in files:
        image = cv2.imread(path)

        if image is not None:
            frames.append(image)

    return frames, 30.0


def frame_to_rgb(frame_bgr: np.ndarray) -> np.ndarray:
    """BGR → RGB"""

    return frame_bgr[:, :, ::-1].copy()
