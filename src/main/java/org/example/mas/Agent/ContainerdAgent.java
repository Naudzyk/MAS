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
}