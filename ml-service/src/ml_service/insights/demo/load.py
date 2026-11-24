"""Carrega o histórico de demonstração (gerado por `demo.build`) no banco **da demo**.

1. Veículos e motoristas são criados pela API (mesmo caminho do frontend; `serialNumber` DEMO-*, usuários demo.*).
2. Viagens e alertas do ano inteiro vão direto ao banco; as métricas dos meses antigos vão para
   `telemetry.trip_metrics` com `source = 'BACKFILL'`.
3. As viagens dos últimos `live_days` dias entram **sem** métricas, com telemetria e posições a 1 Hz: o executor do
   motor de insights (o mesmo do compose) calcula as métricas delas pelo caminho normal e gera os insights de hoje.

Por segurança, recusa um banco que tenha viagens de veículos que não são da demo. `--reset` apaga só o que é da
demo (e todos os insights) antes de carregar.

Uso (banco da demo do compose, perfil `demo`):
  DEMO_DATABASE_URL=postgresql://<usuário>:<senha>@localhost:5433/<banco> ADMIN_PASSWORD=<senha do admin> \\
  python -m ml_service.insights.demo.load --api http://localhost:8082/api/v1 [--reset]
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

import pandas as pd
import psycopg

from ml_service.insights import db
from ml_service.insights.demo.build import DEMO_VEHICLE_MODEL
from ml_service.insights.demo.scenario import DemoConfig
from ml_service.insights.run import ModelResolver, compute_pending_trips, evaluate_insights

log = logging.getLogger("ml_service.insights.demo")

SERIAL_PREFIX = "DEMO-"
USER_PREFIX = "demo."


# ---------------------------------------------------------------------------------------------------------------
# API

class Api:
    def __init__(self, base: str, username: str, password: str):
        self.base = base.rstrip("/")
        self.token = self._call("POST", "/auth/login", {"username": username, "password": password})["accessToken"]

    def _call(self, method: str, path: str, body: dict | None = None):
        req = urllib.request.Request(self.base + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None,
                                     headers={"Content-Type": "application/json",
                                              **({"Authorization": f"Bearer {self.token}"}
                                                 if getattr(self, "token", None) else {})})
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None

    def get(self, path: str, **params):
        return self._call("GET", f"{path}?{urllib.parse.urlencode(params)}" if params else path)

    def post(self, path: str, body: dict):
        return self._call("POST", path, body)


def ensure_vehicles(api: Api, cfg: DemoConfig) -> dict[str, str]:
    ids = {}
    for v in cfg.vehicles:
        serial = SERIAL_PREFIX + v["key"].upper()
        found = [x for x in api.get("/vehicles/search", serialNumber=serial, size=50)["content"]
                 if x["serialNumber"] == serial]
        if found:
            ids[v["key"]] = found[0]["id"]
            continue
        created = api.post("/vehicles", {"serialNumber": serial, "licensePlate": v["plate"], "nickname": v["nickname"],
                                         "vehicleType": cfg.raw.get("vehicle_type", "RIGID_TRUCK"),
                                         "fuelType": "DIESEL", "brand": "Simulador", "model": "Caminhão médio",
                                         "year": 2022})
        ids[v["key"]] = created["id"]
    return ids


def ensure_drivers(api: Api, cfg: DemoConfig) -> dict[str, str]:
    role = next(r["id"] for r in api.get("/roles") if r["name"] == "Driver")
    ids = {}
    for d in cfg.drivers:
        username = USER_PREFIX + d["key"]
        found = [u for u in api.get("/users/search", username=username, size=50)["content"]
                 if u["username"] == username]
        if found:
            ids[d["key"]] = found[0]["userId"]
            continue
        created = api.post("/users", {"username": username, "email": f"{username}@demo.fleet",
                                      "password": os.environ.get("DEMO_DRIVER_PASSWORD", "demo-driver"),
                                      "roleId": role, "firstName": d["first"], "lastName": d["last"],
                                      "phone": "11900000000"})
        ids[d["key"]] = created["userId"]
    return ids


# ---------------------------------------------------------------------------------------------------------------
# banco

def check_demo_database(conn: psycopg.Connection) -> None:
    other = conn.execute("""
        SELECT count(*) FROM fleet.trips t JOIN fleet.vehicles v ON v.id = t.vehicle_id
        WHERE v.serial_number IS NULL OR v.serial_number NOT LIKE %s""", (SERIAL_PREFIX + "%",)).fetchone()
    count = other["count"] if isinstance(other, dict) else other[0]
    if count:
        raise SystemExit(f"o banco tem {count} viagens de veículos que não são da demo: use o banco da demo "
                         "(perfil `demo` do compose), não o banco principal")


def reset(conn: psycopg.Connection, vehicle_ids: list[str]) -> None:
    with conn.cursor() as cur:
        cur.execute("DELETE FROM telemetry.insights")
        for table in ("telemetry.trip_metrics", "telemetry.vehicle_telemetry", "telemetry.vehicle_positions",
                      "telemetry.events", "fleet.trips"):
            cur.execute(f"DELETE FROM {table} WHERE vehicle_id = ANY(%s::uuid[])", (vehicle_ids,))
    conn.commit()


def _copy(conn: psycopg.Connection, table: str, columns: list[str], rows) -> int:
    n = 0
    with conn.cursor() as cur, cur.copy(f"COPY {table} ({', '.join(columns)}) FROM STDIN") as cp:
        for row in rows:
            cp.write_row(row)
            n += 1
    return n


def _none(x):
    return None if x is None or (isinstance(x, float) and x != x) else x


def load(demo: Path, cfg: DemoConfig, api: Api, conn: psycopg.Connection, do_reset: bool) -> dict:
    vehicles, drivers = ensure_vehicles(api, cfg), ensure_drivers(api, cfg)
    check_demo_database(conn)
    if do_reset:
        reset(conn, list(vehicles.values()))
    elif conn.execute("SELECT 1 FROM fleet.trips LIMIT 1").fetchone():
        raise SystemExit("o banco da demo já tem viagens: rode com --reset para recarregar")

    metrics = pd.read_parquet(demo / "trip_metrics.parquet")
    metrics["vehicle_id"] = metrics["vehicle_key"].map(vehicles)
    metrics["driver_id"] = metrics["driver_key"].map(drivers)
    metrics["start_time"] = pd.to_datetime(metrics["start_time"], utc=True)
    metrics["end_time"] = pd.to_datetime(metrics["end_time"], utc=True)
    live_start = pd.Timestamp(cfg.live_start, tz="America/Sao_Paulo").tz_convert("UTC")
    live = metrics["start_time"] >= live_start
    counts = {"trips": _copy(conn, "fleet.trips", ["id", "driver_id", "vehicle_id", "start_time", "end_time"],
                             metrics[["trip_id", "driver_id", "vehicle_id", "start_time", "end_time"]]
                             .itertuples(index=False))}

    cols = ["trip_id", "vehicle_id", "driver_id", "start_time", "end_time", *db.TRIP_METRIC_COLUMNS]
    backfill = metrics.loc[~live & metrics["engine_on_s"].notna(), cols]

    def metric_rows():
        for r in backfill.itertuples(index=False):
            yield [*(_none(x) for x in r), "BACKFILL"]
    counts["trip_metrics"] = _copy(conn, "telemetry.trip_metrics", [*cols, "source"], metric_rows())

    events = pd.read_parquet(demo / "events.parquet")
    counts["events"] = _copy(conn, "telemetry.events",
                             ["time", "vehicle_id", "trip_id", "event_type", "severity", "message"],
                             ([pd.Timestamp(r.time).to_pydatetime(), vehicles[r.vehicle], r.trip_id, r.event_type,
                               r.severity, r.message] for r in events.itertuples(index=False)))
    conn.commit()

    live_trips = set(metrics.loc[live, "trip_id"])
    counts["telemetry"] = counts["positions"] = 0
    for month_dir in sorted(p for p in demo.iterdir() if (p / "telemetry.csv.gz").exists()):
        tel = pd.read_csv(month_dir / "telemetry.csv.gz")
        tel = tel[tel["tripId"].isin(live_trips)]
        if tel.empty:
            continue
        tel["time"] = pd.to_datetime(tel["timestamp"], unit="s", utc=True)
        tel["vehicle_uuid"] = tel["vehicleId"].map(vehicles)
        counts["telemetry"] += _copy(conn, "telemetry.vehicle_telemetry",
                                     ["time", "vehicle_id", "trip_id", "engine_rpm", "fuel_level_pct", "engine_temp_c",
                                      "odometer_km", "battery_voltage", "speed_kmh", "load_pct", "engine_torque_pct",
                                      "pedal_pct", "gear", "oil_kpa", "fuel_rate_lph", "boost_kpa", "intake_c"],
                                     tel[["time", "vehicle_uuid", "tripId", "engineRpm", "fuelLevelPct", "engineTempC",
                                          "odometerKm", "batteryVoltage", "speedKmh", "loadPct", "engineTorquePct",
                                          "pedalPct", "gear", "oilKpa", "fuelRateLph", "boostKpa", "intakeC"]]
                                     .itertuples(index=False))
        counts["positions"] += _copy(conn, "telemetry.vehicle_positions",
                                     ["time", "vehicle_id", "trip_id", "latitude", "longitude", "speed_kmh"],
                                     tel[["time", "vehicle_uuid", "tripId", "latitude", "longitude", "speedKmh"]]
                                     .itertuples(index=False))
        conn.commit()
    return counts


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--demo", default="data/demo")
    ap.add_argument("--config", default="configs/demo-history.yaml")
    ap.add_argument("--api", default="http://localhost:8082/api/v1")
    ap.add_argument("--admin-user", default="admin")
    ap.add_argument("--reset", action="store_true", help="apaga os dados da demo (e os insights) antes de carregar")
    a = ap.parse_args()
    url = os.environ.get("DEMO_DATABASE_URL")
    if not url:
        raise SystemExit("defina DEMO_DATABASE_URL (conninfo do banco da demo); o banco principal nunca é usado")
    manifest = json.loads((Path(a.demo) / "manifest.json").read_text())
    cfg = DemoConfig.load(a.config, pd.Timestamp(manifest["end"]).date())
    try:
        api = Api(a.api, a.admin_user, os.environ["ADMIN_PASSWORD"])
    except urllib.error.URLError as e:
        raise SystemExit(f"API da demo indisponível em {a.api}: {e}")
    with psycopg.connect(url, row_factory=psycopg.rows.dict_row) as conn:
        counts = load(Path(a.demo), cfg, api, conn, a.reset)
        log.info("carregado: %s", counts)
        resolver = ModelResolver(Path("models"), DEMO_VEHICLE_MODEL)
        computed = 0
        while n := compute_pending_trips(conn, resolver, batch=200, window_s=60):
            computed += n
        log.info("métricas LIVE calculadas: %d viagens", computed)
        log.info("insights: %s", evaluate_insights(conn, lookback_days=100))


if __name__ == "__main__":
    main()
