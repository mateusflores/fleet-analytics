package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Service
public class PositionGenerator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_SPEED_KMH = 120.0;
    private static final double SPEED_STDDEV_KMH = 3.0;
    private static final double HEADING_STDDEV_DEG = 2.0;
    private static final double ACCEL_LIMIT_KMH = 15.0;
    private static final double START_PROBABILITY = 0.05;
    private static final double STOP_PROBABILITY = 0.01;
    private static final double BASE_ACCURACY = 5.0;
    private static final double INITIAL_LAT = -23.5505;
    private static final double INITIAL_LON = -46.6333;

    private final Random random = new Random();

    public Position generate(Position lastPosition, Telemetry lastTelemetry) {
        Instant now = Instant.now();

        if (lastPosition == null) {
            return initialPosition(now);
        }

        double hoursDelta = deltaHours(lastPosition.getTimestamp(), now);
        if (hoursDelta <= 0) {
            hoursDelta = 1.0 / 3600.0;
        }

        double newSpeed = evolveSpeed(lastPosition.getSpeedKmh(), hoursDelta);
        double newHeading = evolveHeading(lastPosition.getHeading(), newSpeed);
        double newLat;
        double newLon;

        if (newSpeed > 0.5) {
            double distanceKm = newSpeed * hoursDelta;
            double[] coords = moveGeodesic(
                    lastPosition.getLatitude(),
                    lastPosition.getLongitude(),
                    newHeading,
                    distanceKm
            );
            newLat = coords[0];
            newLon = coords[1];
        } else {
            newLat = lastPosition.getLatitude();
            newLon = lastPosition.getLongitude();
        }

        double newAccuracy = Math.max(1.0, BASE_ACCURACY + randomGaussian(0, 2));

        return new Position.Builder()
                .timestamp(now)
                .latitude(newLat)
                .longitude(newLon)
                .speedKmh(newSpeed)
                .heading(newHeading)
                .accuracy(newAccuracy)
                .build();
    }

    private Position initialPosition(Instant now) {
        double lat = INITIAL_LAT + random.nextDouble() * 0.01 - 0.005;
        double lon = INITIAL_LON + random.nextDouble() * 0.01 - 0.005;
        return new Position.Builder()
                .timestamp(now)
                .latitude(lat)
                .longitude(lon)
                .speedKmh(0.0)
                .heading(random.nextDouble() * 360.0)
                .accuracy(BASE_ACCURACY + Math.abs(random.nextGaussian()))
                .build();
    }

    private double evolveSpeed(double prevSpeed, double hoursDelta) {
        if (prevSpeed < 0.5 && random.nextDouble() < START_PROBABILITY) {
            return 10.0 + random.nextDouble() * 20.0;
        }

        if (prevSpeed > 10.0 && random.nextDouble() < STOP_PROBABILITY) {
            double reduction = 15.0 + random.nextDouble() * 20.0;
            return Math.max(0, prevSpeed - reduction);
        }

        double maxStep = ACCEL_LIMIT_KMH * hoursDelta * 3600;
        double delta = randomGaussian(0, SPEED_STDDEV_KMH);
        delta = clamp(delta, -maxStep, maxStep);

        double newSpeed = prevSpeed + delta;
        return clamp(newSpeed, 0, MAX_SPEED_KMH);
    }

    private double evolveHeading(double prevHeading, double speed) {
        if (speed < 0.5) {
            return prevHeading;
        }

        double delta = randomGaussian(0, HEADING_STDDEV_DEG);
        double newHeading = prevHeading + delta;

        while (newHeading >= 360) {
            newHeading -= 360;
        }
        while (newHeading < 0) {
            newHeading += 360;
        }

        return newHeading;
    }

    private double[] moveGeodesic(double lat, double lon, double headingDeg, double distanceKm) {
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double headingRad = Math.toRadians(headingDeg);
        double distanceRad = distanceKm / EARTH_RADIUS_KM;

        double newLatRad = Math.asin(
                Math.sin(latRad) * Math.cos(distanceRad)
                        + Math.cos(latRad) * Math.sin(distanceRad) * Math.cos(headingRad)
        );

        double newLonRad = lonRad + Math.atan2(
                Math.sin(headingRad) * Math.sin(distanceRad) * Math.cos(latRad),
                Math.cos(distanceRad) - Math.sin(latRad) * Math.sin(newLatRad)
        );

        return new double[]{Math.toDegrees(newLatRad), Math.toDegrees(newLonRad)};
    }

    private double deltaHours(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / (1000.0 * 3600.0);
    }

    private double randomGaussian(double mean, double stddev) {
        return mean + random.nextGaussian() * stddev;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
