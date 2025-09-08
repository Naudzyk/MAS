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
}


