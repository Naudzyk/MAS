package org.example.mas.Service.Agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import lombok.RequiredArgsConstructor;
import org.example.mas.SpringContextHelper;
import org.example.mas.utils.AnsibleRunner;
import org.example.mas.utils.InventoryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoordinatorAgent extends BaseAgent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);
    private String inventory;
    private String playbooksDir;
    private final Map<String, AID> nodeAgents = new HashMap<>();


    @Override
    protected void setup() {
        StatusService statusSvc = SpringContextHelper.getBean(StatusService.class);
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            logger.error("CoordinatorAgent requires: inventoryPath, playbooksDir");
            doDelete();
            return;
        }

        this.inventory = (String) args[0];
        this.playbooksDir = (String) args[1];
        logger.info("CoordinatorAgent initialized with inventory: {}, playbooksDir: {}", inventory, playbooksDir);

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                logger.info("Starting initial cluster deployment...");

                String[] playbooks = {
                        "01_system_preparation.yml",
                        "02_containerd.yml",
                        "03_kubernetes_install.yml",
                        "04_kubernetes_init.yml",
                        "05_calico_cni.yml",
                        "06_worker_preparation.yml",
                        "07_worker_join.yml",
                        "08_htcondor.yml",
                        "09_prometheus.yml",
                        "10_prometheus_server.yml"
                };

                for (String playbook : playbooks) {
                    logger.info("Running playbook: {}", playbook);
                    sendStatusUpdate("ansibleStage", playbook);

                    AnsibleRunner.AnsibleResult result = AnsibleRunner.run(playbook, inventory, playbooksDir, 15);

                    if (!result.success) {
                        handlePlaybookFailure(playbook, result);
                        statusSvc.update("ansibleStage", "Ошибка");
                        statusSvc.update("clusterStatus","Ошибка");
                        return;
                    }
                }

                logger.info("Initial cluster deployment completed.");
                statusSvc.update("ansibleStage", "Успешное разворачивание кластера");
                statusSvc.update("clusterStatus","DEPLOY CLUSTER");
                createNodeAgents();
            }
        });




    }
    private void handlePlaybookFailure(String playbook, AnsibleRunner.AnsibleResult result) {
        String alert = "Playbook " + playbook + " failed: " + result.errorCode;
        logger.error(alert + " | Details: " + result.details);

        switch (result.errorCode) {
            case "TIMEOUT":
                sendAlert("ALERT: TIMEOUT in " + playbook + ". Check network or increase timeout.");
                break;
            case "CONNECTION_FAILURE":
                sendAlert("ALERT: UNREACHABLE NODE during " + playbook + ". Verify inventory and SSH keys.");
                break;
            default:
                sendAlert("ALERT: Execution failed in " + playbook + ": " + result.errorCode);
        }

        collectDiagnosticLogs();
    }

    private void createNodeAgents() {
        logger.info("Creating node agents...");
        List<String> nodeList = new ArrayList<>();
        try {
            InventoryParser.Inventory inv = InventoryParser.parse(this.inventory);

            for (InventoryParser.Host master : inv.getGroup("central_manager")) {
                nodeList.add(master.name);
                createNodeAgent(master.name, true);
            }

            for (InventoryParser.Host worker : inv.getGroup("execute_nodes")) {
                nodeList.add(worker.name);
                createNodeAgent(worker.name, false);
            }

            ObjectMapper mapper = new ObjectMapper();
            String nodeListJson = mapper.writeValueAsString(nodeList);
            sendStatusUpdate("activeNodes", nodeListJson);


        } catch (Exception e) {
            logger.error("Failed to create node agents from inventory", e);
        }
    }

    private void createNodeAgent(String nodeName, boolean isMaster) {
        try {
            String agentType;
            if(isMaster) {
                agentType = MasterAgent.class.getName();
            }else {
                agentType = WorkerAgent.class.getName();
            }

            Object[] agentArgs = new Object[]{
                    nodeName,
                    this.inventory,
                    this.playbooksDir
            };

            AgentController ac = getContainerController().createNewAgent(
                    nodeName,
                    agentType,
                    agentArgs
            );
            ac.start();

            AID agentAID = new AID(nodeName, AID.ISLOCALNAME);
            nodeAgents.put(nodeName, agentAID);

            logger.info("Created {} agent: {}", isMaster ? "master" : "worker", nodeName);
        } catch (Exception e) {
            logger.error("Failed to create agent for node: " + nodeName, e);
        }
    }

    private void sendAlert(String message) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("coordinator", AID.ISLOCALNAME));
        msg.setContent(message);
        send(msg);
    }
    private void collectDiagnosticLogs() {
    try {
        Path logPath = Paths.get(System.getProperty("user.dir"), "diagnostic-logs.txt");
        logger.info("Saving diagnostic logs to: {}", logPath.toAbsolutePath());

        String kubeletLog = executeCommand("journalctl", "-u", "kubelet", "--since=-1h", "-n", "50");
        String containerdLog = executeCommand("systemctl", "status", "containerd");

        String fullLog = "=== KUBELET ===\n" + (kubeletLog != null ? kubeletLog : "N/A") +
                        "\n\n=== CONTAINERD ===\n" + (containerdLog != null ? containerdLog : "N/A");

        Files.write(logPath, fullLog.getBytes(StandardCharsets.UTF_8));
        logger.info("Diagnostic logs saved successfully");

        sendStatusUpdate("diagnosticLogs", logPath.toAbsolutePath().toString());
        logger.info("Diagnostic logs saved to: {}", logPath);

    } catch (Exception e) {
        logger.error("Failed to collect diagnostic logs", e);
        sendStatusUpdate("diagnosticLogs", "");
    }
}

    private String executeCommand(String... args) {
        try {
            Process p = new ProcessBuilder(args).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            logger.warn("Command failed: {}", String.join(" ", args), e);
            return null;
        }
    }

}