package org.example.mas;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import jade.wrapper.AgentController;
import org.example.mas.Agent.MasterAgent;
import org.example.mas.utils.AnsibleRunner;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);
    private String inventory;
    private String varsPath;
    private String playbooksDir;
    private final Map<String, AID> nodeAgents = new ConcurrentHashMap<>();

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent initialized (waiting for deploy command)");


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                logger.info("CoordinatorAgent: Checking for O2A commands...");
                Object cmd = getO2AObject();
                if (cmd != null) {
                    logger.info("CoordinatorAgent: Received O2A object of type: {}", cmd.getClass().getSimpleName());
                    
                    if (cmd instanceof String) {
                        String command = (String) cmd;
                        logger.info("CoordinatorAgent: Received command: {}", command);
                        
                        if (command.startsWith("DEPLOY:")) {
                            String[] parts = command.substring(7).split(",", 3);
                            logger.info("DEPLOY command parts: {}", java.util.Arrays.toString(parts));
                            if (parts.length == 3) {
                                logger.info("Starting deployment with inventory: {}, vars: {}, playbooks: {}", 
                                    parts[0].trim(), parts[1].trim(), parts[2].trim());
                                startDeployment(parts[0].trim(), parts[1].trim(), parts[2].trim());
                            } else {
                                logger.error("Invalid DEPLOY command format. Expected 3 parts, got: {}", parts.length);
                                logger.error("Command was: {}", command);
                            }
                        } else if ("COMMAND: COLLECT_DIAGNOSTIC_LOGS".equals(command)) {
                            logger.info("Starting diagnostic logs collection");
                            collectDiagnosticLogs();
                        } else if ("TEST_COMMAND".equals(command)) {
                            logger.info("Received TEST_COMMAND - updating status");
                            DashboardServer.updateStatus("ansibleStage", "TEST_COMMAND_RECEIVED");
                            logger.info("Status updated to: TEST_COMMAND_RECEIVED");
                        } else {
                            logger.warn("Unknown command received: {}", command);
                        }
                    }
                } else {
                    logger.info("No O2A command received - waiting...");
                }
                block();
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleNodeMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void startDeployment(String inventoryPath, String varsPath, String playbooksDir) {
        logger.info("Starting deployment with inventory: {}, playbooks: {}", inventoryPath, playbooksDir);

        if (!validatePaths(inventoryPath, playbooksDir)) {
            sendAlert("Deployment failed: invalid paths");
            return;
        }

        this.inventory = inventoryPath;
        this.varsPath = varsPath;
        this.playbooksDir = playbooksDir;

        new Thread(() -> {
            try {
                logger.info("Starting deployment thread...");
                DashboardServer.updateStatus("ansibleStage", "Starting deployment...");
                
                // Копируем vars.yml в директорию playbooks
                logger.info("Copying vars.yml from {} to {}", varsPath, playbooksDir);
                try {
                    Path sourceVars = Paths.get(varsPath);
                    Path targetVars = Paths.get(playbooksDir, "vars.yml");
                    Files.copy(sourceVars, targetVars, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("vars.yml copied successfully");
                } catch (Exception e) {
                    logger.error("Failed to copy vars.yml", e);
                    sendAlert("Failed to copy vars.yml: " + e.getMessage());
                    return;
                }
                
                String[] playbooks = {
                    "01_system_preparation.yml",
                    "02_containerd.yml",
                    "03_kubernetes_install.yml",
                    "04_kubernetes_init.yml",
                    "05_calico_cni.yml",
                    "06_worker_preparation.yml",
                    "07_worker_join.yml",
                    "08_htcondor.yml"
                };

                for (String playbook : playbooks) {
                    logger.info("Running playbook: {}", playbook);
                    DashboardServer.updateStatus("ansibleStage", "Running: " + playbook);
                    
                    AnsibleRunner.AnsibleResult result = AnsibleRunner.run(playbook, inventoryPath, playbooksDir, 15);
                    if (!result.success) {
                        logger.error("Playbook {} failed: {} - {}", playbook, result.errorCode, result.details);
                        sendAlert("Deployment failed at: " + playbook + " - " + result.errorCode);
                        DashboardServer.updateStatus("ansibleStage", "ERROR: " + playbook);
                        return;
                    }
                    logger.info("Playbook {} completed successfully", playbook);
                }

                logger.info("Deployment completed successfully");
                DashboardServer.updateStatus("ansibleStage", "ALL_STAGES_COMPLETED ✅");

                createNodeAgents();

            } catch (Exception e) {
                logger.error("Deployment error", e);
                sendAlert("Deployment crashed: " + e.getMessage());
                DashboardServer.updateStatus("ansibleStage","ERROR");
            }
        }).start();
    }

    private boolean validatePaths(String inventoryPath, String playbooksDir) {
        logger.info("Validating paths - Inventory: {}, Playbooks: {}", inventoryPath, playbooksDir);
        
        if (!Files.exists(Paths.get(inventoryPath))) {
            logger.error("Inventory file not found: {}", inventoryPath);
            return false;
        }
        if (!Files.exists(Paths.get(playbooksDir))) {
            logger.error("Playbooks directory not found: {}", playbooksDir);
            return false;
        }
        
        logger.info("All paths validated successfully");
        return true;
    }

    private void createNodeAgents() {
        try {
            InventoryParser.Inventory inv = InventoryParser.parse(this.inventory);

            for (InventoryParser.Host master : inv.getGroup("central_manager")) {
                createNodeAgent(master.name, true);
            }

        } catch (Exception e) {
            logger.error("Failed to create node agents", e);
        }
    }

    private void createNodeAgent(String nodeName, boolean isMaster) {
        try {
            String agentType = MasterAgent.class.getName();
            Object[] agentArgs = new Object[]{nodeName, this.inventory, this.playbooksDir};

            AgentController ac = getContainerController().createNewAgent(nodeName, agentType, agentArgs);
            ac.start();

            AID agentAID = new AID(nodeName, AID.ISLOCALNAME);
            nodeAgents.put(nodeName, agentAID);

            logger.info("Created {} agent: {}", isMaster ? "master" : "worker", nodeName);
        } catch (Exception e) {
            logger.error("Failed to create agent for node: " + nodeName, e);
        }
    }

    private void handleNodeMessage(ACLMessage msg) {
        String sender = msg.getSender().getLocalName();
        String content = msg.getContent();

        if (content != null && content.startsWith("ALERT:")) {
            logger.warn("Received alert from {}: {}", sender, content);
            DashboardServer.updateStatus("alerts", new String[]{content});
        } else if ("READY".equals(content)) {
            logger.info("Node {} is ready", sender);
        }
    }

    private void sendAlert(String message) {
        logger.warn("ALERT: {}", message);
        DashboardServer.updateStatus("alerts", new String[]{message});
    }

    private void collectDiagnosticLogs() {
        logger.info("Starting diagnostic logs collection...");
        try {
            Path logPath = Paths.get(System.getProperty("user.dir"), "diagnostic-logs.txt");
            logger.info("Saving diagnostic logs to: {}", logPath.toAbsolutePath());

            // Создаем тестовые логи вместо реальных команд
            String systemInfo = "System: " + System.getProperty("os.name") + " " + System.getProperty("os.version");
            String javaInfo = "Java: " + System.getProperty("java.version");
            String timestamp = "Timestamp: " + java.time.Instant.now();
            
            String fullLog = "=== SYSTEM INFO ===\n" + systemInfo +
                    "\n\n=== JAVA INFO ===\n" + javaInfo +
                    "\n\n=== TIMESTAMP ===\n" + timestamp +
                    "\n\n=== MOCK LOGS ===\n" +
                    "This is a mock diagnostic log collection.\n" +
                    "In a real environment, this would contain:\n" +
                    "- Kubelet logs\n" +
                    "- Containerd status\n" +
                    "- Kubernetes pod status\n" +
                    "- System logs\n";

            Files.write(logPath, fullLog.getBytes());
            logger.info("Diagnostic logs saved successfully to: {}", logPath.toAbsolutePath());
            DashboardServer.updateStatus("diagnosticLogs", logPath.toAbsolutePath().toString());

        } catch (Exception e) {
            logger.error("Failed to collect diagnostic logs", e);
            DashboardServer.updateStatus("diagnosticLogs", "");
        }
    }

    private String executeCommand(String... args) {
        try {
            Process p = new ProcessBuilder(args).start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
        } catch (Exception e) {
            logger.warn("Command failed: {}", String.join(" ", args), e);
            return null;
        }
    }
}
