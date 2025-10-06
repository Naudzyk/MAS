package org.example.mas;

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
        if (args == null || args.length < 4) {
            logger.error("Agent {} requires: playbook, inventory, timeoutMinutes, workingDir", getLocalName());
            doDelete();
            return;
        }

        this.playbookPath = args[0].toString();
        this.inventoryPath = args[1].toString();
        int timeoutMinutes = Integer.parseInt(args[2].toString());
        this.timeout = Duration.ofMinutes(timeoutMinutes);
        this.workingDir = args[3].toString();

        File wd = new File(workingDir);
        if (!wd.exists() || !wd.isDirectory()) {
            logger.error("Working directory does not exist: {}", workingDir);
            doDelete();
            return;
        }

        Path playbookFullPath = Paths.get(workingDir, playbookPath);
        if (!Files.exists(playbookFullPath)) {
            logger.error("Playbook not found: {}", playbookFullPath);
            doDelete();
            return;
        }

        logger.info("Agent {} initialized with playbook: {}, inventory: {}, timeout: {}, workingDir: {}",
                getLocalName(), playbookPath, inventoryPath, timeout, workingDir);

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
                            String safeLine = line.replaceAll("(?i)(password|token|secret)[=:].*",
                                    "$1=***MASKED***");
                            logger.info("{} | {}", getLocalName(), safeLine);
                            output.append(line).append("\n");
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
                    notifyCompletion(success, "Exit code: " + exitCode);
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

            if ("ABORT".equals(content)) {
                logger.info("Received ABORT signal. Terminating without running playbook.");
                ACLMessage ack = new ACLMessage(ACLMessage.INFORM);
                ack.addReceiver(new AID("coordinator-agent", AID.ISLOCALNAME));
                ack.setContent("ABORT_ACK");
                send(ack);
                doDelete();
                return;
            }

            if (content.contains("START") && started.compareAndSet(false, true)) {
                addBehaviour(new ExecutePlaybookBehaviour());
            }

            handleMessage(msg);
        }
    }

    protected List<String> buildAnsibleCommand() {
        return new ArrayList<>(Arrays.asList(
                "ansible-playbook",
                "-i", inventoryPath,
                playbookPath
        ));
    }

    protected void setupEnvironment(ProcessBuilder pb) {
        pb.environment().putIfAbsent("ANSIBLE_HOST_KEY_CHECKING", "False");
        pb.environment().putIfAbsent("ANSIBLE_STDOUT_CALLBACK", "debug");

        String lower = playbookPath.toLowerCase();
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
        msg.addReceiver(new AID("coordinator-agent", AID.ISLOCALNAME));
        msg.setContent(success ? "COMPLETE" : "FAILED: " + details);
        send(msg);
    }
    // Метод для переопределения
    protected void handleMessage(ACLMessage msg) {

    }
}




