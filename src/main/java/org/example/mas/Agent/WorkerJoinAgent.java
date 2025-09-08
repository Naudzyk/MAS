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
}
