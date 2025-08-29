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

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("KubernetesInitAgent {} received: {}", getLocalName(), content);

        if (content.contains("K8S_READY_FOR_INIT") || content.contains("START_K8S_INIT")) {
            logger.info("Starting Kubernetes cluster initialization");
        } else if (content.contains("K8S_INIT_COMPLETE")) {
            logger.info("Kubernetes cluster initialization completed successfully");
            // Уведомляем следующий агент о готовности
            sendMessage("calico-agent", "K8S_READY_FOR_CNI");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("Kubernetes cluster initialization completed successfully");
            // Уведомляем следующий агент
            sendMessage("calico-agent", "K8S_INIT_COMPLETE");
        } else {
            logger.error("Kubernetes initialization failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "K8S_INIT_FAILED: " + details);
        }
    }
}
