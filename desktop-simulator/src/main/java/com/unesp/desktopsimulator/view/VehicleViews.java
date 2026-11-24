package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.model.Vehicle;

/** Textos de exibição de um veículo. */
final class VehicleViews {

    private VehicleViews() {
    }

    static String displayName(Vehicle v) {
        return v.getNickname() != null && !v.getNickname().isBlank() ? v.getNickname() : v.getLicensePlate();
    }

    static String subtitle(Vehicle v) {
        StringBuilder sb = new StringBuilder(v.getLicensePlate() != null ? v.getLicensePlate() : "");
        String model = join(v.getBrand(), v.getModel());
        if (!model.isEmpty()) sb.append(" · ").append(model);
        if (v.getYear() != null) sb.append(" ").append(v.getYear());
        return sb.toString();
    }

    static String typeLabel(Vehicle v) {
        if (v.getVehicleType() == null) return "";
        return switch (v.getVehicleType()) {
            case "RIGID_TRUCK" -> "Caminhão rígido";
            case "ARTICULATED_VEHICLE" -> "Cavalo mecânico";
            case "PASSAGER_VEHICLE" -> "Veículo de passageiros";
            case "AGRICULTURAL_VEHICLE" -> "Veículo agrícola";
            default -> v.getVehicleType();
        };
    }

    static boolean matches(Vehicle v, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase().trim();
        return contains(v.getNickname(), q) || contains(v.getLicensePlate(), q)
                || contains(v.getBrand(), q) || contains(v.getModel(), q);
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private static String join(String a, String b) {
        String x = a != null ? a.trim() : "";
        String y = b != null ? b.trim() : "";
        return (x + " " + y).trim();
    }
}
