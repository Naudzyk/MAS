package org.example.mas.Agent;


import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для инициализации Kubernetes кластера (04_kubernetes_init.yml)
 * - kubeadm init на control-plane
 * - Настройка kubectl
 * - Подготовка токенов для присоединения worker'ов
 */
public class KubernetesInitAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesInitAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("KubernetesInitAgent {} initialized", getLocalName());
    }
}
