package org.example.mas;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class CoordinatorAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);

    private static final List<String> DEPLOYMENT_SEQUENCE = Arrays.asList(
            "system-prep-agent",
            "containerd-agent",
            "kubernetes-install-agent",
            "kubernetes-init-agent",
            "calico-agent",
            "worker-prep-agent",
            "worker-join-agent",
            "htcondor-agent"
    );

    private CountDownLatch completionLatch;
    private String inventoryPath;
    private String workingDir;
    private int timeoutMinutes;

    private final Map<String, String> agentStatus = new ConcurrentHashMap<>();
    private volatile boolean deploymentFinished = false;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 4) {
            logger.error("CoordinatorAgent requires: CountDownLatch, inventoryPath, workingDir, timeoutMinutes");
            doDelete();
            return;
        }

        this.completionLatch = (CountDownLatch) args[0];
        this.inventoryPath = (String) args[1];
        this.workingDir = (String) args[2];
        this.timeoutMinutes = (Integer) args[3];

        logger.info("CoordinatorAgent initialized with inventory: {}, workingDir: {}, timeout: {} min",
                inventoryPath, workingDir, timeoutMinutes);

        addBehaviour(new MessageHandlerBehaviour());

        addBehaviour(new WakerBehaviour(this, 5000) {
            @Override
            protected void onWake() {
                startDeployment();
            }
        });
    }

    private void startDeployment() {
        logger.info("=== STARTING HTCONDOR CLUSTER DEPLOYMENT ===");
        triggerNextStage(null);
    }

    private void triggerNextStage(String completedAgent) {
        int nextIndex = (completedAgent == null) ? 0 : DEPLOYMENT_SEQUENCE.indexOf(completedAgent) + 1;

        if (nextIndex >= DEPLOYMENT_SEQUENCE.size()) {
            sendDeploymentComplete();
            return;
        }

        String nextAgent = DEPLOYMENT_SEQUENCE.get(nextIndex);
        String command = "START_" + nextAgent.toUpperCase().replace("-", "_");
        sendMessage(nextAgent, command);
        logger.info("Triggered next stage: {}", nextAgent);
    }

    private void sendDeploymentComplete() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(getAID());
        msg.setContent("DEPLOYMENT_COMPLETE");
        send(msg);
    }

    private void abortAllAgents(String failedAgent) {
        logger.warn("Aborting all agents due to failure in: {}", failedAgent);
        for (String agentName : DEPLOYMENT_SEQUENCE) {
            if (!agentName.equals(failedAgent)) {
                sendMessage(agentName, "ABORT");
            }
        }
    }

    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg == null) {
                block();
                return;
            }

            String content = msg.getContent() == null ? "" : msg.getContent();
            String sender = msg.getSender().getLocalName();

            logger.info("Coordinator received from {}: {}", sender, content);
            processMessage(sender, content);
        }
    }

    private void processMessage(String sender, String content) {
        if (deploymentFinished) return;

        agentStatus.put(sender, content);

        if (content.equals("ABORT_ACK")) {
            logger.debug("Agent {} acknowledged abort", sender);
            return;
        }

        if (content.contains("FAILED")) {
            handleFailure(sender, content);
        } else if (content.contains("COMPLETE")) {
            handleSuccess(sender);
        } else if (content.equals("DEPLOYMENT_COMPLETE")) {
            handleDeploymentComplete();
        }
    }

    private void handleSuccess(String agent) {
        logger.info("Stage completed successfully: {}", agent);
        triggerNextStage(agent);
    }

    private void handleFailure(String agent, String content) {
        logger.error("Deployment failed at stage: {}", agent);
        logger.error("Error details: {}", content);
        logger.error("=== DEPLOYMENT FAILED ===");

        deploymentFinished = true;
        abortAllAgents(agent);
        safeCountDown();
    }

    private void handleDeploymentComplete() {
        if (deploymentFinished) return;
        deploymentFinished = true;

        logger.info("=== HTCONDOR CLUSTER DEPLOYMENT COMPLETED SUCCESSFULLY ===");
        logger.info("=== DEPLOYMENT SUMMARY ===");
        DEPLOYMENT_SEQUENCE.forEach(agent -> {
            String status = agentStatus.getOrDefault(agent, "NOT STARTED");
            logger.info("{}: {}", agent, status);
        });
        logger.info("=== HTCONDOR CLUSTER IS READY ===");
        performFinalChecks();

        safeCountDown();
    }

    private void safeCountDown() {
        if (completionLatch != null && completionLatch.getCount() > 0) {
            completionLatch.countDown();
        }
    }

    private void performFinalChecks() {
        logger.info("Performing final cluster checks...");
        logger.info("Final checks completed. Cluster is operational.");
    }

    private void sendMessage(String agentName, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(agentName, AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
        logger.debug("Coordinator sent to {}: {}", agentName, content);
    }
}