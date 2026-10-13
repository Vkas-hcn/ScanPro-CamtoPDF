# -*- coding: utf-8 -*-
from pathlib import Path

root = Path(r"D:/android_work/pdf_converter/app/src/main/res")
data = {
    "hi": ("ad loading...", "कृपया प्रतीक्षा करें, विज्ञापन लोड हो रहा है..."),
    "fr": ("chargement pub...", "Veuillez patienter, publicité en cours de chargement..."),
    "de": ("Anzeige wird geladen...", "Bitte warten, Anzeige wird geladen..."),
    "pt": ("carregando anúncio...", "Aguarde, o anúncio está carregando..."),
    "es": ("cargando anuncio...", "Espere, el anuncio se está cargando..."),
    "in": ("iklan dimuat...", "Harap tunggu, iklan sedang dimuat..."),
    "ru": ("загрузка рекламы...", "Подождите, реклама загружается..."),
}
for loc, (a, b) in data.items():
    path = root / f"values-{loc}" / "strings.xml"
    text = path.read_text(encoding="utf-8")
    if "ad_interstitial_loading" in text:
        print(loc, "skip")
        continue
    a = a.replace("'", r"\'")
    b = b.replace("'", r"\'")
    block = (
        f'    <string name="ad_gate_loading">{a}</string>\n'
        f'    <string name="ad_interstitial_loading">{b}</string>\n'
    )
    path.write_text(text.replace("</resources>", block + "</resources>"), encoding="utf-8")
    print(loc, "ok")
