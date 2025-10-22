package org.example.mas;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import spark.Spark;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardServer {

    private static AgentContainer jadeContainer;
    public static final Map<String, Object> STATUS = new ConcurrentHashMap<>();

    static {
        STATUS.put("", "OK");
        STATUS.put("ansibleStage", "not_started");
        STATUS.put("htcondorStatus", "unknown");
        STATUS.put("alerts", new String[0]);
        STATUS.put("diagnosticLogs", "");
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }

    public static void start(int port, AgentContainer container) {
        jadeContainer = container;
        Spark.port(port);

        Spark.staticFileLocation("/public");

        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });

        Spark.post("/api/collect-logs", (req, res) -> {
            try {
                AgentController ac = jadeContainer.getAgent("coordinator");
                ac.putO2AObject("COMMAND: COLLECT_DIAGNOSTIC_LOGS", false);
                return "{\"status\":\"started\",\"message\":\"Diagnostic logs collection initiated\"}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        });

        Spark.get("/logs", (req, res) -> {
            String logFile = (String) STATUS.get("diagnosticLogs");
            if (logFile != null && !logFile.isEmpty() && Files.exists(Paths.get(logFile))) {
                res.type("text/plain");
                return new String(Files.readAllBytes(Paths.get(logFile)));
            } else {
                res.status(404);
                return "Logs not available";
            }
        });

        System.out.println("Dashboard available at http://localhost:" + port);
    }

    // Метод для обновления статуса из агентов
    public static void updateStatus(String key, Object value) {
        STATUS.put(key, value);
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }
}
