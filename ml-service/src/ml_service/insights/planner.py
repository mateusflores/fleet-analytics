"""Reconciliação entre os insights candidatos de uma execução e os já gravados.

Regras:
- candidato com insight aberto (ACTIVE/ACKNOWLEDGED) de mesma `dedup_key` → atualiza números e textos; se a
  gravidade subir, um insight já reconhecido volta a ACTIVE;
- candidato sem insight aberto → cria, exceto se o usuário descartou um de mesma chave há menos de
  `DISMISS_SNOOZE_DAYS` dias e a gravidade não subiu;
- insight aberto sem candidato → RESOLVED (a condição deixou de existir), mas só depois de `RESOLVE_AFTER_H`
  horas sem ser confirmado (`updated_at` = última execução em que a condição apareceu): histerese que evita abrir e
  fechar o mesmo insight quando a métrica oscila perto do limiar.
"""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from ml_service.insights.rules import SEVERITY_RANK, Candidate

DISMISS_SNOOZE_DAYS = 14
RESOLVE_AFTER_H = 48


@dataclass
class Action:
    kind: str                       # insert | update | resolve
    candidate: Candidate | None = None
    insight_id: str | None = None
    reopen: bool = False


def plan(candidates: list[Candidate], open_insights: pd.DataFrame, dismissed: pd.DataFrame,
         now: pd.Timestamp) -> list[Action]:
    """`open_insights`: id, dedup_key, status, severity e, opcionalmente, updated_at (sem ela, resolve na hora).
    `dismissed`: dedup_key, severity, updated_at."""
    actions: list[Action] = []
    open_by_key = {r["dedup_key"]: r for _, r in open_insights.iterrows()} if not open_insights.empty else {}
    snooze = {}
    if not dismissed.empty:
        recent = dismissed[dismissed["updated_at"] >= now - pd.Timedelta(days=DISMISS_SNOOZE_DAYS)]
        for _, r in recent.iterrows():
            rank = SEVERITY_RANK.get(r["severity"], 0)
            snooze[r["dedup_key"]] = max(rank, snooze.get(r["dedup_key"], -1))
    seen = set()
    for c in candidates:
        if c.dedup_key in seen:
            continue
        seen.add(c.dedup_key)
        cur = open_by_key.get(c.dedup_key)
        if cur is not None:
            escalated = SEVERITY_RANK[c.severity] > SEVERITY_RANK.get(cur["severity"], 0)
            actions.append(Action("update", c, str(cur["id"]), reopen=escalated and cur["status"] == "ACKNOWLEDGED"))
        elif c.dedup_key in snooze and SEVERITY_RANK[c.severity] <= snooze[c.dedup_key]:
            continue
        else:
            actions.append(Action("insert", c))
    grace = pd.Timedelta(hours=RESOLVE_AFTER_H)
    for key, cur in open_by_key.items():
        if key in seen:
            continue
        last_seen = cur.get("updated_at")
        if last_seen is not None and not pd.isna(last_seen) and now - pd.Timestamp(last_seen) < grace:
            continue  # ainda dentro da histerese
        actions.append(Action("resolve", insight_id=str(cur["id"])))
    return actions
