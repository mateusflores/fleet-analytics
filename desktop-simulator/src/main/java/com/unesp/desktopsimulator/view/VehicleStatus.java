package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.view.components.Theme;

import java.awt.*;

enum VehicleStatus {
    AVAILABLE("Disponível", Theme.IDLE),
    RUNNING("Em simulação", Theme.OK),
    EXTERNAL("Em viagem (outro terminal)", Theme.EXTERNAL);

    final String label;
    final Color color;

    VehicleStatus(String label, Color color) {
        this.label = label;
        this.color = color;
    }
}
