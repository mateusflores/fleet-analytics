"""Relatório de treinamento (Markdown) gerado para cada empresa/versão."""

from __future__ import annotations

from ml_service.config import Config
from ml_service.synthetic.faults import FAULTS


def _pct(x) -> str:
    return "—" if x is None or x != x else f"{x * 100:.1f}%"


def render_report(cfg: Config, version: str, quality: dict, metrics: dict, seconds: float) -> str:
    L = [f"# Relatório de treinamento — {cfg.company}", "",
         f"Versão `{version}` · janela {cfg.window_size_s} s / passo {cfg.window_stride_s} s · "
         f"taxa-alvo de falsos alarmes {_pct(cfg.target_false_alarm_rate)} · aquecimento {cfg.warmup_s} s · semente {cfg.seed} · {seconds:.0f} s", ""]
    L += ["## 1. Validação dos dados", "", f"**Resultado: {'aprovado' if quality['ok'] else 'REPROVADO'}**", ""]
    for kind, title in (("errors", "Erros"), ("warnings", "Avisos")):
        if quality[kind]:
            L += [f"{title}:", *[f"- {m}" for m in quality[kind]], ""]
    for group, q in quality["groups"].items():
        L += [f"### Grupo `{group}`", "",
              f"- Veículos: {', '.join(q['vehicles'])}",
              f"- Período: {q['period'][0]} a {q['period'][1]}",
              f"- Horas: {q['hours_total']} no total, {q['hours_engine_on']} com motor ligado, em {q['segments']} segmentos"]
        if "operating_states_pct" in q:
            L.append("- Estados de operação: " + ", ".join(f"{k} {v}%" for k, v in q["operating_states_pct"].items()))
        L += ["", "| Sinal | Usado | Ausente | Fora da faixa | Mín | Média | Máx |", "|---|---|---|---|---|---|---|"]
        for s, d in q["signals"].items():
            L.append(f"| {s} | {'sim' if s in q['usable_signals'] else 'não'} | {d['missing_pct']}% | "
                     f"{d['out_of_range_pct']}% | {d['min']} | {d['mean']} | {d['max']} |")
        L.append("")
    for group, m in metrics.items():
        c = m["clean_test"]
        L += [f"## 2. Modelo do grupo `{group}`", "",
              f"- Sinais: {', '.join(m['signals'])} ({m['n_features']} features por janela)",
              f"- Janelas: {m['windows']['train']} de treino ({m['n_train_in_scope']} fora do aquecimento, usadas no ajuste), "
              f"{m['windows']['test']} de teste ({m['windows']['gap']} descartadas na fronteira)",
              f"- Treino: {m['split_detail'].get('train', {})}",
              f"- Teste: {m['split_detail'].get('test', {})}", "",
              "### Falsos alarmes em dados normais de teste", "",
              f"- Janelas avaliadas: {c['n_scored']} de {c['n_windows']} "
              f"({_pct(c['out_of_scope_frac'])} fora do escopo por estarem no aquecimento)",
              f"- Combinado: **{_pct(c['false_alarm_rate'])}** das janelas "
              f"(≈ {c['false_alarms_per_hour']:.1f} janelas com alarme por hora; alvo {_pct(cfg.target_false_alarm_rate)})",
              f"- IsolationForest: {_pct(c['far_isolation_forest'])} · valor esperado: {_pct(c['far_expected_value'])}", ""]
        if c["expected_value_quality"]:
            L += ["### Modelos de valor esperado", "",
                  "| Alvo | MAE teste | R² teste | MAE validação cruzada | R² validação cruzada | Limiar do resíduo | Falsos alarmes |",
                  "|---|---|---|---|---|---|---|"]
            for t, q in c["expected_value_quality"].items():
                r2 = "—" if q["r2"] is None else f"{q['r2']:.2f}"
                L.append(f"| {q['target']} | {q['mae']:.3g} | {r2} | {q['cv_mae']:.3g} | {q['cv_r2']:.2f} | "
                         f"{q['threshold']:.3g} | {_pct(c['far_by_signal'][t])} |")
            L.append("")
        L += ["### Detecção de falhas sintéticas (injetadas nos dados de teste)", "",
              f"Eventos de {cfg.fault_duration_s} s a cada {cfg.fault_period_s} s; rampa na primeira metade. "
              "Janela positiva = ≥ 50% dentro do evento; janelas parcialmente sobrepostas são excluídas.", "",
              "| Falha | Severidade | Precisão | Recall | F1 | AUC | Recall IF | Recall VE | Eventos detectados | Atraso mediano |",
              "|---|---|---|---|---|---|---|---|---|---|"]
        for r in m["faults"]:
            delay = "—" if r["median_delay_s"] is None else f"{r['median_delay_s']:.0f} s"
            L.append(f"| {r['fault']} | {r['severity']} | {_pct(r['precision'])} | {_pct(r['recall'])} | {r['f1']:.2f} | "
                     f"{r['auc']:.3f} | {_pct(r['recall_isolation_forest'])} | {_pct(r['recall_expected_value'])} | "
                     f"{r['events_detected']}/{r['events']} | {delay} |")
        used = {r["fault"] for r in m["faults"]}
        L += ["", "Falhas (severidade 1,0): " + "; ".join(f"`{n}` — {FAULTS[n].description}" for n in FAULTS if n in used) + ".",
              "", "IF = IsolationForest; VE = valor esperado. A precisão depende da proporção de janelas com falha "
              "no conjunto injetado (cerca de um terço), não da frequência real de falhas.", ""]
    L += ["## 3. Limitações", "",
          "- As falhas são sintéticas: medem a sensibilidade a desvios de magnitude conhecida, não a falhas reais.",
          f"- Não há detecção nos primeiros {cfg.warmup_s} s após a partida do motor (aquecimento).",
          "- O modelo vale só para os grupos de veículo e condições presentes nos dados de treino.",
          "- Os limiares foram calibrados por validação cruzada em blocos temporais do treino; a taxa de falsos "
          "alarmes no teste pode diferir do alvo se a operação do teste for diferente.", ""]
    return "\n".join(L)
