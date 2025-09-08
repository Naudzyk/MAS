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
}
