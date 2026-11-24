"""Políticas da frota (tabelas fleet.fleet_policy e fleet.vehicle_type_policy, V13)."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field

DEFAULT_ECONOMY_RPM_MAX = 2000


@dataclass(frozen=True)
class FleetPolicy:
    speed_limit_kmh: float = 90.0
    harsh_accel_ms2: float = 0.7
    harsh_brake_ms2: float = 1.5
    idle_threshold_s: int = 300
    fuel_price_per_liter: float = 6.20
    economy_rpm_max: dict[str, int] = field(default_factory=dict)  # vehicle_type -> rpm

    def rpm_max_for(self, vehicle_type: str | None) -> int:
        return int(self.economy_rpm_max.get(vehicle_type or "", DEFAULT_ECONOMY_RPM_MAX))

    def snapshot(self, vehicle_type: str | None) -> dict:
        """Limites aplicados a uma viagem (gravados em trip_metrics.policy, para auditoria)."""
        d = asdict(self)
        d.pop("economy_rpm_max")
        d["economy_rpm_max"] = self.rpm_max_for(vehicle_type)
        return d
