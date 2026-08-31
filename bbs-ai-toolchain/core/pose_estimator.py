#
# BBS AI Studio 外部工具链
#
# 姿态估计器：MediaPipe Pose（33 点，推荐）/ YOLOv8-pose（17 点）/ RTMPose（SimCC）。
# 统一输出 COCO-17 关键点中间表示（与游戏内 PoseKeypoints 对齐）。
#
# 作者：BBS AI Studio
#

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Optional

import numpy as np

# COCO 17 关键点索引
NOSE, LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR = 0, 1, 2, 3, 4
LEFT_SHOULDER, RIGHT_SHOULDER = 5, 6
LEFT_ELBOW, RIGHT_ELBOW = 7, 8
LEFT_WRIST, RIGHT_WRIST = 9, 10
LEFT_HIP, RIGHT_HIP = 11, 12
LEFT_KNEE, RIGHT_KNEE = 13, 14
LEFT_ANKLE, RIGHT_ANKLE = 15, 16

KEYPOINT_COUNT = 17


@dataclass
class PoseKeypoints:
    """单帧 COCO-17 关键点（归一化 0~1 坐标 + 置信度）"""

    x: np.ndarray
    y: np.ndarray
    confidence: np.ndarray
    source_frame: int = 0
    frame_confidence: float = 0.0

    def valid(self, index: int, min_confidence: float) -> bool:
        return 0 <= index < KEYPOINT_COUNT and self.confidence[index] >= min_confidence


class PoseEstimator:
    """姿态估计器基类（统一接口：estimate(frame_rgb) -> PoseKeypoints）"""

    name = "base"

    def estimate(self, frame_rgb: np.ndarray, source_frame: int = 0) -> PoseKeypoints:
        raise NotImplementedError

    def close(self) -> None:
        pass

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()


def _empty_keypoints(source_frame: int) -> PoseKeypoints:
    return PoseKeypoints(
        x=np.zeros(KEYPOINT_COUNT, dtype=np.float32),
        y=np.zeros(KEYPOINT_COUNT, dtype=np.float32),
        confidence=np.zeros(KEYPOINT_COUNT, dtype=np.float32),
        source_frame=source_frame,
    )


class MediaPipePoseEstimator(PoseEstimator):
    """MediaPipe Pose（33 关键点 → COCO 17 映射，轻量推荐）"""

    name = "mediapipe_pose"

    def __init__(self, model_complexity: int = 1, min_detection_confidence: float = 0.5):
        try:
            import mediapipe as mp
        except ImportError as exc:
            raise ImportError("需要 mediapipe：pip install mediapipe") from exc

        self._mp_pose = mp.solutions.pose
        self._pose = self._mp_pose.Pose(
            static_image_mode=False,
            model_complexity=model_complexity,
            min_detection_confidence=min_detection_confidence,
        )

        # MediaPipe Pose 33 点 → COCO 17 映射
        self._mapping = {
            NOSE: self._mp_pose.PoseLandmark.NOSE,
            LEFT_EYE: self._mp_pose.PoseLandmark.LEFT_EYE,
            RIGHT_EYE: self._mp_pose.PoseLandmark.RIGHT_EYE,
            LEFT_EAR: self._mp_pose.PoseLandmark.LEFT_EAR,
            RIGHT_EAR: self._mp_pose.PoseLandmark.RIGHT_EAR,
            LEFT_SHOULDER: self._mp_pose.PoseLandmark.LEFT_SHOULDER,
            RIGHT_SHOULDER: self._mp_pose.PoseLandmark.RIGHT_SHOULDER,
            LEFT_ELBOW: self._mp_pose.PoseLandmark.LEFT_ELBOW,
            RIGHT_ELBOW: self._mp_pose.PoseLandmark.RIGHT_ELBOW,
            LEFT_WRIST: self._mp_pose.PoseLandmark.LEFT_WRIST,
            RIGHT_WRIST: self._mp_pose.PoseLandmark.RIGHT_WRIST,
            LEFT_HIP: self._mp_pose.PoseLandmark.LEFT_HIP,
            RIGHT_HIP: self._mp_pose.PoseLandmark.RIGHT_HIP,
            LEFT_KNEE: self._mp_pose.PoseLandmark.LEFT_KNEE,
            RIGHT_KNEE: self._mp_pose.PoseLandmark.RIGHT_KNEE,
            LEFT_ANKLE: self._mp_pose.PoseLandmark.LEFT_ANKLE,
            RIGHT_ANKLE: self._mp_pose.PoseLandmark.RIGHT_ANKLE,
        }

    def estimate(self, frame_rgb: np.ndarray, source_frame: int = 0) -> PoseKeypoints:
        result = self._pose.process(frame_rgb)

        if result.pose_landmarks is None:
            return _empty_keypoints(source_frame)

        keypoints = _empty_keypoints(source_frame)
        landmarks = result.pose_landmarks.landmark
        total = 0.0

        for coco_index, mp_index in self._mapping.items():
            landmark = landmarks[mp_index.value]

            keypoints.x[coco_index] = landmark.x
            keypoints.y[coco_index] = landmark.y
            keypoints.confidence[coco_index] = landmark.visibility
            total += landmark.visibility

        keypoints.frame_confidence = total / KEYPOINT_COUNT

        return keypoints

    def close(self) -> None:
        self._pose.close()


class YoloPoseEstimator(PoseEstimator):
    """YOLOv8-pose（ultralytics，17 关键点，COCO 原生）"""

    name = "yolov8"

    def __init__(self, model_path: str = "yolov8n-pose.pt", device: Optional[str] = None):
        try:
            from ultralytics import YOLO
        except ImportError as exc:
            raise ImportError("需要 ultralytics：pip install ultralytics") from exc

        self._model = YOLO(model_path)
        self._device = device

    def estimate(self, frame_rgb: np.ndarray, source_frame: int = 0) -> PoseKeypoints:
        results = self._model.predict(frame_rgb, verbose=False, device=self._device)

        if not results or results[0].keypoints is None or len(results[0].boxes) == 0:
            return _empty_keypoints(source_frame)

        keypoints_tensor = results[0].keypoints

        if keypoints_tensor.conf is None or len(keypoints_tensor.conf) == 0:
            return _empty_keypoints(source_frame)

        # 取置信度最高的人
        person_scores = results[0].boxes.conf.cpu().numpy()
        person = int(np.argmax(person_scores))

        points = keypoints_tensor.data[person].cpu().numpy()  # (17, 3)

        height, width = frame_rgb.shape[:2]

        keypoints = _empty_keypoints(source_frame)
        keypoints.x = points[:, 0] / max(1, width)
        keypoints.y = points[:, 1] / max(1, height)
        keypoints.confidence = points[:, 2]
        keypoints.frame_confidence = float(person_scores[person])

        return keypoints


class RtmposeEstimator(PoseEstimator):
    """RTMPose（ONNX SimCC 输出，单人，输入 192x256）"""

    name = "rtmpose"

    def __init__(self, model_path: str, input_size=(192, 256)):
        try:
            import onnxruntime as ort
        except ImportError as exc:
            raise ImportError("需要 onnxruntime：pip install onnxruntime") from exc

        self._session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
        self._input_name = self._session.get_inputs()[0].name
        self._input_w, self._input_h = input_size

    def estimate(self, frame_rgb: np.ndarray, source_frame: int = 0) -> PoseKeypoints:
        import cv2

        resized = cv2.resize(frame_rgb, (self._input_w, self._input_h)).astype(np.float32) / 255.0
        blob = resized.transpose(2, 0, 1)[None]  # 1x3xHxW

        outputs = self._session.run(None, {self._input_name: blob})

        if len(outputs) < 2:
            return _empty_keypoints(source_frame)

        simcc_x, simcc_y = outputs[0][0], outputs[1][0]  # (17, W/2), (17, H/2)

        keypoints = _empty_keypoints(source_frame)

        for k in range(KEYPOINT_COUNT):
            argx = int(np.argmax(simcc_x[k]))
            argy = int(np.argmax(simcc_y[k]))

            keypoints.x[k] = (argx / 2.0) / self._input_w
            keypoints.y[k] = (argy / 2.0) / self._input_h
            keypoints.confidence[k] = float(simcc_x[k][argx] / max(1, simcc_x.shape[1]))

        keypoints.frame_confidence = float(np.mean(keypoints.confidence))

        return keypoints

    def close(self) -> None:
        pass


def create_estimator(backend: str, **kwargs) -> PoseEstimator:
    """工厂：按名称创建估计器"""

    backend = (backend or "mediapipe").lower()

    if backend in ("mediapipe", "mediapipe_pose"):
        return MediaPipePoseEstimator(**kwargs)

    if backend in ("yolo", "yolov8"):
        return YoloPoseEstimator(**kwargs)

    if backend == "rtmpose":
        model_path = kwargs.pop("model_path", None)

        if not model_path:
            raise ValueError("RTMPose 需要 model_path 参数（ONNX 文件）")

        return RtmposeEstimator(model_path, **kwargs)

    raise ValueError("未知姿态估计后端：%s" % backend)


def estimate_video(frames, backend: str = "mediapipe", progress=None, **kwargs) -> List[PoseKeypoints]:
    """批量估计视频帧（BGR 帧列表），返回关键点列表"""

    results: List[PoseKeypoints] = []

    with create_estimator(backend, **kwargs) as estimator:
        for index, frame in enumerate(frames):
            results.append(estimator.estimate(frame[:, :, ::-1], source_frame=index))

            if progress is not None:
                progress((index + 1) / float(len(frames)))

    return results
