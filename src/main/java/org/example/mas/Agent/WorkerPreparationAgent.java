package org.example.mas.Agent;


import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для подготовки worker узлов (06_worker_preparation.yml)
 * - Установка containerd на worker'ах
 * - Установка Kubernetes компонентов
 * - Подготовка к присоединению к кластеру
 */
public class WorkerPreparationAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(WorkerPreparationAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("WorkerPreparationAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("WorkerPreparationAgent {} received: {}", getLocalName(), content);

        if (content.contains("CNI_READY_FOR_WORKERS") || content.contains("START_WORKER_PREP")) {
            logger.info("Starting worker nodes preparation");
        } else if (content.contains("WORKER_PREP_COMPLETE")) {
            logger.info("Worker preparation completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("worker-join-agent", "WORKERS_READY_FOR_JOIN");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("Worker nodes preparation completed successfully");
            // Уведомляем следующий агент
            sendMessage("worker-join-agent", "WORKER_PREP_COMPLETE");
        } else {
            logger.error("Worker preparation failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "WORKER_PREP_FAILED: " + details);
        }
    }
}


