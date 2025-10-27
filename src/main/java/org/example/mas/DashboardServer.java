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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardServer {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServer.class);
    private static AgentContainer jadeContainer;

    public static final Map<String, Object> STATUS = new ConcurrentHashMap<>();

    static {
        STATUS.put("status", "OK");
        STATUS.put("ansibleStage", "not_started");
        STATUS.put("clusterStatus", "NOT_DEPLOYED");
        STATUS.put("alerts", new String[0]);
        STATUS.put("diagnosticLogs", "");
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }

    /**
     * Запускает веб-сервер Dashboard.
     *
     * @param port      Номер порта для веб-сервера.
     * @param container Контейнер JADE для взаимодействия с агентами.
     */
    public static void start(int port, AgentContainer container) {
        jadeContainer = container;

        Spark.staticFileLocation("/public");
        Spark.port(port);

        logger.info("Configured static file location and port {}", port);


        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });


        Spark.post("/api/deploy", (req, res) -> {
            res.type("application/json");
            try {
                logger.info("Received deploy request");

                DeployRequest request = new Gson().fromJson(req.body(), DeployRequest.class);

                if (request == null) {
                    logger.warn("Received empty or invalid JSON for deploy request");
                    res.status(400);
                    return new Gson().toJson(Collections.singletonMap("error", "Invalid JSON payload"));
                }

                if (request.master == null || request.master.trim().isEmpty()) {
                    logger.warn("Master IP is missing or empty");
                    res.status(400);
                    return new Gson().toJson(Collections.singletonMap("error", "Master IP is required"));
                }

                if (request.workers == null || request.workers.isEmpty()) {
                    logger.warn("Workers IPs list is missing or empty");
                    res.status(400);
                    return new Gson().toJson(Collections.singletonMap("error", "At least one worker IP is required"));
                }

                if (request.nameHostEX == null) {
                    logger.warn("nameHostEX list is null");
                    res.status(400);
                    return new Gson().toJson(Collections.singletonMap("error", "nameHostEX list is required"));
                }

                if (request.nameHostEX.size() != request.workers.size()) {
                    logger.warn("Mismatch between number of workers ({}) and nameHostEX entries ({})", request.workers.size(), request.nameHostEX.size());
                    res.status(400);
                    return new Gson().toJson(Collections.singletonMap("error", "Number of worker IPs must match number of nameHostEX entries"));
                }

                if (request.nameHostCM == null || request.nameHostCM.trim().isEmpty()) {
                    request.nameHostCM = "vboxuser";
                    logger.info("nameHostCM not provided, using default: {}", request.nameHostCM);
                }


                Path projectBaseDir = Paths.get("").toAbsolutePath();
                logger.info("Project base directory: {}", projectBaseDir);
                Path scriptsDir = projectBaseDir.resolve("scripts");
                Path templateInventory = scriptsDir.resolve("inventory_template.ini");
                Path templateVars = scriptsDir.resolve("vars_template.yml");

                if (!Files.exists(templateInventory) || !Files.exists(templateVars)) {
                     logger.error("Template files not found in scripts/ directory: inventory_template.ini or vars_template.yml");
                     res.status(500);
                     return new Gson().toJson(Collections.singletonMap("error", "Required template files (inventory_template.ini, vars_template.yml) not found in 'scripts' directory."));
                }

                Path tempDir = Files.createTempDirectory("mas-deploy-");
                logger.info("Created temporary directory: {}", tempDir);
                Path filledInventoryPath = tempDir.resolve("inventory.ini");
                Path filledVarsPath = tempDir.resolve("vars.yml");

                String inventoryContent = Files.readString(templateInventory, StandardCharsets.UTF_8);
                inventoryContent = fillInventoryContent(inventoryContent, request);
                Files.writeString(filledInventoryPath, inventoryContent, StandardCharsets.UTF_8);
                logger.info("Filled inventory.ini written to: {}", filledInventoryPath);

                String varsContent = Files.readString(templateVars, StandardCharsets.UTF_8);
                varsContent = fillVarsContent(varsContent, request);
                Files.writeString(filledVarsPath, varsContent, StandardCharsets.UTF_8);
                logger.info("Filled vars.yml written to: {}", filledVarsPath);


                AgentController ac = jadeContainer.getAgent("coordinator");
                if (ac == null) {
                    logger.error("Coordinator agent not found!");
                    res.status(500);
                    return new Gson().toJson(Collections.singletonMap("error", "Coordinator agent is not running"));
                }

                String deployCommand = "DEPLOY:" + filledInventoryPath.toAbsolutePath() + "," + filledVarsPath.toAbsolutePath() + "," + scriptsDir.toAbsolutePath();
                logger.info("Sending deploy command to coordinator agent: {}", deployCommand);
                ac.putO2AObject(deployCommand, false);

                logger.info("Deployment initiated successfully");
                updateStatus("clusterStatus", "DEPLOYING");
                return new Gson().toJson(Map.of(
                    "status", "started",
                    "message", "Deployment process initiated",
                    "tempDir", tempDir.toString()
                ));

            } catch (JsonSyntaxException e) {
                logger.error("Failed to parse deploy request JSON", e);
                res.status(400);
                return new Gson().toJson(Collections.singletonMap("error", "Malformed JSON in request body: " + e.getMessage()));
            } catch (Exception e) {
                logger.error("Failed to initiate deployment", e);
                res.status(500);
                return new Gson().toJson(Collections.singletonMap("error", "Internal server error during deployment initiation: " + e.getMessage()));
            }
        });

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
                ac.putO2AObject("COMMAND: COLLECT_DIAGNOSTIC_LOGS", false);
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
                    return new Gson().toJson(Collections.singletonMap("error", "Coordinator agent not found"));
                }
                logger.info("Sending TEST_COMMAND O2A to coordinator");
                ac.putO2AObject("TEST_COMMAND", false);
                logger.info("TEST_COMMAND O2A sent");
                return new Gson().toJson(Collections.singletonMap("status", "sent"));
            } catch (Exception e) {
                logger.error("Test O2A command failed", e);
                return new Gson().toJson(Collections.singletonMap("error", e.getMessage()));
            }
        });

        logger.info("Dashboard server started successfully on port {}", port);
        System.out.println("Dashboard available at http://localhost:" + port);
    }

    /**
     * Обновляет статус в общей карте и фиксирует время последнего обновления.
     *
     * @param key   Ключ статуса.
     * @param value Значение статуса.
     */
    public static synchronized void updateStatus(String key, Object value) {
        STATUS.put(key, value);
        STATUS.put("lastUpdate", System.currentTimeMillis());
        logger.debug("Status updated: {} = {}", key, value);
    }

    public static class DeployRequest {
        private String master;
        private String nameHostCM;
        private List<String> workers;
        private List<String> nameHostEX;
        private String condorPassword;

        public String getMaster() { return master; }
        public void setMaster(String master) { this.master = master; }
        public String getNameHostCM() { return nameHostCM; }
        public void setNameHostCM(String nameHostCM) { this.nameHostCM = nameHostCM; }
        public List<String> getWorkers() { return workers; }
        public void setWorkers(List<String> workers) { this.workers = workers; }
        public List<String> getNameHostEX() { return nameHostEX; }
        public void setNameHostEX(List<String> nameHostEX) { this.nameHostEX = nameHostEX; }
        public String getCondorPassword() { return condorPassword; }
        public void setCondorPassword(String condorPassword) { this.condorPassword = condorPassword; }
    }


    /**
     * Заполняет содержимое inventory.ini данными из запроса.
     * Предполагается, что в шаблоне есть секции [central_manager] и [execute_nodes],
     * и они будут полностью заменены.
     */
    private static String fillInventoryContent(String templateContent, DeployRequest request) {
        StringBuilder sb = new StringBuilder();

        // Заполняем секцию [central_manager]
        sb.append("[central_manager]\n");
        String cmHostName = "central_manager";
        String cmUserName = request.nameHostCM != null && !request.nameHostCM.isEmpty() ? request.nameHostCM : "vboxuser";
        sb.append(cmHostName).append(" ansible_host=").append(request.master).append(" ansible_user=").append(cmUserName).append("\n\n");

        // Заполняем секцию [execute_nodes]
        sb.append("[execute_nodes]\n");
        if (request.workers != null && request.nameHostEX != null) {
            int minWorkers = Math.min(request.workers.size(), request.nameHostEX.size());
            for (int i = 0; i < minWorkers; i++) {
                String workerAlias = "worker" + (i + 1);
                String workerIp = request.workers.get(i);
                String workerUser = request.nameHostEX.get(i);
                sb.append(workerAlias)
                  .append(" ansible_host=").append(workerIp)
                  .append(" ansible_user=").append(workerUser)
                  .append("\n");
            }
        }
        // Если workers больше, чем nameHostEX, используем имя пользователя по умолчанию
        if (request.workers != null && request.nameHostEX != null && request.workers.size() > request.nameHostEX.size()) {
             for (int i = request.nameHostEX.size(); i < request.workers.size(); i++) {
                 String workerAlias = "worker" + (i + 1);
                 String workerIp = request.workers.get(i);
                 String workerUser = "kuber"; // Пользователь по умолчанию
                 sb.append(workerAlias)
                   .append(" ansible_host=").append(workerIp)
                   .append(" ansible_user=").append(workerUser)
                   .append("\n");
             }
        }

        return sb.toString();
    }


    /**
     * Заполняет содержимое vars.yml данными из запроса.
     * Предполагается, что в шаблоне vars.yml есть переменные в формате {{VAR_NAME}}.
     */
    private static String fillVarsContent(String templateContent, DeployRequest request) {
        String result = templateContent;

        // Заменяем известные переменные
        result = replacePlaceholder(result, "CENTRAL_MANAGER_IP", request.master);
        String password = request.condorPassword != null && !request.condorPassword.isEmpty() ? request.condorPassword : "qwerty";
        result = replacePlaceholder(result, "CONDOR_PASSWORD", password);

        // Можно добавить замену других переменных, если они есть в шаблоне
        // Например, если в vars_template.yml есть k8s_version: "{{K8S_VERSION}}"
        // result = replacePlaceholder(result, "K8S_VERSION", "1.30.7");

        return result;
    }

    /**
     * Вспомогательный метод для замены {{PLACEHOLDER}} на значение.
     * Использует Pattern.quote для экранирования специальных символов в ключе.
     */
    private static String replacePlaceholder(String content, String placeholderKey, String value) {
        if (value == null) value = ""; // Защита от NullPointerException
        // Создаем паттерн для поиска {{placeholderKey}} (с учетом регистра)
        Pattern pattern = Pattern.compile(Pattern.quote("{{" + placeholderKey + "}}"));
        Matcher matcher = pattern.matcher(content);
        return matcher.replaceAll(Matcher.quoteReplacement(value)); // quoteReplacement экранирует $ и \
    }

}
