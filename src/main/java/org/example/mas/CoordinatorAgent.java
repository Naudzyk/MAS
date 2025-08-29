package org.example.mas;


import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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

    private Map<String, String> agentStatus = new HashMap<>();
    private boolean deploymentStarted = false;

    @Override
    protected void setup() {
        logger.info("CoordinatorAgent {} initialized", getLocalName());

        // Добавляем поведение для обработки сообщений
        addBehaviour(new MessageHandlerBehaviour());

        // Инициируем начало развертывания через 5 секунд
        addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 5000) {
            @Override
            protected void onWake() {
                if (!deploymentStarted) {
                    startDeployment();
                }
            }
        });
    }

    /**
     * Обработчик сообщений от всех агентов
     */
    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = receive(template);

            if (msg != null) {
                String content = msg.getContent();
                String sender = msg.getSender().getLocalName();

                logger.info("Coordinator received from {}: {}", sender, content);
                processMessage(sender, content);
            } else {
                block();
            }
        }
    }

    /**
     * Обрабатывает сообщения от агентов
     */
    private void processMessage(String sender, String content) {
        agentStatus.put(sender, content);

        if (content.contains("FAILED")) {
            logger.error("Deployment failed at {}: {}", sender, content);
            handleFailure(sender, content);
        } else if (content.contains("COMPLETE")) {
            logger.info("Stage completed successfully: {}", sender);
            handleSuccess(sender, content);
        } else if (content.contains("DEPLOYMENT_COMPLETE")) {
            logger.info("=== HTCONDOR CLUSTER DEPLOYMENT COMPLETED SUCCESSFULLY ===");
            handleDeploymentComplete();
        }
    }

    /**
     * Инициирует начало развертывания
     */
    private void startDeployment() {
        deploymentStarted = true;
        logger.info("=== STARTING HTCONDOR CLUSTER DEPLOYMENT ===");

        // Запускаем первый этап
        sendMessage("system-prep-agent", "START_SYSTEM_PREP");
    }

    /**
     * Обрабатывает успешное завершение этапа
     */
    private void handleSuccess(String agent, String content) {
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

    /**
     * Обрабатывает ошибки
     */
    private void handleFailure(String agent, String content) {
        logger.error("Deployment failed at stage: {}", agent);
        logger.error("Error details: {}", content);

        // Здесь можно добавить логику восстановления или остановки
        logger.error("=== DEPLOYMENT FAILED - MANUAL INTERVENTION REQUIRED ===");
    }

    /**
     * Обрабатывает завершение всего развертывания
     */
    private void handleDeploymentComplete() {
        logger.info("=== DEPLOYMENT SUMMARY ===");
        agentStatus.forEach((agent, status) -> {
            logger.info("{}: {}", agent, status);
        });
        logger.info("=== HTCONDOR CLUSTER IS READY ===");

        // Можно добавить финальные проверки
        performFinalChecks();
    }

    /**
     * Выполняет финальные проверки кластера
     */
    private void performFinalChecks() {
        logger.info("Performing final cluster checks...");

        // Здесь можно добавить проверки:
        // - kubectl get nodes
        // - kubectl get pods -A
        // - condor_status (внутри manager pod)

        logger.info("Final checks completed. Cluster is operational.");
    }

    /**
     * Отправляет сообщение агенту
     */
    private void sendMessage(String agentName, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID(agentName, jade.core.AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
        logger.info("Coordinator sent to {}: {}", agentName, content);
    }
}