package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для выполнения подготовки системы (01_system_preparation.yml)
 * - Установка необходимых пакетов
 * - Настройка ядра и модулей
 * - Конфигурация sysctl
 */
public class SystemPreparationAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(SystemPreparationAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("SystemPreparationAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("SystemPreparationAgent {} received: {}", getLocalName(), content);

        // Обработка специфичных сообщений для подготовки системы
        if (content.contains("START_SYSTEM_PREP")) {
            logger.info("Starting system preparation phase");
        } else if (content.contains("SYSTEM_PREP_COMPLETE")) {
            logger.info("System preparation completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("containerd-agent", "SYSTEM_READY_FOR_CONTAINERD");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("System preparation completed successfully");
            // Уведомляем следующий агент
            sendMessage("containerd-agent", "SYSTEM_PREP_COMPLETE");
        } else {
            logger.error("System preparation failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "SYSTEM_PREP_FAILED: " + details);
        }
    }
}
