#!/usr/bin/env python3
"""design-tokens.json -> android-core/app/.../ui/theme/Tokens.kt

사용법: python3 tools/gen-tokens/gen_tokens.py  (저장소 루트에서)
생성 파일은 직접 고치지 않는다. 원본(design-tokens.json)을 고치고 다시 실행한다.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "design-tokens.json"
OUT = ROOT / "android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt"


def kotlin_name(key: str) -> str:
    return "".join(part[:1].upper() + part[1:].lower() for part in key.split("."))


def color_literal(hex_value: str) -> str:
    value = hex_value.lstrip("#").upper()
    if len(value) != 6:
        raise ValueError(f"색은 #RRGGBB 형식이어야 한다: {hex_value}")
    return f"Color(0xFF{value})"


def main() -> None:
    tokens = json.loads(SRC.read_text(encoding="utf-8"))
    lines = [
        "// 생성 파일: tools/gen-tokens/gen_tokens.py가 design-tokens.json에서 만든다. 직접 고치지 말 것.",
        "package com.chronoquest.app.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.unit.Dp",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.sp",
        "",
        "object ChronoTokens {",
        "    object Colors {",
    ]
    for key, value in tokens["color"].items():
        lines.append(f"        val {kotlin_name(key)} = {color_literal(value)}")
    lines.append("")
    lines.append("        val byKey: Map<String, Color> = mapOf(")
    for key in tokens["color"]:
        lines.append(f'            "{key}" to {kotlin_name(key)},')
    lines.append("        )")
    lines.append("    }")
    lines.append("")
    lines.append("    object Tier {")
    for key, value in tokens["tier"].items():
        lines.append(f"        val {kotlin_name(key)} = {color_literal(value)}")
    if tokens["tier"]:
        lines.append("")
    lines.append("        val all: Map<String, Color> = mapOf(")
    for key in tokens["tier"]:
        lines.append(f'            "{key}" to {kotlin_name(key)},')
    lines.append("        )")
    lines.append("    }")
    lines.append("")
    lines.append("    object TypeScale {")
    for size in tokens["typeScale"]:
        lines.append(f"        val S{size} = {size}.sp")
    lines.append("")
    sizes = ", ".join(str(s) for s in tokens["typeScale"])
    lines.append(f"        val sizes: List<Int> = listOf({sizes})")
    lines.append("    }")
    for obj, key in (("Space", "space"), ("Radius", "radius")):
        lines.append("")
        lines.append(f"    object {obj} {{")
        for name, value in tokens[key].items():
            lines.append(f"        val {kotlin_name(name)} = {int(value)}.dp")
        lines.append("")
        lines.append("        val all: Map<String, Dp> = mapOf(")
        for name in tokens[key]:
            lines.append(f'            "{name}" to {kotlin_name(name)},')
        lines.append("        )")
        lines.append("    }")
    lines.append("}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
