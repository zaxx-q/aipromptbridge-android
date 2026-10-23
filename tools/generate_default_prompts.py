#!/usr/bin/env python3
"""
Generates core/.../prompt/DefaultPromptsData.kt from the ORIGINAL Python prompts.py.

Why a script: the prompts are large (Humanize alone is ~19 KB). Generating them avoids retyping errors.
Re-run after editing the mapping tables below:

    python3 tools/generate_default_prompts.py

Scope in v1: text-edit + snip tools. Skipped on purpose: "compare" actions (need two inputs), audio and TTS tools.
"""
import importlib.util
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "reference/original-python/prompts.py"
OUT = ROOT / "core/src/main/kotlin/com/aipromptbridge/core/prompt/DefaultPromptsData.kt"

spec = importlib.util.spec_from_file_location("orig_prompts", SRC)
orig = importlib.util.module_from_spec(spec)
spec.loader.exec_module(orig)

# ---- mapping tables (edit these, not the generated file) -------------------------------------------------------
TEXT_LABELS = {"Proofread": "Fix Grammar", "Humanize": "Humanizer", "Summary": "Summarize"}
TEXT_BAR = ["proofread", "humanize", "rewrite", "casual", "summary", "explain", "key_points"]
SNIP_BAR = ["quick_extract", "smart_extract", "to_markdown", "describe", "explain", "answer", "summarize"]

# Original emoji icons -> icon ids understood by app/.../designsystem/AipbIcons.kt
TEXT_ICONS = {
    "Explain": "lightbulb", "ELI5": "child_care", "Explain Slang/Meme": "mood", "ESL Breakdown": "public",
    "Summary": "summarize", "Digest": "library_books", "Extract Data": "table_chart", "Key Points": "key",
    "Proofread": "spellcheck", "Refine": "auto_fix_high", "Rewrite": "edit_note", "Paraphrase": "swap_horiz",
    "Professional": "work", "Friendly": "mood", "Casual": "chat", "Concise": "content_cut", "Humanize": "face",
    "Table": "table_chart", "Continue": "arrow_forward", "Reply Suggest": "reply", "Emojify": "mood",
    "Kaomojify": "mood", "Kaomoji Suggest": "mood", "Answer": "check", "Translate to English": "translate",
    "Translate to Indonesian": "translate", "Explain Code": "code", "Code Review": "rate_review",
    "Debug": "bug_report", "Refactor": "build", "Document": "description", "_Custom": "bolt", "_Ask": "help",
}
SNIP_ICONS = {
    "Explain": "lightbulb", "Describe": "image", "Summarize": "summarize", "Answer": "check",
    "Quick Extract": "bolt", "Exact Extract": "visibility", "Smart Extract": "psychology",
    "To Markdown": "description", "Handwriting Cleanup": "edit_note", "Translate to English": "translate",
    "_Custom": "bolt",
}
MODIFIER_ICONS = {
    "variations": "format_list_numbered", "direct": "bolt", "language": "translate", "explain": "info",
    "creative": "auto_fix_high", "literal": "description", "shorter": "content_cut", "longer": "add",
    "formal": "work", "informal": "chat", "global": "public",
}
SETTING_KEYS = {  # original key -> Kotlin constant in SettingKeys
    "chat_system_instruction": "CHAT_SYSTEM_INSTRUCTION",
    "base_output_rules_edit": "BASE_OUTPUT_RULES_EDIT",
    "base_output_rules_general": "BASE_OUTPUT_RULES_GENERAL",
    "text_delimiter": "TEXT_DELIMITER",
    "text_delimiter_close": "TEXT_DELIMITER_CLOSE",
    "custom_task_template": "CUSTOM_TASK_TEMPLATE",
    "ask_task_template": "ASK_TASK_TEMPLATE",
}
# -----------------------------------------------------------------------------------------------------------------


def kt(s: str) -> str:
    """Kotlin string literal. '$' must be escaped because Kotlin uses it for templates."""
    s = (s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
          .replace("\r", "\\r").replace("\t", "\\t").replace("\n", "\\n"))
    return '"' + s + '"'


def slug(key: str) -> str:
    if key == "_Custom":
        return "_custom"
    if key == "_Ask":
        return "_ask"
    return re.sub(r"[^a-z0-9]+", "_", key.lower()).strip("_")


def build_actions(actions: dict, icons: dict, labels: dict, tool: str):
    out, kept = [], {}
    for key, a in actions.items():
        if a.get("compare_prompts"):
            continue  # skipped: compare actions need two inputs (future work)
        aid = slug(key)
        kept[key] = aid
        if tool == "text":
            ptype = "EDIT" if a.get("prompt_type", "edit") == "edit" else "GENERAL"
            show_chat = bool(a.get("show_chat_window_instead_of_replace", False))
        else:
            ptype = "GENERAL"
            show_chat = bool(a.get("show_chat_window", True))
        label = "Custom" if key == "_Custom" else "Ask" if key == "_Ask" else labels.get(key, key)
        out.append(
            "        PromptAction(\n"
            f"            id = {kt(aid)},\n"
            f"            label = {kt(label)},\n"
            f"            icon = {kt(icons.get(key, 'auto_fix_high'))},\n"
            f"            systemPrompt = {kt(a.get('system_prompt', ''))},\n"
            f"            task = {kt(a.get('task', ''))},\n"
            f"            promptType = PromptType.{ptype},\n"
            f"            showInChat = {'true' if show_chat else 'false'},\n"
            "        ),"
        )
    return out, kept


def build_groups(groups: list, kept: dict):
    out = []
    for g in groups:
        ids = [kept[i] for i in g["items"] if i in kept and not kept[i].startswith("_")]
        if not ids:
            continue
        items = ", ".join(kt(i) for i in ids)
        out.append(f"        PromptGroup({kt(g['name'])}, {'true' if g.get('enabled', True) else 'false'}, listOf({items})),")
    return out


def build_settings(settings: dict):
    lines = []
    for okey, const in SETTING_KEYS.items():
        if okey in settings and isinstance(settings[okey], str):
            lines.append(f"        SettingKeys.{const} to {kt(settings[okey])},")
    return lines


g = orig.DEFAULT_GLOBAL_SETTINGS
text_actions, text_kept = build_actions(orig.DEFAULT_TEXT_EDIT_ACTIONS, TEXT_ICONS, TEXT_LABELS, "text")
snip_actions, snip_kept = build_actions(orig.DEFAULT_SNIP_ACTIONS, SNIP_ICONS, {}, "snip")

mods = []
for m in g["modifiers"]:
    mods.append(
        "        PromptModifier(\n"
        f"            key = {kt(m['key'])},\n"
        f"            icon = {kt(MODIFIER_ICONS.get(m['key'], 'tune'))},\n"
        f"            label = {kt(m['label'])},\n"
        f"            tooltip = {kt(m.get('tooltip', ''))},\n"
        f"            injection = {kt(m.get('injection', ''))},\n"
        f"            forcesChat = {'true' if m.get('forces_chat_window') else 'false'},\n"
        "        ),"
    )

for bar, kept in ((TEXT_BAR, text_kept), (SNIP_BAR, snip_kept)):
    missing = [b for b in bar if b not in kept.values()]
    assert not missing, f"bar ids missing from actions: {missing}"

nl = "\n"
src = f'''package com.aipromptbridge.core.prompt

// GENERATED by tools/generate_default_prompts.py from the original AIPromptBridge prompts.py.
// DO NOT EDIT BY HAND: change the mapping tables in the script and re-run it.

internal object DefaultPromptsData {{
    const val CHAT_WINDOW_SYSTEM_INSTRUCTION: String = {kt(g["chat_window_system_instruction"])}

    val modifiers: List<PromptModifier> = listOf(
{nl.join(mods)}
    )

    val textEditSettings: Map<String, String> = mapOf(
{nl.join(build_settings(orig.DEFAULT_TEXT_EDIT_SETTINGS))}
    )

    val textEditGroups: List<PromptGroup> = listOf(
{nl.join(build_groups(orig.DEFAULT_TEXT_EDIT_SETTINGS["popup_groups"], text_kept))}
    )

    val textEditBarOrder: List<String> = listOf({", ".join(kt(b) for b in TEXT_BAR)})

    val textEditActions: List<PromptAction> = listOf(
{nl.join(text_actions)}
    )

    val snipSettings: Map<String, String> = mapOf(
{nl.join(build_settings(orig.DEFAULT_SNIP_SETTINGS))}
    )

    val snipGroups: List<PromptGroup> = listOf(
{nl.join(build_groups(orig.DEFAULT_SNIP_SETTINGS["popup_groups"], snip_kept))}
    )

    val snipBarOrder: List<String> = listOf({", ".join(kt(b) for b in SNIP_BAR)})

    val snipActions: List<PromptAction> = listOf(
{nl.join(snip_actions)}
    )
}}
'''
OUT.write_text(src, encoding="utf-8")
print(f"wrote {OUT} ({len(src)//1024} KB): {len(text_actions)} text actions, {len(snip_actions)} snip actions, {len(mods)} modifiers")
