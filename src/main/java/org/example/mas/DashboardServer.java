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
    private static CoordinatorAgent coordinatorAgent;
    public static final Map<String, Object> STATUS = new ConcurrentHashMap<>();

    static {
        STATUS.put("", "OK");
        STATUS.put("ansibleStage", "not_started");
        STATUS.put("htcondorStatus", "unknown");
        STATUS.put("alerts", new String[0]);
        STATUS.put("diagnosticLogs", "");
        STATUS.put("lastUpdate", System.currentTimeMillis());
    }

    public static String start(int port, AgentContainer container) {
        jadeContainer = container;
        Spark.port(port);

        Spark.staticFileLocation("/public");

        Spark.get("/api/status", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(STATUS);
        });

        Spark.post("/api/deploy",(req, res) -> {
                try {
                    Gson gson = new Gson();
                    DeployRequest request = gson.fromJson(req.body(), DeployRequest.class);

                    if(request.master == null || request.master == "" || request.workers == null || request.workers.isEmpty()){
                        res.status(400);
                        return gson.toJson(Map.of("error","MAster IP And ay least one worker IP are required"));
                    }

                    Path tempDir = Files.createTempDirectory("mas-deploy-");
                    Path inventoryPath = tempDir.resolve("inventory.ini");
                    generateInventoryFile(inventoryPath, request.master, request.workers, request.nameHostCM, request.nameHostEX);

                    String scriptsDir = Paths.get("scripts").toAbsolutePath().toString();
                    new Thread ((Runnable) () -> {
                        try {
                            String jarPath = DashboardServer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath, inventoryPath.toString(), scriptsDir);

                            pb.inheritIO();
                            Process process = pb.start();
                            process.waitFor();
                        }catch(Exception e){
                            logger.error("MAS deployment failed" ,e);
                        }
                    }).start();
                    String msg = "Deployment started with inventory: " + inventoryPath;
                    logger.info(msg);
                    return gson.toJson(Map.of(
                        "status", "started",
                        "message", "HTCondor cluster deployment initiated",
                        "inventory", inventoryPath.toString()
                    ));




                }catch (Exception e){
                    logger.error("Failed to start deployment", e);
                    res.status(500);
                    return new Gson().toJson(Map.of("error", e.getMessage()));
                }});

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
        return "";
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

    public static void generateInventoryFile(Path inventoryPath, String masterIps,  List<String> workerIps, String nameHostCM, List<String> nameHostEX) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("[central_manager]\n]");
        sb.append("central_manager ansible_host=").append(masterIps).append("ansible_user=").append(nameHostCM).append("\n\n");

        sb.append("[execute_nodes]\n");
        for(int i = 0; i < workerIps.size(); i++){
            sb.append("worker").append(i+1)
                    .append("ansible_host=").append(workerIps.get(i))
                    .append(nameHostEX.get(i)).append("\n");

        }
        Files.write(inventoryPath, sb.toString().getBytes(StandardCharsets.UTF_8));


    }
}
