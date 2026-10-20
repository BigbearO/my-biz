# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path

from PyInstaller.utils.hooks import collect_submodules

project_root = Path(SPECPATH)
hiddenimports = (
    collect_submodules("uvicorn")
    + collect_submodules("alembic", filter=lambda name: not name.startswith("alembic.testing"))
    + collect_submodules("xianyu_client")
    + collect_submodules("xianyu")
    + [
        "aiosqlite",
        "sqlalchemy.dialects.sqlite",
        "sqlalchemy.dialects.sqlite.aiosqlite",
    ]
)

# Alembic migrations are runtime data, not importable package resources.
datas = [
    (str(project_root / "alembic"), "alembic"),
]

a = Analysis(
    [str(project_root / "xianyu_client" / "__main__.py")],
    pathex=[str(project_root)],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="xianyu-client",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    name="xianyu-client",
)