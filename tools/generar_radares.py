#!/usr/bin/env python3
"""Genera radares.json para RadarCapri unificando fuentes abiertas.

Fuentes (por prioridad al eliminar duplicados):
  1. DGT (NAP)            -> radares fijos y de tramo (excepto Cataluna y Pais Vasco)
  2. Servei Catala de Transit -> radares fijos de Cataluna (con limite)
  3. OpenStreetMap        -> huecos: Madrid (semaforos), Pais Vasco, urbanos...

Solo usa la libreria estandar. Uso:
    python generar_radares.py [--salida radares.json] [--cache cache]
"""
import argparse, json, math, os, re, sys, time, urllib.parse, urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

UA = "RadarCapri-datos/1.0"
URL_DGT = "https://nap.dgt.es/datex2/dgt/PredefinedLocationsPublication/radares/content.xml"
URL_CAT = "https://transit.gencat.cat/web/.content/documents/seguretat_viaria/radars.txt"
OVERPASS = ["https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"]
# Teselas (sur, oeste, norte, este) que cubren Espana peninsular, Baleares y Canarias
TESELAS = [
    (36.0, -9.5, 39.0, -4.5), (36.0, -4.5, 39.0, 0.0), (39.0, -9.5, 42.0, -6.0),
    (39.0, -6.0, 42.0, -3.0), (39.0, -3.0, 42.0, 0.0), (39.0, 0.0, 42.0, 4.5),
    (42.0, -9.5, 44.0, -4.0), (42.0, -4.0, 44.0, 0.0), (42.0, 0.0, 44.0, 4.5),
    (27.5, -18.5, 29.5, -13.0),
]
DEDUP_M = 50.0


def descargar(url, cache, nombre, datos=None, reintentos=3):
    ruta = os.path.join(cache, nombre)
    for i in range(reintentos):
        try:
            req = urllib.request.Request(url, data=datos, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=180) as r:
                cuerpo = r.read()
            with open(ruta, "wb") as f:
                f.write(cuerpo)
            return cuerpo
        except Exception as e:  # noqa
            print(f"   fallo {nombre} ({e}); intento {i + 1}/{reintentos}", file=sys.stderr)
            time.sleep(4 * (i + 1))
    if os.path.exists(ruta):
        print(f"   usando copia en cache de {nombre}", file=sys.stderr)
        return open(ruta, "rb").read()
    raise RuntimeError(f"No se pudo descargar {nombre}")


def haversine(a, b):
    R = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


# ---------------------------------------------------------------- DGT
def leer_dgt(cache):
    xml = descargar(URL_DGT, cache, "dgt.xml")
    ns = {"d": "http://datex2.eu/schema/1_0/1_0"}
    raiz = ET.fromstring(xml)
    pub = raiz.find(".//d:publicationTime", ns)
    out = []
    for loc in raiz.iter("{%s}predefinedLocation" % ns["d"]):
        ident = loc.get("id", "")
        if not ident.startswith("GUID_"):
            continue
        tramo = "CVM" in ident
        carretera = None
        provincia = None
        for nm in loc.iter("{%s}name" % ns["d"]):
            tipo = nm.findtext("d:tpegDescriptorType", namespaces=ns)
            val = nm.findtext("d:descriptor/d:value", namespaces=ns)
            if tipo == "linkName" and not carretera:
                carretera = val
            elif tipo == "other" and not provincia:
                provincia = val
        for pc in loc.iter("{%s}pointCoordinates" % ns["d"]):
            lat = float(pc.findtext("d:latitude", namespaces=ns))
            lng = float(pc.findtext("d:longitude", namespaces=ns))
            out.append(dict(id="dgt-" + ident.split("_", 1)[1] + ("-%d" % len(out) if tramo else ""),
                            lat=lat, lng=lng, t="tramo" if tramo else "fijo", v=0,
                            n=carretera or "", c=provincia or "", s="dgt"))
    return out, (pub.text if pub is not None else "")


# ------------------------------------------------------------- Cataluna
def utm_a_latlon(x, y, huso=31):
    """UTM (ETRS89 ~ WGS84) -> lat/lon. Formula inversa estandar."""
    a, f = 6378137.0, 1 / 298.257222101
    k0 = 0.9996
    e2 = f * (2 - f)
    ep2 = e2 / (1 - e2)
    x -= 500000.0
    m = y / k0
    mu = m / (a * (1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256))
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    phi1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu)
            + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
            + (151 * e1 ** 3 / 96) * math.sin(6 * mu)
            + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    n1 = a / math.sqrt(1 - e2 * math.sin(phi1) ** 2)
    t1 = math.tan(phi1) ** 2
    c1 = ep2 * math.cos(phi1) ** 2
    r1 = a * (1 - e2) / (1 - e2 * math.sin(phi1) ** 2) ** 1.5
    d = x / (n1 * k0)
    lat = phi1 - (n1 * math.tan(phi1) / r1) * (
        d ** 2 / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * ep2) * d ** 4 / 24
        + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * ep2 - 3 * c1 ** 2) * d ** 6 / 720)
    lon = (d - (1 + 2 * t1 + c1) * d ** 3 / 6
           + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * ep2 + 24 * t1 ** 2) * d ** 5 / 120) / math.cos(phi1)
    lon0 = math.radians((huso - 1) * 6 - 180 + 3)
    return math.degrees(lat), math.degrees(lon0 + lon)


def leer_cataluna(cache):
    txt = descargar(URL_CAT, cache, "cataluna.txt").decode("latin-1")
    out, descartados = [], 0
    for linea in txt.splitlines():
        m = re.match(r"^(\S+)\s+(\S+)\s+(\d+)\s+(\d+(?:,\d+)?)\s+(\d+(?:,\d+)?)\s*$", linea)
        if not m:
            continue
        via, pk, vel, x, y = m.groups()
        x, y = float(x.replace(",", ".")), float(y.replace(",", "."))
        lat, lng = utm_a_latlon(x, y)
        if not (40.4 <= lat <= 42.95 and 0.1 <= lng <= 3.4):  # fuera de Cataluna: dato erroneo
            descartados += 1
            continue
        out.append(dict(id="cat-%d" % len(out), lat=lat, lng=lng,
                        t="tramo" if "-" in pk else "fijo", v=int(vel),
                        n="%s PK %s" % (via, pk), c="Cataluña", s="cat"))
    return out, descartados


# ---------------------------------------------------------------- OSM
def leer_osm(cache):
    out = []
    for i, (s, w, n, e) in enumerate(TESELAS):
        consulta = '[out:json][timeout:120];node["highway"="speed_camera"](%s,%s,%s,%s);out;' % (s, w, n, e)
        datos = urllib.parse.urlencode({"data": consulta}).encode()
        cuerpo = None
        for url in OVERPASS:
            try:
                cuerpo = descargar(url, cache, "osm_%d.json" % i, datos, reintentos=2)
                json.loads(cuerpo)
                break
            except Exception:  # noqa
                cuerpo = None
        if cuerpo is None:
            raise RuntimeError("Overpass no responde para la tesela %d" % i)
        for el in json.loads(cuerpo)["elements"]:
            tg = el.get("tags", {})
            enf = tg.get("enforcement", "")
            tipo = "tramo" if enf == "average_speed" else "semaforo" if enf == "traffic_signals" else "fijo"
            vel = re.match(r"^\d+", tg.get("maxspeed", ""))
            az = tg.get("camera:direction") or tg.get("direction") or ""
            out.append(dict(id="osm-%d" % el["id"], lat=el["lat"], lng=el["lon"], t=tipo,
                            v=int(vel.group()) if vel else 0, n=tg.get("name", ""), c="", s="osm",
                            d=int(float(az)) % 360 if re.match(r"^\d+(\.\d+)?$", az) else None))
        print("   OSM tesela %d: acumulado %d" % (i, len(out)))
    return out


# --------------------------------------------------------- unificar
def unificar(listas):
    """Orden de prioridad = orden de las listas. Descarta puntos a <50 m de uno ya aceptado."""
    celda = 0.001  # ~111 m
    rejilla, final = {}, []
    for lista in listas:
        for r in lista:
            ci, cj = int(r["lat"] / celda), int(r["lng"] / celda)
            if any(haversine((r["lat"], r["lng"]), (o["lat"], o["lng"])) < DEDUP_M
                   for di in (-1, 0, 1) for dj in (-1, 0, 1) for o in rejilla.get((ci + di, cj + dj), [])):
                continue
            rejilla.setdefault((ci, cj), []).append(r)
            final.append(r)
    return final


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", default="radares.json")
    ap.add_argument("--cache", default="cache")
    args = ap.parse_args()
    os.makedirs(args.cache, exist_ok=True)

    print("DGT...")
    dgt, fecha_dgt = leer_dgt(args.cache)
    print("   %d puntos (publicado %s)" % (len(dgt), fecha_dgt))
    print("Cataluña...")
    cat, malos = leer_cataluna(args.cache)
    print("   %d radares, %d descartados por coordenadas erróneas" % (len(cat), malos))
    print("OpenStreetMap...")
    osm = leer_osm(args.cache)

    # OSM de Cataluna ya viene de la fuente oficial: se deduplica por distancia igualmente.
    final = unificar([dgt, cat, osm])
    por_fuente = {}
    for r in final:
        por_fuente[r["s"]] = por_fuente.get(r["s"], 0) + 1

    radares = []
    for r in final:
        o = {"i": r["id"], "a": round(r["lat"], 6), "o": round(r["lng"], 6), "t": r["t"]}
        if r["v"]:
            o["v"] = r["v"]
        if r["n"]:
            o["n"] = r["n"]
        if r["c"]:
            o["c"] = r["c"]
        if r.get("d") is not None:
            o["d"] = r["d"]
        radares.append(o)

    doc = {"version": 1,
           "generado": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
           "fuentes": {"dgt": fecha_dgt, **por_fuente},
           "total": len(radares), "radares": radares}
    with open(args.salida, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print("OK: %d radares %s -> %s (%d KB)" % (len(radares), por_fuente, args.salida,
                                                os.path.getsize(args.salida) // 1024))


if __name__ == "__main__":
    main()
