package com.unesp.desktopsimulator.app.services.mqtt;

import tools.jackson.databind.ObjectMapper;
import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.RawData;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);
    private static final int QOS = 1;

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    public MqttPublisher(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connect() {
        if (mqttClient.isConnected()) return;
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            mqttClient.connect(options);
            log.info("Connected to MQTT broker at {}", mqttClient.getServerURI());
        } catch (MqttException e) {
            log.warn("Could not connect to MQTT broker: {}", e.getMessage());
        }
    }

    public void publish(RawData data) {
        if (!mqttClient.isConnected()) {
            log.debug("MQTT not connected, skipping publish for {}", data.getClass().getSimpleName());
            return;
        }
        try {
            String topic = resolveTopic(data);
            String payload = objectMapper.writeValueAsString(data);
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(QOS);
            mqttClient.publish(topic, message);
            log.debug("Published to {}: {}", topic, payload);
        } catch (Exception e) {
            log.warn("Failed to publish {}: {}", data.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String resolveTopic(RawData data) {
        String base = "fleet/" + data.getVehicleId();
        if (data instanceof Position) return base + "/position";
        if (data instanceof Telemetry) return base + "/telemetry";
        if (data instanceof Message) return base + "/message";
        throw new IllegalArgumentException("Unknown data type: " + data.getClass());
    }

}
