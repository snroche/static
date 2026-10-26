"""Re-subsets the terminal font (app/src/main/res/font/terminal_*.ttf).

Keeps every character the font has now, adds the icons in NEW from the full JetBrains Mono Nerd
Font, and aliases each icon into the BMP private area (U+E000 + icon - U+F0000) so XML resources
can use it (see ResourceEncodingTest). Also adds a string per new icon to res/values/icons.xml.

Needs fontTools (pip install fonttools) and the full fonts, Regular and Bold, saved as
JBM-Regular.ttf / JBM-Bold.ttf in NERD_FONT_DIR (default D:\\Android\\fonts), from:
https://github.com/ryanoasis/nerd-fonts/raw/v3.2.1/patched-fonts/JetBrainsMono/Ligatures/<Weight>/JetBrainsMonoNerdFont-<Weight>.ttf

To add an icon: put its Nerd Font codepoint (U+F0000 range, https://www.nerdfonts.com/cheat-sheet)
in NEW with a name, then run: python tools/font_subset.py
"""
import os

from fontTools import subset
from fontTools.ttLib import TTFont

SRC = os.environ.get("NERD_FONT_DIR", r"D:\Android\fonts")
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")

NEW = {
    "ic_stats": 0xF0128, "ic_file": 0xF0148, "ic_orders": 0xF00E6, "ic_backspace": 0xF0B5C,
    "ic_bed": 0xF02E3, "ic_dumbbell": 0xF01E6, "ic_timer": 0xF051B, "ic_walk": 0xF0583,
    "ic_marker": 0xF034E, "ic_crown": 0xF01A5, "ic_levelup": 0xF0DBC, "ic_brain": 0xF09D1,
    "ic_pulse": 0xF05F6, "ic_tools": 0xF1323, "ic_infinity": 0xF06E4, "ic_swap": 0xF04E1,
    "ic_percent": 0xF03F0, "ic_bike": 0xF00A3, "ic_motorbike": 0xF037C, "ic_truck": 0xF053D,
    "ic_fuel": 0xF07CA, "ic_target": 0xF04FE, "ic_wrench": 0xF05B7, "ic_chips": 0xF0830,
    "ic_handshake": 0xF1218, "ic_blueprint": 0xF0821, "ic_recycle": 0xF044C, "ic_upgrade": 0xF0738,
    "ic_courier": 0xF053E, "ic_package": 0xF03D6,
    "ic_fair": 0xF07C7,  # storefront: the Fair on the map
}

for weight in ("Regular", "Bold"):
    out = os.path.join(RES, "font", "terminal_%s.ttf" % weight.lower())
    have = TTFont(out).getBestCmap()
    keep = {c for c in have if not (0xE000 <= c <= 0xF8FF)}
    keep |= set(NEW.values())
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.hinting = True
    font = TTFont(os.path.join(SRC, "JBM-%s.ttf" % weight))
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=sorted(keep))
    sub.subset(font)
    best = {}
    for table in font["cmap"].tables:
        if table.format == 12:
            best.update(table.cmap)
    for c, g in list(best.items()):
        if c >= 0xF0000:
            best[0xE000 + (c - 0xF0000)] = g
    for table in font["cmap"].tables:
        if not table.isUnicode():
            continue
        table.cmap = dict(best) if table.format == 12 else {c: g for c, g in best.items() if c <= 0xFFFF}
    missing = [n for n, c in NEW.items() if c not in best]
    assert not missing, missing
    lost = [hex(c) for c in have if c not in best]
    assert not lost, "characters lost: %s" % lost[:10]
    font.save(out)
    print(weight, os.path.getsize(out), len(best))

icons = os.path.join(RES, "values", "icons.xml")
s = open(icons, encoding="utf-8", newline="").read()
nl = "\r\n" if "\r\n" in s else "\n"
add = ""
for name, c in NEW.items():
    if 'name="%s"' % name not in s:
        add += '    <string name="%s" translatable="false">&#x%X;</string>%s' % (name, 0xE000 + (c - 0xF0000), nl)
s = s.replace("</resources>", add + "</resources>")
open(icons, "w", encoding="utf-8", newline="").write(s)
print("icons.xml up to date")
