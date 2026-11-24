"""Regras de insight sobre o histórico (funções puras sobre DataFrames).

Entradas:
- `trips`: linhas de `telemetry.trip_metrics` + `vehicle_type`, `vehicle_name`, `driver_name`
  (`health` e `policy` como dicionários; `start_time` com fuso);
- `events`: `time`, `vehicle_id`, `event_type`, `severity` (alertas fixos e de ML);
- `now` (Timestamp com fuso) e a política da frota.

Saída: lista de `Candidate` — o que deveria estar aberto agora. A persistência (inserir, atualizar, resolver) fica
em `planner.py`. Critérios estatísticos em docs/plano-insights-historicos.md (D37); textos são frases prontas
com os números (D36).
"""

from __future__ import annotations

import zlib
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy import stats

from ml_service.insights.policy import FleetPolicy

SEVERITY_RANK = {"INFO": 0, "WARNING": 1, "CRITICAL": 2}

PEER_DAYS = 30
PEER_MIN_DRIVERS = 3
PEER_MIN_RATIO = 1.25
BOOTSTRAP = 400

TREND_RECENT_DAYS = 7
TREND_BASE_DAYS = 90
HEALTH_BASE_DAYS = 60
ALERT_BASE_WEEKS = 8

EVENT_LABELS = {
    "RPM_LIMIT": "limite de RPM", "ENGINE_OVERHEAT": "superaquecimento do motor",
    "LOW_FUEL": "combustível baixo", "LOW_BATTERY": "bateria fraca", "LOW_OIL_PRESSURE": "pressão do óleo baixa",
    "ML_COOLANT_C": "temperatura do motor fora do esperado", "ML_OIL_KPA": "pressão do óleo fora do esperado",
    "ML_FUEL_RATE_LPH": "consumo fora do esperado", "ML_BOOST_KPA": "pressão do turbo fora do esperado",
    "ML_INTAKE_C": "temperatura de admissão fora do esperado", "ML_BATTERY_V": "tensão da bateria fora do esperado",
    "ML_FUEL_LEVEL_PCT": "queda anormal do nível de combustível", "ML_RPM_ROUGHNESS": "rotação irregular",
    "ML_PATTERN": "padrão de operação anômalo",
}

VEHICLE_TYPE_LABELS = {"RIGID_TRUCK": "caminhões rígidos", "ARTICULATED_VEHICLE": "cavalos mecânicos",
                       "PASSAGER_VEHICLE": "veículos de passageiros", "AGRICULTURAL_VEHICLE": "veículos agrícolas"}


@dataclass
class Candidate:
    dedup_key: str
    scope: str            # VEHICLE | DRIVER | FLEET
    subject_id: str | None
    category: str
    severity: str         # INFO | WARNING | CRITICAL
    title: str
    description: str
    period_start: pd.Timestamp
    period_end: pd.Timestamp
    evidence: dict = field(default_factory=dict)


# ---------------------------------------------------------------------------------------------------------------
# formatação pt-BR

def br(x: float, nd: int = 0) -> str:
    s = f"{x:,.{nd}f}"
    return s.replace(",", "§").replace(".", ",").replace("§", ".")


def pct_more(ratio: float) -> str:
    """'40% mais' para razões < 2; '2,3× mais' a partir de 2."""
    return f"{br(ratio, 1)}× mais" if ratio >= 2 else f"{br((ratio - 1) * 100)}% mais"


def _rng(*keys) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32("|".join(map(str, keys)).encode()))


def _since(df: pd.DataFrame, now: pd.Timestamp, days: float, until_days: float = 0) -> pd.DataFrame:
    start, end = now - pd.Timedelta(days=days), now - pd.Timedelta(days=until_days)
    return df[(df["start_time"] >= start) & (df["start_time"] < end)]


# ---------------------------------------------------------------------------------------------------------------
# 1–4. motoristas comparados com os colegas

@dataclass(frozen=True)
class PeerMetric:
    category: str
    title: str
    numerator: str              # coluna (ou expressão calculada em _peer_frame)
    exposure: str
    scale: float                # rate = soma(numerator) / soma(exposure) * scale
    min_exposure: float         # na unidade de soma(exposure) (ex.: segundos ou km)
    floor: float                # piso da mediana, para não dividir por ~0


PEER_METRICS = (
    PeerMetric("DRIVER_SPEEDING", "Velocidade acima da política", "speeding_s", "driving_s",
               scale=10 * 3600 / 60, min_exposure=10 * 3600, floor=1.0),          # min a cada 10 h dirigidas
    PeerMetric("DRIVER_HARSH_DRIVING", "Direção brusca", "harsh_total", "distance_km",
               scale=100.0, min_exposure=300.0, floor=0.5),                      # eventos a cada 100 km
    PeerMetric("DRIVER_IDLE", "Motor ligado parado", "excessive_idle_s", "engine_on_s",
               scale=100.0, min_exposure=10 * 3600, floor=2.0),                  # % do tempo de motor ligado
    PeerMetric("DRIVER_RPM", "Rotação fora da faixa econômica", "rpm_over_s", "driving_s",
               scale=100.0, min_exposure=10 * 3600, floor=2.0),                  # % do tempo dirigindo
)


def _peer_frame(trips: pd.DataFrame) -> pd.DataFrame:
    t = trips[trips["driver_id"].notna()].copy()
    t["harsh_total"] = t["harsh_accel_count"] + t["harsh_brake_count"]
    idle = t["idle_s"].where(t["idle_s"] > 0)
    t["excess_idle_fuel_l"] = (t["idle_fuel_l"].fillna(0) * (t["excessive_idle_s"] / idle)).fillna(0)
    return t


def _bootstrap_rate_low(num: np.ndarray, expo: np.ndarray, scale: float, rng: np.random.Generator) -> float:
    """Percentil 5% da taxa do motorista, reamostrando as viagens dele."""
    n = len(num)
    idx = rng.integers(0, n, size=(BOOTSTRAP, n))
    e = expo[idx].sum(axis=1)
    rates = np.where(e > 0, num[idx].sum(axis=1) / np.where(e > 0, e, 1) * scale, 0.0)
    return float(np.quantile(rates, 0.05))


def _peer_text(m: PeerMetric, name: str, rate: float, med: float, ratio: float, policy: FleetPolicy,
               ev: dict, vehicle_type: str) -> str:
    peers = f"a mediana dos motoristas de {VEHICLE_TYPE_LABELS.get(vehicle_type, 'veículos do mesmo tipo')}"
    if m.category == "DRIVER_SPEEDING":
        return (f"{name} passa {pct_more(ratio)} tempo acima de {br(policy.speed_limit_kmh)} km/h que {peers} "
                f"({br(rate, 1)} contra {br(med, 1)} min a cada 10 h dirigidas, últimos {PEER_DAYS} dias).")
    if m.category == "DRIVER_HARSH_DRIVING":
        return (f"{name} tem {pct_more(ratio)} acelerações e frenagens bruscas que {peers} "
                f"({br(rate, 1)} contra {br(med, 1)} a cada 100 km; {ev['harsh_brake']} frenagens e "
                f"{ev['harsh_accel']} acelerações em {br(ev['distance_km'])} km nos últimos {PEER_DAYS} dias).")
    if m.category == "DRIVER_IDLE":
        return (f"{name} deixa o motor ligado parado por mais de {br(policy.idle_threshold_s / 60)} min seguidos em "
                f"{br(rate)}% do tempo de motor ligado ({peers}: {br(med)}%). Foram ≈ {br(ev['idle_fuel_l'])} L de "
                f"diesel (≈ R$ {br(ev['idle_cost'])}) nos últimos {PEER_DAYS} dias.")
    return (f"{name} dirige {br(rate)}% do tempo acima de {br(ev['rpm_max'])} rpm, fora da faixa econômica "
            f"({peers}: {br(med)}%), nos últimos {PEER_DAYS} dias.")


def driver_peer_insights(trips: pd.DataFrame, now: pd.Timestamp, policy: FleetPolicy) -> list[Candidate]:
    recent = _peer_frame(_since(trips, now, PEER_DAYS))
    out: list[Candidate] = []
    if recent.empty:
        return out
    for vtype, group in recent.groupby("vehicle_type"):
        for m in PEER_METRICS:
            agg = group.groupby("driver_id").agg(num=(m.numerator, "sum"), expo=(m.exposure, "sum"),
                                                 name=("driver_name", "first"))
            agg = agg[agg["expo"] >= m.min_exposure]
            if len(agg) < PEER_MIN_DRIVERS:
                continue
            agg["rate"] = agg["num"] / agg["expo"] * m.scale
            for driver_id, row in agg.iterrows():
                med = float(agg.drop(index=driver_id)["rate"].median())
                base = max(med, m.floor)
                ratio = row["rate"] / base
                if ratio < PEER_MIN_RATIO:
                    continue
                d = group[group["driver_id"] == driver_id]
                low = _bootstrap_rate_low(d[m.numerator].to_numpy(float), d[m.exposure].to_numpy(float), m.scale,
                                          _rng(m.category, driver_id, now.date()))
                if low <= base:
                    continue  # diferença não sustentada pelas viagens do motorista
                ev = {"rate": round(float(row["rate"]), 3), "peer_median": round(med, 3), "ratio": round(ratio, 3),
                      "rate_p05": round(low, 3), "peers": len(agg) - 1, "trips": len(d), "vehicle_type": vtype,
                      "exposure": round(float(row["expo"]), 1), "harsh_brake": int(d["harsh_brake_count"].sum()),
                      "harsh_accel": int(d["harsh_accel_count"].sum()), "distance_km": float(d["distance_km"].sum()),
                      "idle_fuel_l": float(d["excess_idle_fuel_l"].sum()), "rpm_max": policy.rpm_max_for(vtype)}
                ev["idle_cost"] = ev["idle_fuel_l"] * policy.fuel_price_per_liter
                out.append(Candidate(
                    dedup_key=f"{m.category}:{driver_id}:{vtype}", scope="DRIVER", subject_id=str(driver_id),
                    category=m.category, severity="CRITICAL" if ratio >= 2 else "WARNING", title=m.title,
                    description=_peer_text(m, row["name"] or "Motorista", row["rate"], med, ratio, policy, ev, vtype),
                    period_start=now - pd.Timedelta(days=PEER_DAYS), period_end=now, evidence=ev))
    return out


# ---------------------------------------------------------------------------------------------------------------
# 5. consumo do veículo comparado com o próprio histórico (normalizado pelo modelo)

def _fuel_pairs(trips: pd.DataFrame) -> pd.DataFrame:
    t = trips.copy()
    t["fuel_real_scored"] = t["health"].map(lambda h: (h or {}).get("fuel_real_l"))
    t["fuel_exp_scored"] = t["health"].map(lambda h: (h or {}).get("fuel_expected_l"))
    return t[t["fuel_real_scored"].notna() & (t["fuel_exp_scored"].fillna(0) > 0)]


def vehicle_fuel_trend(trips: pd.DataFrame, now: pd.Timestamp) -> list[Candidate]:
    out: list[Candidate] = []
    pairs = _fuel_pairs(trips)
    for vid, t in pairs.groupby("vehicle_id"):
        rec = _since(t, now, TREND_RECENT_DAYS)
        base = _since(t, now, TREND_RECENT_DAYS + TREND_BASE_DAYS, TREND_RECENT_DAYS)
        if len(rec) < 3 or rec["fuel_exp_scored"].sum() < 20 or len(base) < 5 or base["fuel_exp_scored"].sum() < 60:
            continue
        r_rec = rec["fuel_real_scored"].sum() / rec["fuel_exp_scored"].sum()
        r_base = base["fuel_real_scored"].sum() / base["fuel_exp_scored"].sum()
        change = r_rec / r_base - 1
        if change < 0.10:
            continue
        rng = _rng("fuel", vid, now.date())
        real, exp_ = rec["fuel_real_scored"].to_numpy(float), rec["fuel_exp_scored"].to_numpy(float)
        idx = rng.integers(0, len(rec), size=(BOOTSTRAP, len(rec)))
        low = float(np.quantile(real[idx].sum(1) / exp_[idx].sum(1) / r_base - 1, 0.05))
        if low <= 0.03:
            continue
        l100 = lambda d: d["fuel_used_l"].sum() / max(d["distance_km"].sum(), 1e-9) * 100
        name = t["vehicle_name"].iloc[0]
        ev = {"ratio_recent": round(r_rec, 4), "ratio_baseline": round(r_base, 4), "change": round(change, 4),
              "change_p05": round(low, 4), "trips_recent": len(rec), "trips_baseline": len(base),
              "l100_recent": round(l100(rec), 2), "l100_baseline": round(l100(base), 2)}
        out.append(Candidate(
            dedup_key=f"VEHICLE_FUEL_TREND:{vid}", scope="VEHICLE", subject_id=str(vid),
            category="VEHICLE_FUEL_TREND", severity="CRITICAL" if change >= 0.25 else "WARNING",
            title="Consumo acima do histórico",
            description=(f"{name}: consumo {br(change * 100)}% acima do próprio histórico nos últimos "
                         f"{TREND_RECENT_DAYS} dias, já descontados rota, carga e velocidade ({br(r_rec, 2)} L por "
                         f"litro esperado, contra {br(r_base, 2)} nos {TREND_BASE_DAYS} dias anteriores; "
                         f"{br(ev['l100_recent'], 1)} contra {br(ev['l100_baseline'], 1)} L/100 km). "
                         f"Verifique injetores, filtro de ar e pressão dos pneus."),
            period_start=now - pd.Timedelta(days=TREND_RECENT_DAYS), period_end=now, evidence=ev))
    return out


# ---------------------------------------------------------------------------------------------------------------
# 6. desgaste progressivo: real − esperado de óleo, bateria, turbo e temperatura

@dataclass(frozen=True)
class HealthRule:
    signal: str
    relative: bool          # desvio relativo (fração) ou absoluto (unidade do sinal)
    direction: int          # -1: queda é ruim; +1: alta é ruim
    warn: float
    crit: float
    label: str
    advice: str


HEALTH_RULES = (
    HealthRule("oil_kpa", True, -1, 0.05, 0.12, "Pressão do óleo", "Programe inspeção do nível de óleo e da bomba."),
    HealthRule("battery_v", True, -1, 0.025, 0.06, "Tensão de carga da bateria",
               "Verifique alternador, correia e conexões."),
    HealthRule("boost_kpa", True, -1, 0.10, 0.20, "Pressão do turbo", "Verifique mangueiras, abraçadeiras e intercooler."),
    HealthRule("coolant_c", False, +1, 3.0, 6.0, "Temperatura do motor",
               "Verifique radiador, ventoinha e nível do líquido de arrefecimento."),
)


def _daily_health(trips: pd.DataFrame, signal: str, relative: bool) -> pd.Series:
    rows = []
    for _, r in trips.iterrows():
        h = r["health"] or {}
        s = (h.get("signals") or {}).get(signal)
        n = h.get("windows", 0)
        if not s or not n or not s.get("expected"):
            continue
        dev = (s["actual"] - s["expected"]) / s["expected"] if relative else s["actual"] - s["expected"]
        rows.append((r["start_time"].tz_convert("America/Sao_Paulo").date(), dev, n))
    if not rows:
        return pd.Series(dtype=float)
    d = pd.DataFrame(rows, columns=["day", "dev", "n"])
    return d.groupby("day").apply(lambda g: np.average(g["dev"], weights=g["n"]), include_groups=False)


def vehicle_health_drift(trips: pd.DataFrame, now: pd.Timestamp) -> list[Candidate]:
    out: list[Candidate] = []
    today = now.tz_convert("America/Sao_Paulo").date()
    for vid, t in trips.groupby("vehicle_id"):
        t = _since(t, now, TREND_RECENT_DAYS + HEALTH_BASE_DAYS)
        name = t["vehicle_name"].iloc[0] if len(t) else ""
        for rule in HEALTH_RULES:
            daily = _daily_health(t, rule.signal, rule.relative)
            if daily.empty:
                continue
            age = np.array([(today - d).days for d in daily.index])
            rec, base = daily[age < TREND_RECENT_DAYS], daily[age >= TREND_RECENT_DAYS]
            if len(rec) < 3 or len(base) < 7:
                continue
            delta = float(rec.mean() - base.mean()) * rule.direction  # > 0 = piora
            if delta < rule.warn:
                continue
            alt = "greater" if rule.direction > 0 else "less"
            p = float(stats.mannwhitneyu(rec, base, alternative=alt).pvalue)
            if p >= 0.05:
                continue
            unit = "%" if rule.relative else " °C"
            scale = 100 if rule.relative else 1
            word = "abaixo" if rule.direction < 0 else "acima"
            ev = {"signal": rule.signal, "recent_dev": round(float(rec.mean()), 4),
                  "baseline_dev": round(float(base.mean()), 4), "worsening": round(delta, 4), "p_value": round(p, 5),
                  "days_recent": len(rec), "days_baseline": len(base), "relative": rule.relative}
            out.append(Candidate(
                dedup_key=f"VEHICLE_HEALTH_DRIFT:{vid}:{rule.signal}", scope="VEHICLE", subject_id=str(vid),
                category="VEHICLE_HEALTH_DRIFT", severity="CRITICAL" if delta >= rule.crit else "WARNING",
                title=f"Desgaste progressivo: {rule.label.lower()}",
                description=(f"{rule.label} do {name} está {br(abs(rec.mean()) * scale, 1)}{unit} {word} do esperado "
                             f"para as condições de operação nos últimos {TREND_RECENT_DAYS} dias (nos "
                             f"{HEALTH_BASE_DAYS} dias anteriores: {br(base.mean() * scale, 1)}{unit}). {rule.advice}"),
                period_start=now - pd.Timedelta(days=TREND_RECENT_DAYS), period_end=now, evidence=ev))
    return out


# ---------------------------------------------------------------------------------------------------------------
# 7. alertas recorrentes

def vehicle_alert_recurrence(events: pd.DataFrame, trips: pd.DataFrame, now: pd.Timestamp) -> list[Candidate]:
    out: list[Candidate] = []
    if events.empty or trips.empty:
        return out
    first_trip = trips.groupby("vehicle_id")["start_time"].min()
    names = trips.groupby("vehicle_id")["vehicle_name"].first()
    week = pd.Timedelta(days=7)
    for (vid, etype), e in events.groupby(["vehicle_id", "event_type"]):
        if vid not in first_trip or first_trip[vid] > now - week * (ALERT_BASE_WEEKS / 2 + 1):
            continue  # histórico curto demais para comparar
        recent = e[e["time"] >= now - week]
        base = e[(e["time"] < now - week) & (e["time"] >= now - week * (ALERT_BASE_WEEKS + 1))]
        weeks = min(ALERT_BASE_WEEKS, max((now - week - first_trip[vid]) / week, 1.0))
        rate = len(base) / weeks
        n = len(recent)
        if n < 3 or n < 3 * max(rate, 0.5):
            continue
        p = float(stats.poisson.sf(n - 1, max(rate, 0.5)))
        if p >= 0.01:
            continue
        label = EVENT_LABELS.get(etype, etype)
        ev = {"event_type": etype, "recent": n, "baseline_per_week": round(rate, 3), "p_value": p,
              "critical_recent": int((recent["severity"] == "CRITICAL").sum())}
        out.append(Candidate(
            dedup_key=f"VEHICLE_ALERT_RECURRENCE:{vid}:{etype}", scope="VEHICLE", subject_id=str(vid),
            category="VEHICLE_ALERT_RECURRENCE",
            severity="CRITICAL" if ev["critical_recent"] > 0 else "WARNING", title="Alertas recorrentes",
            description=(f"{names.get(vid, 'Veículo')} teve {n} alertas de {label} nos últimos 7 dias "
                         f"(média das semanas anteriores: {br(rate, 1)} por semana)."),
            period_start=now - week, period_end=now, evidence=ev))
    return out


# ---------------------------------------------------------------------------------------------------------------
# 8–9. frota

def fleet_idle_cost(trips: pd.DataFrame, now: pd.Timestamp, policy: FleetPolicy) -> list[Candidate]:
    t = _peer_frame(_since(trips, now, PEER_DAYS).assign(driver_id=lambda d: d["driver_id"].fillna("-")))
    total = float(t["idle_fuel_l"].fillna(0).sum())
    excess = float(t["excess_idle_fuel_l"].sum())
    cost = total * policy.fuel_price_per_liter
    if cost < 50:
        return []
    share = excess / total if total else 0.0
    saving = excess / 2 * policy.fuel_price_per_liter
    ev = {"idle_fuel_l": round(total, 1), "excessive_idle_fuel_l": round(excess, 1), "cost": round(cost, 2),
          "saving_half_excessive": round(saving, 2), "price": policy.fuel_price_per_liter}
    return [Candidate(
        dedup_key="FLEET_IDLE_COST", scope="FLEET", subject_id=None, category="FLEET_IDLE_COST", severity="INFO",
        title="Custo da ociosidade",
        description=(f"Nos últimos {PEER_DAYS} dias a frota gastou ≈ {br(total)} L de diesel com motor ligado "
                     f"parado (≈ R$ {br(cost)}). Paradas de mais de {br(policy.idle_threshold_s / 60)} min responderam "
                     f"por {br(share * 100)}% disso; reduzi-las pela metade economizaria ≈ R$ {br(saving)} por mês."),
        period_start=now - pd.Timedelta(days=PEER_DAYS), period_end=now, evidence=ev)]


def fleet_efficiency_ranking(trips: pd.DataFrame, now: pd.Timestamp) -> list[Candidate]:
    pairs = _fuel_pairs(_since(trips, now, PEER_DAYS))
    if pairs.empty:
        return []
    agg = pairs.groupby("vehicle_id").agg(real=("fuel_real_scored", "sum"), exp=("fuel_exp_scored", "sum"),
                                          name=("vehicle_name", "first"))
    agg = agg[agg["exp"] >= 50]
    if len(agg) < 3:
        return []
    agg["ratio"] = agg["real"] / agg["exp"]
    med = float(agg["ratio"].median())
    agg["above"] = agg["ratio"] / med - 1
    worst = agg[agg["above"] >= 0.08].sort_values("above", ascending=False).head(3)
    if worst.empty:
        return []
    listing = ", ".join(f"{r['name']} (+{br(r['above'] * 100)}%)" for _, r in worst.iterrows())
    ev = {"fleet_median_ratio": round(med, 4),
          "vehicles": [{"vehicle_id": str(v), "ratio": round(r["ratio"], 4), "above_median": round(r["above"], 4)}
                       for v, r in worst.iterrows()]}
    return [Candidate(
        dedup_key="FLEET_EFFICIENCY_RANKING", scope="FLEET", subject_id=None, category="FLEET_EFFICIENCY_RANKING",
        severity="WARNING" if worst["above"].max() >= 0.20 else "INFO", title="Eficiência da frota",
        description=(f"Veículos com consumo mais acima do esperado nos últimos {PEER_DAYS} dias, em relação à "
                     f"mediana da frota (já descontados rota, carga e velocidade): {listing}."),
        period_start=now - pd.Timedelta(days=PEER_DAYS), period_end=now, evidence=ev)]


# ---------------------------------------------------------------------------------------------------------------

def evaluate(trips: pd.DataFrame, events: pd.DataFrame, now: pd.Timestamp, policy: FleetPolicy) -> list[Candidate]:
    """Todos os insights que deveriam estar abertos em `now`."""
    if trips.empty:
        return []
    return [*driver_peer_insights(trips, now, policy), *vehicle_fuel_trend(trips, now),
            *vehicle_health_drift(trips, now), *vehicle_alert_recurrence(events, trips, now),
            *fleet_idle_cost(trips, now, policy), *fleet_efficiency_ranking(trips, now)]
