package org.example.mas.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerAgent extends BaseAgent {
    private static final Logger logger = LoggerFactory.getLogger(WorkerAgent.class);

    @Override
    protected void setup() {
        Object[] args = getArguments();
        String nodeName = args != null && args.length > 0 ? (String) args[0] : "unknown";
        logger.info("WorkerAgent {} initialized for node: {}", getLocalName(), nodeName);
    }
}
