package com.unesp.desktopsimulator.app.services.simulation.route;

/** Ponto do traçado, a cada ~100 m (ver tools/fetch_routes.py). */
public record RoutePoint(double lat, double lon, double elevationM, int speedLimitKmh, double distanceM) {
}
