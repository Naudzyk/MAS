package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для присоединения worker узлов к кластеру (07_worker_join.yml)
 * - kubeadm join на worker'ах
 * - Проверка статуса узлов
 * - Настройка сетевого взаимодействия
 */
public class WorkerJoinAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(WorkerJoinAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("WorkerJoinAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("WorkerJoinAgent {} received: {}", getLocalName(), content);

        if (content.contains("WORKERS_READY_FOR_JOIN") || content.contains("START_WORKER_JOIN")) {
            logger.info("Starting worker nodes join to cluster");
        } else if (content.contains("WORKER_JOIN_COMPLETE")) {
            logger.info("Worker join completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("htcondor-agent", "CLUSTER_READY_FOR_HTCONDOR");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("Worker nodes join to cluster completed successfully");
            // Уведомляем следующий агент
            sendMessage("htcondor-agent", "WORKER_JOIN_COMPLETE");
        } else {
            logger.error("Worker join failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "WORKER_JOIN_FAILED: " + details);
        }
    }
}
