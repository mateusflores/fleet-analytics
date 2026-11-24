"""Avaliação do motor de insights sobre o histórico de demonstração (replay dia a dia, em memória).

Para cada dia da avaliação, roda as mesmas regras (`rules.evaluate`) e a mesma reconciliação (`planner.plan`) que o
executor agendado, com o histórico disponível até aquele dia, e registra os episódios (abertura → resolução) de
cada insight. Depois compara com a verdade conhecida (`ground_truth.json`):

- **detectado**: algum episódio da mesma categoria e assunto (e sinal, no desgaste) aberto enquanto a condição
  existe (de `start` até o conserto + `RESOLVE_GRACE_DAYS`);
- **atraso**: dias entre o início da condição (ou o início da avaliação, se já existia) e a abertura;
- **resolução**: dias entre o conserto e o fechamento do insight (cenários com `resolves`);
- **cobertura**: fração dos dias com a condição presente em que o insight esteve aberto;
- **falso**: episódio de veículo/motorista sem cenário ativo para o assunto; **relacionado**: assunto com cenário
  ativo, mas categoria não prevista (ex.: falha que também altera outro sinal);
- **oscilação**: reaberturas da mesma chave (episódios além do primeiro).

Insights de frota (custo de ociosidade, ranking) não têm assunto e são relatados à parte.

Uso: python -m ml_service.insights.demo.evaluate --demo data/demo [--warmup-days 90]
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass, field
from pathlib import Path

import pandas as pd

from ml_service.insights.demo.build import policy as demo_policy
from ml_service.insights.demo.scenario import DemoConfig
from ml_service.insights.planner import plan
from ml_service.insights.rules import evaluate

LOOKBACK_DAYS = 100        # mesmo histórico lido pelo executor (configs/insights.yaml)
RESOLVE_GRACE_DAYS = 21    # um insight de uma falha consertada ainda é "verdadeiro" enquanto a janela não a esquece


@dataclass
class Episode:
    dedup_key: str
    category: str
    scope: str
    subject: str | None
    signal: str | None
    opened: pd.Timestamp
    closed: pd.Timestamp | None = None
    severities: list = field(default_factory=list)
    descriptions: list = field(default_factory=list)


def rule_inputs(demo: Path, cfg: DemoConfig) -> tuple[pd.DataFrame, pd.DataFrame]:
    """trip_metrics/events do histórico → formato que as regras recebem (como `db.load_trips_for_rules`)."""
    trips = pd.read_parquet(demo / "trip_metrics.parquet")
    trips = trips[trips["engine_on_s"].fillna(0) > 0].copy()
    names_v = {v["key"]: v["nickname"] for v in cfg.vehicles}
    names_d = {d["key"]: f"{d['first']} {d['last']}" for d in cfg.drivers}
    trips["vehicle_id"] = trips["vehicle_key"]
    trips["driver_id"] = trips["driver_key"]
    trips["vehicle_type"] = cfg.raw.get("vehicle_type", "RIGID_TRUCK")
    trips["vehicle_name"] = trips["vehicle_key"].map(names_v)
    trips["driver_name"] = trips["driver_key"].map(names_d)
    trips["health"] = trips["health"].map(json.loads)
    trips["start_time"] = pd.to_datetime(trips["start_time"], utc=True)
    events = pd.read_parquet(demo / "events.parquet").rename(columns={"vehicle": "vehicle_id"})
    events["time"] = pd.to_datetime(events["time"], utc=True)
    return trips, events[["time", "vehicle_id", "event_type", "severity"]]


def replay(trips: pd.DataFrame, events: pd.DataFrame, days: list[pd.Timestamp], pol) -> list[Episode]:
    open_: dict[str, tuple[str, Episode]] = {}   # dedup_key -> (id, episódio)
    last_seen: dict[str, pd.Timestamp] = {}      # como updated_at no banco: última execução com a condição
    episodes: list[Episode] = []
    seq = 0
    for now in days:
        since = now - pd.Timedelta(days=LOOKBACK_DAYS)
        t = trips[(trips["start_time"] >= since) & (trips["start_time"] < now)]
        e = events[(events["time"] >= since) & (events["time"] < now)]
        cands = evaluate(t, e, now, pol)
        open_df = pd.DataFrame([{"id": i, "dedup_key": k, "status": "ACTIVE", "severity": ep.severities[-1],
                                 "updated_at": last_seen[k]} for k, (i, ep) in open_.items()])
        for a in plan(cands, open_df, pd.DataFrame(), now):
            if a.kind == "resolve":
                key = next(k for k, (i, _) in open_.items() if i == a.insight_id)
                open_.pop(key)[1].closed = now
                continue
            c = a.candidate
            if a.kind == "insert":
                seq += 1
                ep = Episode(c.dedup_key, c.category, c.scope, c.subject_id, c.evidence.get("signal"), now)
                episodes.append(ep)
                open_[c.dedup_key] = (str(seq), ep)
            ep = open_[c.dedup_key][1]
            last_seen[c.dedup_key] = now
            ep.severities.append(c.severity)
            ep.descriptions.append(c.description)
    return episodes


def _match(ep: Episode, gt: dict) -> bool:
    return (ep.category == gt["category"] and ep.subject == gt["subject"]
            and (gt["signal"] is None or ep.signal == gt["signal"]))


def score(episodes: list[Episode], truth: list[dict], eval_start: pd.Timestamp, eval_end: pd.Timestamp) -> dict:
    day = pd.Timedelta(days=1)
    per_gt, matched = [], set()
    for gt in truth:
        start = max(pd.Timestamp(gt["start"], tz="UTC"), eval_start)
        end = pd.Timestamp(gt["end"], tz="UTC") if gt["end"] else None
        hits = _hits_of(gt, episodes, eval_start, eval_end)
        matched.update(id(ep) for ep in hits)
        first = min((ep.opened for ep in hits), default=None)
        cond_end = end if end is not None else eval_end + day
        cond_days = max((cond_end - start) / day, 1)
        covered = sum(((min(ep.closed or eval_end + day, cond_end) - max(ep.opened, start)) / day)
                      for ep in hits if (ep.closed or eval_end + day) > start and ep.opened < cond_end)
        resolved_after = None
        if gt["resolves"] and end is not None:
            closes = [ep.closed for ep in hits if ep.closed is not None and ep.closed >= end]
            resolved_after = round((min(closes) - end) / day, 1) if closes else None
        per_gt.append({**gt, "detected": first is not None,
                       "delay_days": round((first - start) / day, 1) if first is not None else None,
                       "started_before_eval": pd.Timestamp(gt["start"], tz="UTC") < eval_start,
                       "coverage": round(max(covered, 0) / cond_days, 3), "episodes": len(hits),
                       "resolved_after_days": resolved_after,
                       "max_severity": max((s for ep in hits for s in ep.severities),
                                           key=["INFO", "WARNING", "CRITICAL"].index, default=None),
                       "example": hits[0].descriptions[-1] if hits else None})

    def active_subjects(ts: pd.Timestamp) -> set:
        out = set()
        for gt in truth:
            s, e = pd.Timestamp(gt["start"], tz="UTC"), gt["end"] and pd.Timestamp(gt["end"], tz="UTC")
            if s - day <= ts and (not e or ts < e + pd.Timedelta(days=RESOLVE_GRACE_DAYS)):
                out.add(gt["subject"])
        return out

    false, related, fleet = [], [], []
    for ep in episodes:
        if ep.scope == "FLEET":
            fleet.append(ep)
        elif id(ep) in matched:
            continue
        elif ep.subject in active_subjects(ep.opened):
            related.append(ep)
        else:
            false.append(ep)
    keys = pd.Series([ep.dedup_key for ep in episodes if ep.scope != "FLEET"])
    flaps = int((keys.value_counts() - 1).clip(lower=0).sum()) if not keys.empty else 0

    def ep_dict(ep: Episode) -> dict:
        return {"category": ep.category, "subject": ep.subject, "signal": ep.signal,
                "opened": ep.opened.date().isoformat(), "closed": ep.closed.date().isoformat() if ep.closed else None,
                "days_open": round(((ep.closed or eval_end + day) - ep.opened) / day, 1),
                "max_severity": max(ep.severities, key=["INFO", "WARNING", "CRITICAL"].index),
                "text": ep.descriptions[-1]}

    required = [g for g in per_gt if not g["optional"]]
    return {
        "eval_start": eval_start.date().isoformat(), "eval_end": eval_end.date().isoformat(),
        "expected": len(required), "detected": sum(g["detected"] for g in required),
        "false_episodes": len(false), "false_insight_days": round(sum(ep_dict(e)["days_open"] for e in false), 1),
        "related_episodes": len(related), "reopenings": flaps,
        "per_expectation": per_gt, "false": [ep_dict(e) for e in false], "related": [ep_dict(e) for e in related],
        "fleet": {"episodes": len(fleet), "categories": sorted({e.category for e in fleet}),
                  "examples": [ep_dict(e) for e in fleet[-3:]]},
    }


def _hits_of(gt: dict, episodes: list[Episode], eval_start, eval_end) -> list[Episode]:
    day = pd.Timedelta(days=1)
    start = max(pd.Timestamp(gt["start"], tz="UTC"), eval_start)
    end = pd.Timestamp(gt["end"], tz="UTC") if gt["end"] else None
    window_end = (end + pd.Timedelta(days=RESOLVE_GRACE_DAYS)) if end is not None else eval_end + day
    return [ep for ep in episodes if _match(ep, gt) and start - day <= ep.opened < window_end]


def markdown(result: dict) -> str:
    lines = [f"Avaliação de {result['eval_start']} a {result['eval_end']}: "
             f"{result['detected']}/{result['expected']} insights esperados detectados; "
             f"{result['false_episodes']} episódios falsos ({result['false_insight_days']} insight-dias); "
             f"{result['related_episodes']} relacionados; {result['reopenings']} reaberturas.", "",
             "| Cenário | Insight esperado | Assunto | Detectado | Atraso (dias) | Cobertura | Resolução (dias) | Gravidade máx. |",
             "|---|---|---|---|---|---|---|---|"]
    for g in result["per_expectation"]:
        cat = g["category"] + (f" ({g['signal']})" if g["signal"] else "") + (" — opcional" if g["optional"] else "")
        delay = "—" if g["delay_days"] is None else (f"{g['delay_days']}" + (" ¹" if g["started_before_eval"] else ""))
        res = "—" if not g["resolves"] else ("não resolvido" if g["resolved_after_days"] is None
                                             else f"{g['resolved_after_days']}")
        lines.append(f"| {g['scenario']} | {cat} | {g['subject']} | {'sim' if g['detected'] else 'não'} | {delay} | "
                     f"{g['coverage']:.0%} | {res} | {g['max_severity'] or '—'} |")
    lines.append("")
    lines.append("¹ condição presente desde antes do início da avaliação: atraso contado a partir do início da avaliação.")
    return "\n".join(lines)


def run(demo: Path, config: Path, warmup_days: int, out: Path | None) -> dict:
    manifest = json.loads((demo / "manifest.json").read_text())
    cfg = DemoConfig.load(config, pd.Timestamp(manifest["end"]).date())
    trips, events = rule_inputs(demo, cfg)
    truth = json.loads((demo / "ground_truth.json").read_text())
    first = pd.Timestamp(manifest["start"], tz="UTC") + pd.Timedelta(days=warmup_days)
    last = pd.Timestamp(manifest["end"], tz="UTC")
    # avaliado ao fim de cada dia (o executor real roda de hora em hora; um passo diário basta para medir atraso)
    days = list(pd.date_range(first + pd.Timedelta(days=1), last + pd.Timedelta(days=1), freq="D"))
    episodes = replay(trips, events, days, demo_policy())
    result = score(episodes, truth, first, last)
    if out:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, indent=2, ensure_ascii=False, default=str))
    return result


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--demo", default="data/demo")
    ap.add_argument("--config", default="configs/demo-history.yaml")
    ap.add_argument("--warmup-days", type=int, default=90, help="dias iniciais só para linhas de base")
    ap.add_argument("--out", default="reports/insights/e14_demo_evaluation.json")
    a = ap.parse_args()
    result = run(Path(a.demo), Path(a.config), a.warmup_days, Path(a.out))
    print(markdown(result))
    for kind in ("false", "related"):
        for e in result[kind]:
            print(f"[{kind}] {e['opened']}–{e['closed']} {e['category']} {e['subject']} {e['signal'] or ''}: {e['text']}")


if __name__ == "__main__":
    main()
