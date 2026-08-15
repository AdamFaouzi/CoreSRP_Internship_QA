#!/usr/bin/env python3
"""Generate square 'evidence card' images for the QA findings video (dark + gold theme)."""
from PIL import Image, ImageDraw, ImageFont
import os

OUT = "QAVID/assets/images"
os.makedirs(OUT, exist_ok=True)
S = 1080
INK = (10, 14, 26)           # background
PANEL = (18, 24, 40)         # evidence panel
GOLD = (201, 162, 39)
GOLD_L = (232, 206, 122)
WHITE = (238, 240, 245)
MUTED = (150, 160, 178)
AMBER = (224, 168, 46)
RED = (214, 90, 74)
GREEN = (77, 200, 140)

HELV = "/System/Library/Fonts/HelveticaNeue.ttc"
MENLO = "/System/Library/Fonts/Menlo.ttc"

def font(path, size, index=0):
    return ImageFont.truetype(path, size, index=index)

# HelveticaNeue.ttc indices vary; 0 regular-ish. Try bold via index fallback.
def helv(size, bold=False):
    try:
        return ImageFont.truetype(HELV, size, index=(2 if bold else 0))
    except Exception:
        return ImageFont.truetype(HELV, size)

def mono(size):
    return ImageFont.truetype(MENLO, size, index=0)

def wrap(draw, text, fnt, maxw):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=fnt) <= maxw:
            cur = t
        else:
            lines.append(cur); cur = w
    if cur:
        lines.append(cur)
    return lines

def rounded(draw, box, r, fill):
    draw.rounded_rectangle(box, radius=r, fill=fill)

def card(name, sev, sev_color, title, evidence_lines, caption):
    img = Image.new("RGB", (S, S), INK)
    d = ImageDraw.Draw(img)
    M = 84
    # severity chip
    chip_f = helv(30, bold=True)
    tw = d.textlength(sev, font=chip_f)
    rounded(d, [M, M, M + tw + 56, M + 58], 29, sev_color)
    d.text((M + 28, M + 12), sev, font=chip_f, fill=INK)
    # kicker
    d.text((M, M + 92), "FINDING", font=helv(26, bold=True), fill=GOLD)
    # title
    tf = helv(62, bold=True)
    y = M + 132
    for ln in wrap(d, title, tf, S - 2 * M):
        d.text((M, y), ln, font=tf, fill=WHITE); y += 74
    # gold rule
    y += 14
    d.rectangle([M, y, M + 150, y + 6], fill=GOLD); y += 46
    # evidence panel
    ph = 44 + len(evidence_lines) * 52 + 20
    rounded(d, [M, y, S - M, y + ph], 20, PANEL)
    d.rectangle([M, y, M + 8, y + ph], fill=GOLD)  # accent border
    ef = mono(32)
    ey = y + 30
    for ln, col in evidence_lines:
        d.text((M + 40, ey), ln, font=ef, fill=col); ey += 52
    y += ph + 40
    # caption
    cf = helv(30)
    for ln in wrap(d, caption, cf, S - 2 * M):
        d.text((M, y), ln, font=cf, fill=MUTED); y += 40
    img.save(os.path.join(OUT, name + ".jpg"), quality=92)
    print("wrote", name)

card("f1_csv", "MEDIUM", AMBER,
     "CSV / formula injection in exports",
     [("vendor name saved as:", MUTED),
      ("=1+1+cmd|' /C calc'!A0", RED),
      ("exported to CSV, unescaped:", MUTED),
      ("...,=1+1+cmd|' /C calc'!A0,...", RED)],
     "Opening the export in Excel evaluates it as a formula. Fix: prefix =,+,-,@ cells on export.")

card("f2_enum", "MEDIUM", AMBER,
     "Password-reset account enumeration",
     [("registered email   -> 202", RED),
      ("unknown email      -> 422", RED),
      ("(UI copy implies neither)", MUTED)],
     "Different responses let an attacker enumerate which emails have accounts.")

card("f3_throttle", "MEDIUM", AMBER,
     "Login throttling gap",
     [("6 rapid failed logins:", MUTED),
      ("400 400 400 400 400 400", RED),
      ("no 429, no lockout", RED)],
     "Low-volume brute force is not throttled; rate limiting only triggers under heavy load.")

card("f4_accounting", "MEDIUM", AMBER,
     "Negative total corrupts the ledger",
     [("grand_total:  -500.00", RED),
      ("is_return:    false", MUTED),
      ("journal debit: -500.00", RED)],
     "A negative debit is accounting-invalid and flows into every GL export.")

# a 'what held up' positive card for balance
card("f5_solid", "VERIFIED", GREEN,
     "What held up under attack",
     [("cross-tenant leakage:  none", GREEN),
      ("JWT forgery (alg:none): 401", GREEN),
      ("quota race (15x):      exact", GREEN)],
     "Multi-tenant isolation, session-token validation, and quota atomicity all proved sound.")
