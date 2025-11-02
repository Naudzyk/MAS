package org.example.mas;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;


import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DashboardServer {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServer.class);
    private static AgentContainer jadeContainer;

    // Общее состояние системы, обновляемое агентами
    public static final Map<String, Object> STATUS = new ConcurrentHashMap<>();

    static {
        // Инициализация начального статуса
        STATUS.put("ansibleStage", "WAITING_FOR_DEPLOYMENT_START");
        STATUS.put("clusterStatus", "NOT_DEPLOYED");
        STATUS.put("alerts", new String[0]);
        STATUS.put("diagnosticLogs", "");
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }

    /**
     * Запускает веб-сервер Dashboard.
     *
     * @param port Номер порта для веб-сервера.
     * @param container Контейнер JADE для взаимодействия с агентами.
     */
    public static void start(int port, AgentContainer container) {
        jadeContainer = container;
        Spark.port(port);

        // ВАЖНО: staticFileLocation ДОЛЖЕН быть ПЕРВЫМ, до любых get/post
        Spark.staticFileLocation("/public");

        logger.info("Dashboard server started successfully on port {}", port);
        System.out.println("Dashboard available at http://localhost:" + port);

        // --- API Endpoints ---

        // GET /api/status - Получить текущий статус
        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });

        // POST /api/collect-logs - Собрать диагностические логи
        Spark.post("/api/collect-logs", (req, res) -> {
            res.type("application/json");
            try {
                AgentController ac = jadeContainer.getAgent("coordinator");
                if (ac == null) {
                    logger.error("Coordinator agent not found for log collection!");
                    res.status(500);
                    return new Gson().toJson(Collections.singletonMap("error", "Coordinator agent is not running"));
                }
                logger.info("Sending 'COLLECT_DIAGNOSTIC_LOGS' command to coordinator agent");
                // Отправляем команду через O2A
                ac.putO2AObject("COMMAND: COLLECT_DIAGNOSTIC_LOGS", false); // false = неблокирующий
                logger.info("'COLLECT_DIAGNOSTIC_LOGS' command sent");
                return new Gson().toJson(Map.of(
                    "status", "started",
                    "message", "Diagnostic logs collection initiated"
                ));
            } catch (Exception e) {
                logger.error("Failed to send collect logs command", e);
                res.status(500);
                return new Gson().toJson(Collections.singletonMap("error", "Failed to initiate log collection: " + e.getMessage()));
            }
        });

        // GET /logs - Получить содержимое диагностических логов
        Spark.get("/logs", (req, res) -> {
            String logFilePath = (String) STATUS.get("diagnosticLogs");
            if (logFilePath != null && !logFilePath.isEmpty()) {
                Path logFile = Paths.get(logFilePath);
                if (Files.exists(logFile)) {
                    res.type("text/plain; charset=utf-8");
                    return Files.readString(logFile, StandardCharsets.UTF_8);
                }
            }
            res.status(404);
            res.type("text/plain");
            return "Logs not available. Click 'Collect logs' first.";
        });

        // --- Тестовые Endpoints (необязательные) ---
        Spark.get("/api/test", (req, res) -> {
            res.type("application/json");
            try {
                AgentController ac = jadeContainer.getAgent("coordinator");
                if (ac == null) {
                    return new Gson().toJson(Collections.singletonMap("error", "Coordinator agent not found"));
                }
                return new Gson().toJson(Map.of("status", "found", "agent", ac.getName()));
            } catch (Exception e) {
                logger.warn("Test endpoint error", e);
                return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
            }
        });

        Spark.get("/api/test-o2a", (req, res) -> {
            res.type("application/json");
            try {
                AgentController ac = jadeContainer.getAgent("coordinator");
                if (ac == null) {
                    logger.error("Coordinator agent not found for test O2A!");
                    res.status(500);
                    return new Gson().toJson(Collections.singletonMap("error", "Coordinator agent not found"));
                }
                logger.info("Sending TEST_COMMAND O2A to coordinator agent");
                ac.putO2AObject("TEST_COMMAND", false); // false = неблокирующий
                logger.info("TEST_COMMAND O2A sent");
                return new Gson().toJson(Collections.singletonMap("status", "sent"));
            } catch (Exception e) {
                logger.error("Test O2A command failed", e);
                res.status(500);
                return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
            }
        });
        // --- Конец Тестовых Endpoints ---
    }

    /**
     * Обновляет статус в общей карте и фиксирует время последнего обновления.
     * Вызывается агентами для обновления информации на веб-панели.
     *
     * @param key   Ключ статуса.
     * @param value Значение статуса.
     */
    public static synchronized void updateStatus(String key, Object value) {
        STATUS.put(key, value);
        STATUS.put("lastUpdate", System.currentTimeMillis());
        logger.debug("Status updated: {} = {}", key, value);
    }
}
