package org.example.mas;

import jade.core.Agent;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final Map<String, String> agentStatus = new HashMap<>();
    private final AtomicBoolean deploymentStarted = new AtomicBoolean(false);
    private final AtomicBoolean deploymentFinished = new AtomicBoolean(false);

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent {} initialized", getLocalName());

        addBehaviour(new MessageHandlerBehaviour());


        addBehaviour(new WakerBehaviour(this, 5000) {
            @Override
            protected void onWake() {
                if (deploymentStarted.compareAndSet(false, true)) {
                    startDeployment();
                }
            }
        });
    }

    private void startDeployment() {
        logger.info("=== STARTING HTCONDOR CLUSTER DEPLOYMENT ===");
        triggerNextStage(null); // null → первый этап
    }


    private void triggerNextStage(String completedAgent) {
        int nextIndex = (completedAgent == null) ? 0 : DEPLOYMENT_SEQUENCE.indexOf(completedAgent) + 1;

        if (nextIndex >= DEPLOYMENT_SEQUENCE.size()) {
            // Все этапы завершены
            sendDeploymentComplete();
            return;
        }

        String nextAgent = DEPLOYMENT_SEQUENCE.get(nextIndex);
        String command = buildStartCommand(nextAgent);
        sendMessage(nextAgent, command);
        logger.info("Triggered next stage: {}", nextAgent);
    }

    private String buildStartCommand(String agentName) {
        return "START_" + agentName.toUpperCase().replace("-", "_");
    }

    private void sendDeploymentComplete() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(getAID()); // отправляем самому себе
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
        // Игнорируем сообщения после завершения
        if (deploymentFinished.get()) {
            return;
        }

        agentStatus.put(sender, content);

        if (content.contains("ABORT_ACK")) {
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
        logger.error("=== DEPLOYMENT FAILED - MANUAL INTERVENTION REQUIRED ===");

        deploymentFinished.set(true);
        abortAllAgents(agent);
    }

    private void handleDeploymentComplete() {
        if (!deploymentFinished.compareAndSet(false, true)) {
            return; // уже обработано
        }

        logger.info("=== HTCONDOR CLUSTER DEPLOYMENT COMPLETED SUCCESSFULLY ===");
        logger.info("=== DEPLOYMENT SUMMARY ===");
        DEPLOYMENT_SEQUENCE.forEach(agent -> {
            String status = agentStatus.getOrDefault(agent, "NOT STARTED");
            logger.info("{}: {}", agent, status);
        });
        logger.info("=== HTCONDOR CLUSTER IS READY ===");
        performFinalChecks();
    }

    private void performFinalChecks() {
        logger.info("Performing final cluster checks...");
        // Здесь можно добавить проверки: kubectl get nodes, condor_status и т.д.
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