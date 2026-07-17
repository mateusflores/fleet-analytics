package com.unesp.desktopsimulator.model;

import java.time.ZonedDateTime;

public record RawData(
        ZonedDateTime timestamp,
        String identifier,
        String value) {
}
