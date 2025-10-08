package org.example.mas.Agent;


import jade.core.AID;
import jade.lang.acl.ACLMessage;
import org.example.mas.NodeAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class MasterAgent extends NodeAgent {
    private static final Logger logger = LoggerFactory.getLogger(MasterAgent.class);

    @Override
    protected void checkServices() {
        try {
            boolean allHealthy = true;

            // 1. Проверяем Central Manager
            if (!isPodReady("app=htcondor-cm,role=manager")) {
                sendAlert("HTCondor Central Manager is not ready");
                allHealthy = false;
            }

            // 2. Проверяем все Execute Nodes
            if (!areAllExecuteNodesReady()) {
                allHealthy = false;
            }

            // 3. Проверяем, видит ли CM worker’ов (через condor_status)
            if (!isCondorClusterActive()) {
                sendAlert("HTCondor reports no active workers");
                allHealthy = false;
            }

            if (allHealthy) {
                logger.info("HTCondor cluster is fully healthy");
                sendToCoordinator("STATUS: HTCONDOR_CLUSTER_HEALTHY");
            }

        } catch (Exception e) {
            logger.error("Error in MasterAgent health check", e);
            sendAlert("MasterAgent monitoring failed: " + e.getMessage());
        }
    }

    // Проверка одного пода по метке
    private static boolean isPodReady(String labelSelector) {
        try {
            Process p = new ProcessBuilder("kubectl", "get", "pod", "-l", labelSelector,
                    "-o", "jsonpath={.items[0].status.containerStatuses[0].ready}").start();
            String ready = readOutput(p).trim();
            return "true".equals(ready);
        } catch (Exception e) {
            return false;
        }
    }

    // Проверка всех Execute Nodes
    private  boolean areAllExecuteNodesReady() {
        try {
            // Получаем имена всех EX-подов
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
                    // Под не готов — читаем логи
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

    // Проверка через condor_status (выполняем в поде CM)
    private static boolean isCondorClusterActive() {


        try {
            // Получаем имя пода CM
            Process p1 = new ProcessBuilder("kubectl", "get", "pod", "-l", "app=htcondor-cm,role=manager",
                    "-o", "name").start();
            String podRef = readOutput(p1).trim(); // например: "pod/htcondor-manager-abc12"
            if (podRef.isEmpty()) return false;
            String podName = podRef.split("/")[1];
            Process p2 = new ProcessBuilder("kubectl", "exec", podName, "--", "condor_status", "-compact").start();
            String output = readOutput(p2);
            // condor_status возвращает строки вида: "Total Owner ..."
            return output != null && output.contains("Total Owner");
        } catch (Exception e) {
            logger.warn("Failed to run condor_status", e);
            return false;
        }
    }

    private static String readOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private void sendAlert(String message) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("coordinator", AID.ISLOCALNAME));
        msg.setContent("ALERT: " + message);
        send(msg);
        logger.warn("Sent alert: {}", message);
    }
}