# -*- mode: python ; coding: utf-8 -*-
#
# BBS AI Studio 外部工具链 —— PyInstaller 打包配置
#
# 打包命令：pyinstaller --noconfirm --clean bbs_ai_toolchain.spec
# 产物：dist/bbs-ai-toolchain-windows-x64/ 目录
#   - bbs-ai-toolchain.exe      图形界面（Gradio 深色主题）
#   - bbs-ai-toolchain-cli.exe  命令行（convert / batch / test-connection）
#
# 打包前需安装：pip install -r requirements-build.txt
#
# 作者：BBS AI Studio
#

import os
from PyInstaller.utils.hooks import collect_all

block_cipher = None

# 收集重资源包的完整文件（数据 + 二进制 + 隐式导入）：
#   gradio       前端静态资源与元数据
#   cv2          OpenCV 原生库与配置
#   onnxruntime  RTMPose 后端推理引擎
# 注意：不打包 mediapipe —— 它与 PyInstaller 冻结环境不兼容
# （_framework_bindings DLL 初始化失败），打包版请使用 rtmpose 后端
datas = []
binaries = []
hiddenimports = []

for package in ('gradio', 'cv2', 'onnxruntime'):
    package_datas, package_binaries, package_hidden = collect_all(package)

    datas += package_datas
    binaries += package_binaries
    hiddenimports += package_hidden

shared_hidden = [
    'core',
    'core.motion_data',
    'core.video_reader',
    'core.pose_estimator',
    'core.skeleton_mapper',
    'core.exporter',
    'core.config_loader',
    'core.api_provider',
    'core.openai_adapter',
    'core.claude_adapter',
    'core.deepseek_adapter',
    'core.glm_adapter',
    'core.batch_processor',
]

# ------------------------------------------------------------------
# 图形界面入口
# ------------------------------------------------------------------
gui = Analysis(
    ['main.py'],
    pathex=[os.path.dirname(os.path.abspath('main.py'))],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports + shared_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['mediapipe'],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

gui_pyz = PYZ(gui.pure, gui.zipped_data, cipher=block_cipher)

gui_exe = EXE(
    gui_pyz,
    gui.scripts,
    [],
    exclude_binaries=True,
    name='bbs-ai-toolchain',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
    icon=None,
)

# ------------------------------------------------------------------
# 命令行入口
# ------------------------------------------------------------------
cli = Analysis(
    ['cli.py'],
    pathex=[os.path.dirname(os.path.abspath('cli.py'))],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports + shared_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['mediapipe'],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

cli_pyz = PYZ(cli.pure, cli.zipped_data, cipher=block_cipher)

cli_exe = EXE(
    cli_pyz,
    cli.scripts,
    [],
    exclude_binaries=True,
    name='bbs-ai-toolchain-cli',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
    icon=None,
)

coll = COLLECT(
    gui_exe,
    cli_exe,
    gui.binaries,
    gui.zipfiles,
    gui.datas,
    cli.binaries,
    cli.zipfiles,
    cli.datas,
    strip=False,
    upx=False,
    name='bbs-ai-toolchain-windows-x64',
)
