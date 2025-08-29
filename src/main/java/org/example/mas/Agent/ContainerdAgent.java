package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для установки и настройки containerd (02_containerd.yml)
 * - Установка containerd
 * - Настройка конфигурации
 * - Запуск сервиса
 */
public class ContainerdAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(ContainerdAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("ContainerdAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("ContainerdAgent {} received: {}", getLocalName(), content);

        if (content.contains("SYSTEM_READY_FOR_CONTAINERD") || content.contains("START_CONTAINERD")) {
            logger.info("Starting containerd installation and configuration");
        } else if (content.contains("CONTAINERD_COMPLETE")) {
            logger.info("Containerd setup completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("kubernetes-install-agent", "CONTAINERD_READY_FOR_K8S");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("Containerd installation and configuration completed successfully");
            // Уведомляем следующий агент
            sendMessage("kubernetes-install-agent", "CONTAINERD_COMPLETE");
        } else {
            logger.error("Containerd installation failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "CONTAINERD_FAILED: " + details);
        }
    }
}
