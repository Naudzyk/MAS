package org.example.mas.Agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import org.example.mas.DashboardServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;


public class MasterAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(MasterAgent.class);
    private String nodeName;
    private String inventoryPath;
    private String playbooksDir;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 3) {
            logger.error("MasterAgent requires: nodeName, inventoryPath, playbooksDir");
            doDelete();
            return;
        }

        this.nodeName = (String) args[0];
        this.inventoryPath = (String) args[1];
        this.playbooksDir = (String) args[2];

        logger.info("MasterAgent {} initialized for node: {}", getLocalName(), nodeName);

        addBehaviour(new CyclicBehaviour() {
            private long lastCheck = 0;
            private static final long CHECK_INTERVAL_MS = 10_000;

            @Override
            public void action() {
                long now = System.currentTimeMillis();
                if (now - lastCheck >= CHECK_INTERVAL_MS) {
                    checkServices();
                    lastCheck = now;
                }
                block(1000);
            }
        });
    }

    protected void checkServices() {
        try {
            boolean allHealthy = true;

            if (!isPodReady("app=htcondor-cm,role=manager")) {
                sendAlert("HTCondor Central Manager is not ready");
                allHealthy = false;
            }

            if (!areAllExecuteNodesReady()) {
                allHealthy = false;
            }

            if (!isCondorClusterActive()) {
                sendAlert("HTCondor reports no active workers");
                allHealthy = false;
            }

            if (allHealthy) {
                logger.info("HTCondor cluster is fully healthy");
                DashboardServer.updateStatus("htcondorStatus", "HEALTHY");
            }

        } catch (Exception e) {
            logger.error("Error in MasterAgent health check", e);
            sendAlert("MasterAgent monitoring failed: " + e.getMessage());
        }
    }

    private boolean isPodReady(String labelSelector) {
        try {
            Process p = new ProcessBuilder("kubectl", "get", "pod", "-l", labelSelector,
                    "-o", "jsonpath={.items[0].status.containerStatuses[0].ready}").start();
            String ready = readOutput(p).trim();
            return "true".equals(ready);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean areAllExecuteNodesReady() {
        try {
            Process p1 = new ProcessBuilder("kubectl", "get", "pod", "-l", "app=htcondor-ex,role=execute",
                    "-o", "jsonpath={.items[*].metadata.name}").start();
            String names = readOutput(p1).trim();
            if (names.isEmpty()) {
                sendAlert("No HTCondor Execute Nodes found");
                return false;
            }

            String[] podNames = names.split(" ");
            for (String podName : podNames) {
                Process p2 = new ProcessBuilder("kubectl", "get", "pod", podName,
                        "-o", "jsonpath={.status.containerStatuses[0].ready}").start();
                String ready = readOutput(p2).trim();
                if (!"true".equals(ready)) {
                    Process p3 = new ProcessBuilder("kubectl", "logs", podName, "--tail=30").start();
                    String logs = readOutput(p3);
                    sendAlert("Execute Node " + podName + " is not ready. Logs:\n" + logs);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to check Execute Nodes", e);
            return false;
        }
    }

    private boolean isCondorClusterActive() {
        try {
            Process p1 = new ProcessBuilder("kubectl", "get", "pod", "-l", "app=htcondor-cm,role=manager",
                    "-o", "name").start();
            String podRef = readOutput(p1).trim();
            if (podRef.isEmpty()) return false;
            String podName = podRef.split("/")[1];
            Process p2 = new ProcessBuilder("kubectl", "exec", podName, "--", "condor_status", "-compact").start();
            String output = readOutput(p2);
            return output != null && output.contains("Total Owner");
        } catch (Exception e) {
            logger.warn("Failed to run condor_status", e);
            return false;
        }
    }

    private String readOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private void sendAlert(String message) {
        logger.warn("Sent alert: {}", message);
        DashboardServer.updateStatus("alerts", new String[]{message});
    }
}