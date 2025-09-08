package org.example.mas.Agent;

import org.example.mas.AnsibleAgent;


import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Агент для установки и настройки Calico CNI (05_calico_cni.yml)
 * - Установка Calico
 * - Настройка сетевой политики
 * - Конфигурация VXLAN и NAT
 */
public class CalicoAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(CalicoAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("CalicoAgent {} initialized", getLocalName());
    }
}
