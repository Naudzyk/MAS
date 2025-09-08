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
}
