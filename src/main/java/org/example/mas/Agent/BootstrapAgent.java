package org.example.mas.Agent;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.wrapper.AgentController;
import lombok.extern.slf4j.Slf4j;
import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
public class BootstrapAgent extends Agent {
    private final String targetIp;
    private final String initialUser;
    private final String initialPassword;
    private final String publicKeyPath;
    private final String coordinatorName;
    private Session sshSession;
    private ChannelExec sshChannel;

    public BootstrapAgent(String targetIp, String initialUser, String initialPassword, String publicKeyPath, String coordinatorName) {
        this.targetIp = targetIp;
        this.initialUser = initialUser;
        this.initialPassword = initialPassword;
        this.publicKeyPath = publicKeyPath;
        this.coordinatorName = coordinatorName;
    }

    @Override
    protected void setup() {
        log.info("Starting BootstrapAgent for {}", targetIp);

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                try{
                    if (!establishSshConnection()) {
                        log.error("Failed to establish SSH connection to {}", targetIp);
                        doDelete();
                        return;
                    }

                    if (!setupSshKeys()) {
                        log.error("Failed to setup SSH keys on {}", targetIp);
                        closeConnection();
                        doDelete();
                        return;
                    }

                    if (!setupPasswordlessSudo()) {
                        log.error("Failed to setup passwordless sudo on {}", targetIp);
                        closeConnection();
                        doDelete();
                        return;
                    }

                    if (!verifySetup()) {
                        log.error("Verification failed for {}", targetIp);
                        closeConnection();
                        doDelete();
                        return;
                    }

                    createCoordinatorAgent();

                    log.info("Bootstrap completed successfully for {}", targetIp);
                    closeConnection();
                    doDelete();

                } catch (Exception e) {
                    log.error("Bootstrap failed for {} due to: {}", targetIp, e.getMessage(), e);
                    closeConnection();
                    doDelete();
                }
            }
        });
    }

    private boolean establishSshConnection() throws JSchException {
        JSch jsch = new JSch();
        sshSession = jsch.getSession(initialUser, targetIp, 22);
        sshSession.setPassword(initialPassword);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        sshSession.setConfig(config);

        log.info("Connecting to {} as {}...", targetIp,initialUser);
        sshSession.connect(30000);

        if (!sshSession.isConnected()) {
            log.error("SSH connection failed to {}", targetIp);
            return false;
        }

        log.info("SSH connection established");
        return true;
    }
    private boolean setupSshKeys() throws Exception {
        log.info("Setting up SSH key on {}...", targetIp);

        String publicKey = new String(Files.readAllBytes(Paths.get(publicKeyPath)), StandardCharsets.UTF_8);
        publicKey = publicKey.trim();

        String command = String.format(
                "mkdir -p ~/.ssh && " +
                "echo '%s' >> ~/.ssh/authorized_keys && " +
                "chmod 700 ~/.ssh && " +
                "chmod 600 ~/.ssh/authorized_keys",
                publicKey
        );

        return executeCommand(command,"SSH key setup");
    }
     private boolean setupPasswordlessSudo() throws Exception {
        log.info("Setting up passwordless sudo for {} on {}", initialUser, targetIp);

        String command = String.format(
            "echo '%s ALL=(ALL) NOPASSWD: ALL' | sudo tee /etc/sudoers.d/%s && " +
            "sudo chmod 440 /etc/sudoers.d/%s",
            initialUser, initialUser, initialUser
        );

        return executeCommand(command, "Passwordless sudo setup");
    }

    private boolean verifySetup() throws Exception {
        log.info("Verifying setup on {}", targetIp);

        String testKeyCommand = "ssh -o BatchMode=yes -o ConnectTimeout=5 localhost echo 'Key verification successful'";
        if (!executeCommand(testKeyCommand, "Key verification")) {
            return false;
        }

        String testSudoCommand = "sudo -n true && echo 'Sudo verification successful' || echo 'Sudo requires password'";
        return executeCommand(testSudoCommand, "Sudo verification");
    }

    private boolean executeCommand(String command, String stepName) throws Exception {
        log.debug("Executing on {}: {}", targetIp, command);

        sshChannel = (ChannelExec) sshSession.openChannel("exec");
        sshChannel.setCommand(command);

        // Потоки для вывода
        InputStream stdout = sshChannel.getInputStream();
        InputStream stderr = sshChannel.getErrStream();

        sshChannel.connect(10000); // Таймаут 10 секунд

        // Читаем вывод
        String output = IOUtils.toString(stdout, StandardCharsets.UTF_8);
        String error = IOUtils.toString(stderr, StandardCharsets.UTF_8);

        int exitStatus = sshChannel.getExitStatus();
        sshChannel.disconnect();

        if (exitStatus != 0) {
            log.error("{} failed on {} (exit code {}):", stepName, targetIp, exitStatus);
            log.error("STDOUT: {}", output.trim());
            log.error("STDERR: {}", error.trim());
            return false;
        }

        log.debug("{} successful on {}:", stepName, targetIp);
        log.debug("Output: {}", output.trim());
        return true;
    }

    private void closeConnection() {
        if (sshChannel != null && sshChannel.isConnected()) {
            sshChannel.disconnect();
        }
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
    }

    private void createCoordinatorAgent() {
        try {
            log.info("Creating CoordinatorAgent for {}", targetIp);

            String agentType = CoordinatorAgent.class.getName();
            Object[] agentArgs = new Object[]{
                targetIp,
                getContainerController().getContainerName(), // Путь к inventory
                "playbooks/" // Путь к плейбукам
            };

            AgentController ac = getContainerController().createNewAgent(
                coordinatorName,
                agentType,
                agentArgs
            );
            ac.start();

            log.info("CoordinatorAgent started for {}", targetIp);

        } catch (Exception e) {
            log.error("Failed to create CoordinatorAgent for {}: {}", targetIp, e.getMessage(), e);
        }
    }
}
