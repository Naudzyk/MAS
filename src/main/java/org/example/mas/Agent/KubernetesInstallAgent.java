package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для установки Kubernetes компонентов (03_kubernetes_install.yml)
 */
public class KubernetesInstallAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesInstallAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("KubernetesInstallAgent {} initialized", getLocalName());
    }

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("KubernetesInstallAgent {} received: {}", getLocalName(), content);
        if (content.contains("CONTAINERD_READY_FOR_K8S") || content.contains("START_K8S_INSTALL")) {
            logger.info("Starting Kubernetes components installation");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);
        if (success) {
            sendMessage("kubernetes-init-agent", "K8S_INSTALL_COMPLETE");
        } else {
            sendMessage("coordinator-agent", "K8S_INSTALL_FAILED: " + details);
        }
    }
}
