"""WCAG 2.x contrast audit of the real colour tokens in designsystem/Color.kt (no rendering needed)."""
import re, sys
from pathlib import Path

# Run from anywhere: python3 tools/contrast_audit.py   (exit code 1 if any text/UI pair fails WCAG AA)
SRC = Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/aipromptbridge/designsystem/Color.kt"
s = SRC.read_text(encoding="utf-8")

def block(name):
    start = s.index(name); end = s.index("\n)", start)
    return s[start:end]

def parse(block_text):
    out = {}
    for key, a, rgb in re.findall(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)", block_text):
        out[key] = (int(a, 16) / 255.0, tuple(int(rgb[i:i+2], 16) for i in (0, 2, 4)))
    return out

def lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
def lum(rgb): r, g, b = rgb; return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
def over(fg, bg):  # alpha-composite fg (alpha, rgb) over an opaque bg rgb
    a, c = fg; return tuple(round(a * c[i] + (1 - a) * bg[i]) for i in range(3))
def ratio(fg_rgb, bg_rgb):
    l1, l2 = sorted((lum(fg_rgb), lum(bg_rgb)), reverse=True); return (l1 + 0.05) / (l2 + 0.05)

themes = {
  "light": (parse(block("val LightColors")), parse(block("val LightExtraColors"))),
  "dark":  (parse(block("val DarkColors")),  parse(block("val DarkExtraColors"))),
}
def rgb(d, k): return d[k][1]

# (description, foreground token, background token, threshold)  — "x:" prefix = extra colours
TEXT = 4.5; UI = 3.0
pairs = [
  ("body text on page",                "onBackground", "background", TEXT),
  ("body text on sheet/dialog",        "onSurface", "surfaceContainerLowest", TEXT),
  ("text on chips / panels",           "onSurface", "surfaceContainer", TEXT),
  ("subtitle on page",                 "onSurfaceVariant", "background", TEXT),
  ("subtitle on sheet/dialog",         "onSurfaceVariant", "surfaceContainerLowest", TEXT),
  ("secondary text on chips/panels",   "onSurfaceVariant", "surfaceContainer", TEXT),
  ("selected chip / button label",     "onPrimary", "primary", TEXT),
  ("accent text (Active, links) page", "primary", "background", TEXT),
  ("accent text in dialogs",           "primary", "surfaceContainerLowest", TEXT),
  ("accent text on chips",             "primary", "surfaceContainer", TEXT),
  ("error text on page",               "error", "background", TEXT),
  ("error text in dialogs",            "error", "surfaceContainerLowest", TEXT),
  ("selection pill 'secondary' text",  "secondary", "surfaceContainer", TEXT),
  ("success text on page",             "x:success", "background", TEXT),
  ("diff: inserted text",              "x:insertText", "x:insertBackground", TEXT),
  ("diff: deleted text on sheet",      "x:deleteText", "surfaceContainerLowest", TEXT),
  ("UI: field border / unchecked switch vs page", "outline", "background", UI),
  ("UI: field border vs sheet/dialog", "outline", "surfaceContainerLowest", UI),
  ("UI: selected chip vs page (shape)", "primary", "background", UI),
  ("UI: chip fill vs page (shape)",    "surfaceContainer", "background", 1.0),  # informational only
]

fails = 0
for theme, (c, x) in themes.items():
    print(f"\n== {theme}")
    for desc, fg, bg, need in pairs:
        def get(k):
            if k.startswith("x:"): d = x; k = k[2:]
            else: d = c
            return d[k]
        page = rgb(c, "background")
        bg_a, bg_rgb = get(bg); bg_rgb = over((bg_a, bg_rgb), page) if bg_a < 1 else bg_rgb  # translucent bg composited on the page
        fg_a, fg_rgb = get(fg); fg_rgb = over((fg_a, fg_rgb), bg_rgb) if fg_a < 1 else fg_rgb
        r = ratio(fg_rgb, bg_rgb)
        ok = r >= need
        info = need == 1.0
        flag = "info" if info else ("ok  " if ok else "FAIL")
        if not ok and not info: fails += 1
        print(f"  {flag} {r:5.2f}:1  (need {need:g})  {desc}")
print(f"\n{fails} failing pair(s)")
sys.exit(1 if fails else 0)
