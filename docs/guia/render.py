"""Genera el manual oficial: portada + guía en un solo PDF.

Uso:  python3 docs/guia/render.py      (requiere playwright y pypdf)
Salida: docs/Guia-instalacion-y-uso.pdf
La versión de la portada se lee de app/build.gradle.kts (val appVersion).
"""
import datetime, pathlib, re, tempfile
from playwright.sync_api import sync_playwright
from pypdf import PdfReader, PdfWriter

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = HERE.parent / "Guia-instalacion-y-uso.pdf"
MESES = "enero febrero marzo abril mayo junio julio agosto septiembre octubre noviembre diciembre".split()

version = re.search(r'val appVersion = "([^"]+)"', (ROOT / "app/build.gradle.kts").read_text()).group(1)
hoy = datetime.date.today()
fecha = f"{MESES[hoy.month - 1]} {hoy.year}"
svg = (HERE / "qr.svg").read_text()
svg = svg[svg.index("<svg"):]

def fill(name):
    html = (HERE / name).read_text().replace("__QR__", svg).replace("__VERSION__", version).replace("__FECHA__", fecha)
    tmp = HERE / ("_build_" + name)
    tmp.write_text(html)
    return tmp

FOOTER = ('<div style="font-family:Inter,sans-serif;font-size:8pt;color:#777;width:100%;text-align:center;">'
          'Lee tu agenda · Guía de instalación y uso · <span class="pageNumber"></span>/<span class="totalPages"></span></div>')

with tempfile.TemporaryDirectory() as td, sync_playwright() as p:
    b = p.chromium.launch()
    page = b.new_page()
    cover, guide = fill("portada.html"), fill("guia.html")
    page.set_viewport_size({"width": 794, "height": 1123})  # A4 a 96 ppp: la portada ocupa la hoja entera
    page.goto(cover.as_uri()); page.wait_for_timeout(300)
    page.pdf(path=f"{td}/portada.pdf", width="210mm", height="297mm", print_background=True, scale=1,
             margin={"top": "0", "bottom": "0", "left": "0", "right": "0"})
    page.goto(guide.as_uri()); page.wait_for_timeout(300)
    page.pdf(path=f"{td}/guia.pdf", format="A4", print_background=True, display_header_footer=True,
             header_template="<span></span>", footer_template=FOOTER,
             margin={"top": "18mm", "bottom": "18mm", "left": "18mm", "right": "18mm"})
    b.close()
    cover.unlink(); guide.unlink()
    w = PdfWriter()
    for f in ("portada.pdf", "guia.pdf"):
        for pg in PdfReader(f"{td}/{f}").pages:
            w.add_page(pg)
    w.add_metadata({"/Title": "Lee tu agenda · Guía de instalación y uso", "/Author": "Lee tu agenda",
                    "/Subject": f"Versión {version}"})
    with open(OUT, "wb") as o:
        w.write(o)
print(f"{OUT} · versión {version} · {len(PdfReader(OUT).pages)} páginas")
