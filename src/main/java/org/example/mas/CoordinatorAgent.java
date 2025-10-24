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
    private String playbooksDir;
    private final Map<String, AID> nodeAgents = new ConcurrentHashMap<>();

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent initialized (waiting for deploy command)");


        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                Object cmd = getO2AObject();
                if (cmd instanceof String) {
                    String command = (String) cmd;
                    if (command.startsWith("DEPLOY:")) {
                        String[] parts = command.substring(7).split(",", 2);
                        if (parts.length == 2) {
                            startDeployment(parts[0].trim(), parts[1].trim());
                        } else {
                            logger.error("Invalid DEPLOY command format: {}", command);
                        }
                    } else if ("COMMAND: COLLECT_DIAGNOSTIC_LOGS".equals(command)) {
                        collectDiagnosticLogs();
                    }
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

    private void startDeployment(String inventoryPath, String playbooksDir) {
        logger.info("Starting deployment with inventory: {}, playbooks: {}", inventoryPath, playbooksDir);

        if (!validatePaths(inventoryPath, playbooksDir)) {
            sendAlert("Deployment failed: invalid paths");
            return;
        }

        this.inventory = inventoryPath;
        this.playbooksDir = playbooksDir;

        new Thread(() -> {
            try {
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
                    if (!AnsibleRunner.run(playbook, inventoryPath, playbooksDir, 15).success) {
                        sendAlert("Deployment failed at: " + playbook);
                        DashboardServer.updateStatus("ansibleStage",playbook);
                        return;
                    }
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
        if (!Files.exists(Paths.get(inventoryPath))) {
            logger.error("Inventory file not found: {}", inventoryPath);
            return false;
        }
        if (!Files.exists(Paths.get(playbooksDir))) {
            logger.error("Playbooks directory not found: {}", playbooksDir);
            return false;
        }
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
        try {
            Path logPath = Paths.get(System.getProperty("user.dir"), "diagnostic-logs.txt");
            logger.info("Saving diagnostic logs to: {}", logPath.toAbsolutePath());

            String kubeletLog = executeCommand("journalctl", "-u", "kubelet", "--since=-1h", "-n", "50");
            String containerdLog = executeCommand("systemctl", "status", "containerd");
            String kubectlLog = executeCommand("kubectl", "get", "pods", "-A");

            String fullLog = "=== KUBELET ===\n" + (kubeletLog != null ? kubeletLog : "N/A") +
                    "\n\n=== CONTAINERD ===\n" + (containerdLog != null ? containerdLog : "N/A") +
                    "\n\n=== KUBECTL ===\n" + (kubectlLog != null ? kubectlLog : "N/A");

            Files.write(logPath, fullLog.getBytes());
            logger.info("Diagnostic logs saved successfully");
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
