"""Gera as rotas do simulador (src/main/resources/routes/*.json) — executado uma vez, resultado versionado.

Fontes: traçado e velocidade estimada por trecho do OSRM (demo público, dados OpenStreetMap);
altitude do OpenTopoData (SRTM 90 m). Os pontos são reamostrados a cada ~STEP_M metros.

Uso: python3 tools/fetch_routes.py   (somente biblioteca padrão)
"""
import json
import math
import time
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/routes"
STEP_M = 100.0
OSRM = "https://router.project-osrm.org/route/v1/driving/{coords}?overview=full&geometries=geojson&annotations=speed,distance"
TOPO = "https://api.opentopodata.org/v1/srtm90m?locations={locs}"
UA = {"User-Agent": "fleet-analytics-simulator-route-builder"}

ROUTES = {
    "campinas-sao-paulo": {
        "name": "Campinas → São Paulo (Rod. dos Bandeirantes)",
        "kind": "HIGHWAY",
        "waypoints": [(-22.9056, -47.0608), (-23.0150, -46.9700), (-23.5155, -46.7140)],
    },
    "sao-paulo-santos": {
        "name": "São Paulo → Santos (Via Anchieta, serra)",
        "kind": "MOUNTAIN",
        "waypoints": [(-23.6260, -46.6000), (-23.8000, -46.5470), (-23.9600, -46.3333)],
    },
    "campinas-urbano": {
        "name": "Entregas urbanas — Campinas",
        "kind": "URBAN",
        "waypoints": [(-22.9056, -47.0608), (-22.8858, -47.0520), (-22.8700, -47.0750),
                      (-22.8925, -47.0950), (-22.9180, -47.0820), (-22.9056, -47.0608)],
    },
}


def get_json(url: str):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def haversine(a, b):
    lat1, lon1, lat2, lon2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def limit_kmh(est_speed_ms):
    """Limite do trecho a partir da velocidade estimada pelo OSRM (perfil de carro), arredondada a 10 km/h.
    O demo público não expõe o maxspeed do OSM; o limite próprio de caminhões é aplicado no simulador."""
    v = est_speed_ms * 3.6 * 1.15
    return int(max(20, min(120, round(v / 10) * 10)))


def build(key, spec):
    coords = ";".join(f"{lon},{lat}" for lat, lon in spec["waypoints"])
    route = get_json(OSRM.format(coords=coords))["routes"][0]
    pts = [(lat, lon) for lon, lat in route["geometry"]["coordinates"]]
    seg_limits = []
    for leg in route["legs"]:
        ann = leg["annotation"]
        seg_limits += [limit_kmh(s) for s in ann["speed"]]
    assert len(seg_limits) == len(pts) - 1, (len(seg_limits), len(pts))

    # reamostragem a cada STEP_M metros ao longo do traçado
    out, acc, nxt = [], 0.0, 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        d = haversine(a, b)
        while d > 0 and nxt <= acc + d:
            f = (nxt - acc) / d
            out.append([a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1]), seg_limits[i], nxt])
            nxt += STEP_M
        acc += d
    last = pts[-1]
    out.append([last[0], last[1], seg_limits[-1], acc])

    elev = []
    for k in range(0, len(out), 100):
        chunk = out[k:k + 100]
        locs = "|".join(f"{p[0]:.6f},{p[1]:.6f}" for p in chunk)
        res = get_json(TOPO.format(locs=locs))["results"]
        elev += [r["elevation"] if r["elevation"] is not None else float("nan") for r in res]
        time.sleep(1.1)  # limite de 1 req/s do serviço público

    points = [{"lat": round(p[0], 6), "lon": round(p[1], 6), "elevationM": round(e, 1),
               "speedLimitKmh": p[2], "distanceM": round(p[3], 1)} for p, e in zip(out, elev)]
    doc = {"id": key, "name": spec["name"], "kind": spec["kind"], "lengthM": round(acc, 1),
           "source": "OSRM (OpenStreetMap) + OpenTopoData SRTM90m", "points": points}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{key}.json").write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")))
    lims = [p["speedLimitKmh"] for p in points]
    print(f"{key}: {acc / 1000:.1f} km, {len(points)} pts, elev {min(elev):.0f}–{max(elev):.0f} m, "
          f"limits {sorted(set(lims))}")


if __name__ == "__main__":
    for key, spec in ROUTES.items():
        build(key, spec)
