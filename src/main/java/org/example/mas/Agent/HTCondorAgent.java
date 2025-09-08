package org.example.mas.Agent;

import jade.lang.acl.ACLMessage;
import org.example.mas.AnsibleAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTCondorAgent extends AnsibleAgent {
    private static final Logger logger = LoggerFactory.getLogger(HTCondorAgent.class);

    @Override
    protected void setup() {
        super.setup();
        logger.info("HTCondorAgent {} initialized", getLocalName());
    }
}
