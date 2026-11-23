package com.unesp.fleetanalyticsapi.app.messaging;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Colunas e mapeamento de {@code telemetry.vehicle_telemetry}, compartilhados por consultas REST e SSE. */
public final class TelemetryRows {

    /** Começa com espaço e termina em quebra de linha: blocos de texto removem espaços finais da linha anterior. */
    public static final String COLUMNS = " " + """
            time, vehicle_id, trip_id, engine_rpm, fuel_level_pct, engine_temp_c, odometer_km, battery_voltage,
            speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, oil_kpa, fuel_rate_lph, boost_kpa, intake_c
            """;

    public static final RowMapper<TelemetryMessage> MAPPER = TelemetryRows::map;

    private TelemetryRows() {
    }

    private static TelemetryMessage map(ResultSet rs, int rowNum) throws SQLException {
        Short gear = rs.getObject("gear", Short.class);
        return new TelemetryMessage(
                rs.getObject("vehicle_id", UUID.class),
                rs.getObject("trip_id", UUID.class),
                rs.getTimestamp("time").toInstant(),
                rs.getObject("engine_rpm", Integer.class),
                rs.getObject("fuel_level_pct", Double.class),
                rs.getObject("engine_temp_c", Double.class),
                rs.getObject("odometer_km", Double.class),
                rs.getObject("battery_voltage", Double.class),
                rs.getObject("speed_kmh", Double.class),
                rs.getObject("load_pct", Double.class),
                rs.getObject("engine_torque_pct", Double.class),
                rs.getObject("pedal_pct", Double.class),
                gear != null ? gear.intValue() : null,
                rs.getObject("oil_kpa", Double.class),
                rs.getObject("fuel_rate_lph", Double.class),
                rs.getObject("boost_kpa", Double.class),
                rs.getObject("intake_c", Double.class));
    }
}
