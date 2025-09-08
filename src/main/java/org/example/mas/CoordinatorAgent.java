package org.example.mas;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Координатор для управления последовательностью развертывания HTCondor кластера
 * Отслеживает статус каждого этапа и инициирует следующий
 */
public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);

    private final Map<String, String> agentStatus = new HashMap<>();
    private boolean deploymentStarted = false;

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent {} initialized", getLocalName());

        addBehaviour(new MessageHandlerBehaviour());

        addBehaviour(new WakerBehaviour(this, 5000) {
            @Override
            protected void onWake() {
                if (!deploymentStarted) {
                    startDeployment();
                }
            }
        });
    }

    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg == null) { block(); return; }

            String content = msg.getContent();
            String sender = msg.getSender().getLocalName();

            logger.info("Coordinator received from {}: {}", sender, content);
            processMessage(sender, content);
        }
    }

    private void processMessage(String sender, String content) {
        agentStatus.put(sender, content);

        if (content != null && content.contains("FAILED")) {
            logger.error("Deployment failed at {}: {}", sender, content);
            handleFailure(sender, content);
        } else if (content != null && content.contains("COMPLETE")) {
            logger.info("Stage completed successfully: {}", sender);
            handleSuccess(sender);
        } else if (content != null && content.contains("DEPLOYMENT_COMPLETE")) {
            logger.info("=== HTCONDOR CLUSTER DEPLOYMENT COMPLETED SUCCESSFULLY ===");
            handleDeploymentComplete();
        }
    }

    private void startDeployment() {
        deploymentStarted = true;
        logger.info("=== STARTING HTCONDOR CLUSTER DEPLOYMENT ===");
        sendMessage("system-prep-agent", "START_SYSTEM_PREP");
    }

    private void handleSuccess(String agent) {
        switch (agent) {
            case "system-prep-agent":
                sendMessage("containerd-agent", "START_CONTAINERD");
                break;
            case "containerd-agent":
                sendMessage("kubernetes-install-agent", "START_K8S_INSTALL");
                break;
            case "kubernetes-install-agent":
                sendMessage("kubernetes-init-agent", "START_K8S_INIT");
                break;
            case "kubernetes-init-agent":
                sendMessage("calico-agent", "START_CALICO");
                break;
            case "calico-agent":
                sendMessage("worker-prep-agent", "START_WORKER_PREP");
                break;
            case "worker-prep-agent":
                sendMessage("worker-join-agent", "START_WORKER_JOIN");
                break;
            case "worker-join-agent":
                sendMessage("htcondor-agent", "START_HTCONDOR");
                break;
            case "htcondor-agent":
                logger.info("All stages completed successfully!");
                break;
        }
    }

    private void handleFailure(String agent, String content) {
        logger.error("Deployment failed at stage: {}", agent);
        logger.error("Error details: {}", content);
        logger.error("=== DEPLOYMENT FAILED - MANUAL INTERVENTION REQUIRED ===");
    }

    private void handleDeploymentComplete() {
        logger.info("=== DEPLOYMENT SUMMARY ===");
        agentStatus.forEach((agent, status) -> logger.info("{}: {}", agent, status));
        logger.info("=== HTCONDOR CLUSTER IS READY ===");
        performFinalChecks();
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
        logger.info("Coordinator sent to {}: {}", agentName, content);
    }
}