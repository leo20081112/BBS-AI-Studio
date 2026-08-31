# -*- mode: python ; coding: utf-8 -*-
#
# BBS AI Studio 外部工具链 —— PyInstaller 打包配置
#
# 打包命令：pyinstaller bbs_ai_toolchain.spec
# 产物：dist/bbs-ai-toolchain/bbs-ai-toolchain.exe（目录模式，mediapipe 体积较大）
#
# 作者：BBS AI Studio
#

import os

block_cipher = None

a = Analysis(
    ["main.py"],
    pathex=[os.path.dirname(os.path.abspath("main.py"))],
    binaries=[],
    datas=[
        ("README.md", "."),
    ],
    hiddenimports=[
        "core",
        "core.motion_data",
        "core.video_reader",
        "core.pose_estimator",
        "core.skeleton_mapper",
        "core.exporter",
        "core.config_loader",
        "core.api_provider",
        "core.openai_adapter",
        "core.claude_adapter",
        "core.deepseek_adapter",
        "core.glm_adapter",
        "core.batch_processor",
        "gradio",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="bbs-ai-toolchain",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
    icon=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    name="bbs-ai-toolchain",
)
