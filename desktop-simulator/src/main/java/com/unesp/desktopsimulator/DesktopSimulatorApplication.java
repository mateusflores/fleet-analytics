package com.unesp.desktopsimulator;

import com.unesp.desktopsimulator.app.services.client.VehicleApiClient;
import com.unesp.desktopsimulator.app.services.simulator.SimulatorRegistry;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.view.ErrorDialog;
import com.unesp.desktopsimulator.view.LoginFrame;
import com.unesp.desktopsimulator.view.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.swing.*;
import java.util.List;

@SpringBootApplication
public class DesktopSimulatorApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        SpringApplication.run(DesktopSimulatorApplication.class, args);
    }

    @Bean
    CommandLineRunner startGui(ConfigurableApplicationContext ctx,
                                SimulatorRegistry registry,
                                VehicleApiClient apiClient) {
        return args -> SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();

            loginFrame.addPropertyChangeListener("login", evt -> {
                loginFrame.setEnabled(false);
                new SwingWorker<List<?>, Void>() {
                    @Override
                    protected List<?> doInBackground() throws Exception {
                        apiClient.login(loginFrame.getUsername(), loginFrame.getPassword());
                        List<Vehicle> vehicles = apiClient.fetchVehicles();
                        List<Driver> drivers = apiClient.fetchDrivers();
                        return List.of(vehicles, drivers);
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void done() {
                        try {
                            List<?> result = get();
                            List<Vehicle> vehicles = (List<Vehicle>) result.get(0);
                            List<Driver> drivers = (List<Driver>) result.get(1);

                            loginFrame.markLoginSuccess();
                            loginFrame.dispose();

                            SwingUtilities.invokeLater(() -> {
                                MainFrame mainFrame = new MainFrame(
                                        registry, apiClient, vehicles, drivers);
                                mainFrame.setVisible(true);
                            });
                        } catch (Exception e) {
                            loginFrame.setEnabled(true);
                            String msg = e.getCause() != null
                                    ? e.getCause().getMessage() : e.getMessage();
                            loginFrame.setError(extractError(msg));
                            loginFrame.setEnabled(true);
                        }
                    }
                }.execute();
            });

            loginFrame.setVisible(true);
        });
    }

    private static String extractError(String raw) {
        if (raw == null || raw.isBlank()) return "Connection failed.";
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            tools.jackson.databind.JsonNode root = mapper.readTree(raw);
            if (root.has("message")) return root.get("message").asText();
            if (root.has("error")) return root.get("error").asText();
        } catch (Exception ignored) {
        }
        if (raw.contains("Connection refused")) return "Could not connect to the API server.";
        if (raw.contains("401") || raw.contains("Unauthorized")) return "Invalid username or password.";
        if (raw.length() > 100) return raw.substring(0, 100) + "...";
        return raw;
    }

}
