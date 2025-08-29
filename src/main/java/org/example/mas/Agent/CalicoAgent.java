package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;


import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для установки и настройки Calico CNI (05_calico_cni.yml)
 * - Установка Calico
 * - Настройка сетевой политики
 * - Конфигурация VXLAN и NAT
 */
public class CalicoAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(CalicoAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("CalicoAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("CalicoAgent {} received: {}", getLocalName(), content);

        if (content.contains("K8S_READY_FOR_CNI") || content.contains("START_CALICO")) {
            logger.info("Starting Calico CNI installation and configuration");
        } else if (content.contains("CALICO_COMPLETE")) {
            logger.info("Calico CNI setup completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("worker-prep-agent", "CNI_READY_FOR_WORKERS");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("Calico CNI installation and configuration completed successfully");
            // Уведомляем следующий агент
            sendMessage("worker-prep-agent", "CALICO_COMPLETE");
        } else {
            logger.error("Calico CNI installation failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "CALICO_FAILED: " + details);
        }
    }
}
