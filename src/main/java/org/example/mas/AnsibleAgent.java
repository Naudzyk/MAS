package org.example.mas;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AnsibleAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(AnsibleAgent.class);

    protected String playbookPath;
    protected String inventoryPath;
    protected Duration timeout;
    protected String workingDir;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 2) {
            this.playbookPath = args[0].toString();
            this.inventoryPath = args[1].toString();
            this.timeout = args.length > 2 ? Duration.ofMinutes(Long.parseLong(args[2].toString()))
                    : Duration.ofMinutes(30);
            if (args.length > 3) {
                this.workingDir = args[3].toString();
            } else {
                this.workingDir = System.getProperty("user.dir");
                logger.warn("Working directory not specified, using default: {}", workingDir);
            }
        } else {
            logger.error("Agent {} requires at least playbook and inventory arguments", getLocalName());
            doDelete();
            return;
        }

        File workingDirFile = new File(workingDir);
        if (!workingDirFile.exists() || !workingDirFile.isDirectory()) {
            logger.error("Working directory does not exist or is not a directory: {}", workingDir);
            doDelete();
            return;
        }

        logger.info("Agent {} initialized with playbook: {}, inventory: {}",
                getLocalName(), playbookPath, inventoryPath);

        addBehaviour(new MessageHandlerBehaviour());
    }

    private class ExecutePlaybookBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            try {
                logger.info("Agent {} starting playbook execution: {}", getLocalName(), playbookPath);

                List<String> command = buildAnsibleCommand();
                ProcessBuilder pb = new ProcessBuilder(command);

                setupEnvironment(pb);
                pb.directory(new File(workingDir));
                pb.redirectErrorStream(true);

                logger.info("Executing command: {}", String.join(" ", command));

                Process process = pb.start();

                CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        StringBuilder output = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                            logger.info("{} | {}", getLocalName(), line);
                        }
                        return output.toString();
                    } catch (IOException e) {
                        logger.error("Error reading process output", e);
                        return "";
                    }
                });

                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

                if (!finished) {
                    logger.warn("Agent {} playbook execution timed out after {}", getLocalName(), timeout);
                    process.destroyForcibly();
                    notifyCompletion(false, "Timeout after " + timeout);
                } else {
                    String output = outputFuture.get();
                    int exitCode = process.exitValue();
                    boolean success = exitCode == 0;
                    logger.info("Agent {} playbook completed with exit code: {}", getLocalName(), exitCode);
                    notifyCompletion(success, "Exit code: " + exitCode + "\nOutput: " + output);
                }

            } catch (Exception e) {
                logger.error("Agent {} failed to execute playbook", getLocalName(), e);
                notifyCompletion(false, "Exception: " + e.getMessage());
            }
        }
    }

    private class MessageHandlerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg == null) {
                block();
                return;
            }

            String content = msg.getContent() == null ? "" : msg.getContent();
            logger.info("Agent {} received message: {}", getLocalName(), content);

            if (content.contains("START") && started.compareAndSet(false, true)) {
                addBehaviour(new ExecutePlaybookBehaviour());
            }

            handleMessage(msg);
        }
    }

        protected List<String> buildAnsibleCommand() {
        List<String> command = new ArrayList<>();
        command.add("ansible-playbook");
        command.add("-i");
        command.add(inventoryPath);
        command.add(playbookPath);
        return command;
    }

    protected void setupEnvironment(ProcessBuilder pb) {
        pb.environment().putIfAbsent("ANSIBLE_HOST_KEY_CHECKING", "False");
        pb.environment().putIfAbsent("ANSIBLE_STDOUT_CALLBACK", "debug");

        String lower = playbookPath == null ? "" : playbookPath.toLowerCase();
        if (lower.contains("kubernetes") || lower.contains("calico") || lower.contains("htcondor")) {
            pb.environment().put("KUBECONFIG", "/etc/kubernetes/admin.conf");
        }
        if (lower.contains("htcondor")) {
            pb.environment().putIfAbsent("CONDOR_LOG_LEVEL", "D_FULLDEBUG");
        }
    }

    protected void notifyCompletion(boolean success, String details) {
        logger.info("Agent {} playbook execution {}.", getLocalName(), success ? "SUCCESS" : "FAILED");
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID("coordinator-agent", jade.core.AID.ISLOCALNAME));
        msg.setContent(success ? "COMPLETE" : "FAILED: " + details);
        send(msg);
    }

    protected void handleMessage(ACLMessage msg) {
        logger.debug("Agent {} processing message: {}", getLocalName(), msg.getContent());
    }

    protected void sendMessage(String agentName, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID(agentName, jade.core.AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
        logger.debug("Agent {} sent message to {}: {}", getLocalName(), agentName, content);
    }
}




