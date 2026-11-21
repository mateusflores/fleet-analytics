package com.unesp.desktopsimulator.app.services.simulation.route;

import java.util.List;

/**
 * Rota percorrida pelo veículo, com consultas por distância percorrida (m).
 *
 * <p>Dois tratamentos sobre os dados brutos:
 * <ul>
 *   <li>a rampa é calculada sobre ±500 m (a altitude SRTM é ruidosa a cada 100 m) e limitada a ±8%;</li>
 *   <li>o limite de velocidade passa por abertura e fechamento morfológicos de ±300 m, que removem picos e
 *       vales curtos vindos da estimativa por trecho do OSRM.</li>
 * </ul>
 */
public final class Route {

    private static final int GRADE_HALF_WINDOW = 5;
    private static final double MAX_GRADE = 0.08;
    private static final int LIMIT_HALF_WINDOW = 3;

    private final String id;
    private final String name;
    private final RouteKind kind;
    private final List<RoutePoint> points;
    private final double[] dist;
    private final double[] grade;
    private final double[] limitKmh;

    public Route(String id, String name, RouteKind kind, List<RoutePoint> points) {
        if (points.size() < 2) throw new IllegalArgumentException("route needs at least 2 points");
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.points = List.copyOf(points);
        int n = points.size();
        dist = new double[n];
        grade = new double[n];
        double[] rawLimit = new double[n];
        for (int i = 0; i < n; i++) {
            dist[i] = points.get(i).distanceM();
            rawLimit[i] = points.get(i).speedLimitKmh();
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - GRADE_HALF_WINDOW), b = Math.min(n - 1, i + GRADE_HALF_WINDOW);
            double dx = dist[b] - dist[a];
            double dz = points.get(b).elevationM() - points.get(a).elevationM();
            double g = dx > 0 && !Double.isNaN(dz) ? dz / dx : 0;
            grade[i] = Math.max(-MAX_GRADE, Math.min(MAX_GRADE, g));
        }
        double[] opened = dilate(erode(rawLimit, LIMIT_HALF_WINDOW), LIMIT_HALF_WINDOW);
        limitKmh = erode(dilate(opened, LIMIT_HALF_WINDOW), LIMIT_HALF_WINDOW);
    }

    private static double[] erode(double[] v, int w) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double m = Double.MAX_VALUE;
            for (int j = Math.max(0, i - w); j <= Math.min(v.length - 1, i + w); j++) m = Math.min(m, v[j]);
            out[i] = m;
        }
        return out;
    }

    private static double[] dilate(double[] v, int w) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double m = -Double.MAX_VALUE;
            for (int j = Math.max(0, i - w); j <= Math.min(v.length - 1, i + w); j++) m = Math.max(m, v[j]);
            out[i] = m;
        }
        return out;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public RouteKind kind() {
        return kind;
    }

    public List<RoutePoint> points() {
        return points;
    }

    public double lengthM() {
        return dist[dist.length - 1];
    }

    /** Índice do último ponto com distância ≤ d. */
    private int indexAt(double d) {
        if (d <= 0) return 0;
        if (d >= lengthM()) return dist.length - 2;
        int lo = 0, hi = dist.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (dist[mid] <= d) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private double fraction(int i, double d) {
        double span = dist[i + 1] - dist[i];
        return span > 0 ? Math.max(0, Math.min(1, (d - dist[i]) / span)) : 0;
    }

    /** Rampa (fração, positiva = subida) no sentido de ida. */
    public double gradeAt(double d) {
        int i = indexAt(d);
        double f = fraction(i, d);
        return grade[i] + f * (grade[i + 1] - grade[i]);
    }

    /** Limite de velocidade (km/h) suavizado. */
    public double speedLimitAt(double d) {
        return limitKmh[indexAt(d) + (fraction(indexAt(d), d) > 0.5 ? 1 : 0)];
    }

    /** Latitude e longitude interpoladas. */
    public double[] latLonAt(double d) {
        int i = indexAt(d);
        double f = fraction(i, d);
        RoutePoint a = points.get(i), b = points.get(i + 1);
        return new double[]{a.lat() + f * (b.lat() - a.lat()), a.lon() + f * (b.lon() - a.lon())};
    }

    /** Rumo (graus, 0 = norte) no sentido de ida. */
    public double headingAt(double d) {
        int i = indexAt(d);
        RoutePoint a = points.get(i), b = points.get(i + 1);
        double lat1 = Math.toRadians(a.lat()), lat2 = Math.toRadians(b.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
