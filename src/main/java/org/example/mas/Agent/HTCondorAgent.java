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

    @Override
    protected void handleMessage(ACLMessage msg) {
        String content = msg.getContent();
        logger.info("HTCondorAgent {} received: {}", getLocalName(), content);

        if (content.contains("CLUSTER_READY_FOR_HTCONDOR") || content.contains("START_HTCONDOR")) {
            logger.info("Starting HTCondor deployment");
        } else if (content.contains("HTCONDOR_COMPLETE")) {
            logger.info("HTCondor deployment completed successfully");
            // Уведомляем координатора о завершении всего процесса
            sendMessage("coordinator-agent", "DEPLOYMENT_COMPLETE");
        }
    }

    @Override
    protected void notifyCompletion(boolean success, String details) {
        super.notifyCompletion(success, details);

        if (success) {
            logger.info("HTCondor deployment completed successfully");
            // Уведомляем координатора о завершении
            sendMessage("coordinator-agent", "HTCONDOR_COMPLETE");
        } else {
            logger.error("HTCondor deployment failed: {}", details);
            // Уведомляем об ошибке
            sendMessage("coordinator-agent", "HTCONDOR_FAILED: " + details);
        }
    }
}
