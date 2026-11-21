package com.unesp.desktopsimulator.app.services.simulation.route;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rotas empacotadas em {@code src/main/resources/routes/} (geradas por tools/fetch_routes.py). */
@Component
public class RouteCatalog {

    public static final List<String> ROUTE_IDS = List.of("campinas-sao-paulo", "sao-paulo-santos", "campinas-urbano");

    private final Map<String, Route> routes = new LinkedHashMap<>();

    public RouteCatalog() {
        ObjectMapper mapper = new ObjectMapper();
        for (String id : ROUTE_IDS) {
            routes.put(id, load(mapper, id));
        }
    }

    private static Route load(ObjectMapper mapper, String id) {
        String path = "/routes/" + id + ".json";
        try (InputStream in = RouteCatalog.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("route resource not found: " + path);
            JsonNode root = mapper.readTree(in);
            List<RoutePoint> points = new ArrayList<>();
            for (JsonNode p : root.get("points")) {
                points.add(new RoutePoint(p.get("lat").asDouble(), p.get("lon").asDouble(),
                        p.get("elevationM").asDouble(), p.get("speedLimitKmh").asInt(), p.get("distanceM").asDouble()));
            }
            return new Route(id, root.get("name").asText(), RouteKind.valueOf(root.get("kind").asText()), points);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Route> all() {
        return List.copyOf(routes.values());
    }

    public Route get(String id) {
        Route r = routes.get(id);
        if (r == null) throw new IllegalArgumentException("unknown route: " + id);
        return r;
    }

    /** Rota padrão de um veículo: estável por veículo, para que cada um rode sempre no mesmo trajeto. */
    public Route defaultFor(UUID vehicleId) {
        List<Route> list = all();
        return list.get(Math.floorMod(vehicleId.hashCode(), list.size()));
    }
}
