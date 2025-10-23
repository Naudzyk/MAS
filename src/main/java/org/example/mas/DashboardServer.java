package org.example.mas;

import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class DashboardServer {

    protected static final Logger logger = LoggerFactory.getLogger(DashboardServer.class);

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

        // СТАТИЧЕСКИЕ ФАЙЛЫ — ОБЯЗАТЕЛЬНО ПЕРВЫМ!
        Spark.staticFileLocation("/public");

        // API: получение статуса
        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });

        // API: запуск развёртывания
        Spark.post("/api/deploy", (req, res) -> {
            try {
                DeployRequest request = new Gson().fromJson(req.body(), DeployRequest.class);
                if (request.master == null || request.workers == null || request.workers.isEmpty()) {
                    res.status(400);
                    return new Gson().toJson(Map.of("error", "Master and at least one worker required"));
                }

                // Генерируем временный inventory
                Path tempDir = Files.createTempDirectory("mas-deploy-");
                Path inventoryPath = tempDir.resolve("inventory.ini");
                generateInventoryFile( request.master,request.workers,request.nameHostCM,request.nameHostEX);

                // Отправляем команду координатору
                AgentController ac = jadeContainer.getAgent("coordinator");
                ac.putO2AObject("DEPLOY:" + inventoryPath.toAbsolutePath() + ",scripts/", false);

                return new Gson().toJson(Map.of(
                    "status", "started",
                    "message", "Deployment initiated"
                ));
            } catch (Exception e) {
                res.status(500);
                return new Gson().toJson(Map.of("error", e.getMessage()));
            }
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
            res.type("text/plain; charset=utf-8");
            return new String(Files.readAllBytes(Paths.get(logFile)), StandardCharsets.UTF_8);
        } else {
            res.status(404);
            return "Logs not available. Click 'Collect logs' first.";
        }
    });

        System.out.println("Dashboard available at http://localhost:" + port);

    }

    // Метод для обновления статуса из агентов
    public static void updateStatus(String key, Object value) {
        STATUS.put(key, value);
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }
    public static class DeployRequest {
        private String master;
        private String nameHostCM;
        private List<String> workers;
        private List<String> nameHostEX;
    }

    public static String generateInventoryFile( String masterIps,  List<String> workerIps, String nameHostCM, List<String> nameHostEX) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("[central_manager]\n]");
        sb.append("central_manager ansible_host=").append(masterIps).append("ansible_user=").append(nameHostCM).append("\n\n");

        sb.append("[execute_nodes]\n");
        for(int i = 0; i < workerIps.size(); i++){
            sb.append("worker").append(i+1)
                    .append("ansible_host=").append(workerIps.get(i))
                    .append("ansible_user=").append(nameHostEX.get(i))
                    .append("\n");

        }
        return sb.toString();


    }
}
