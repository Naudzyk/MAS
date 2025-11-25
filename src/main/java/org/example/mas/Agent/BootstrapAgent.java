package org.example.mas.Agent;

import jade.core.behaviours.OneShotBehaviour;
import lombok.extern.slf4j.Slf4j;
import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;
import org.example.mas.Service.DeploymentService;
import org.example.mas.SpringContextHelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
public class BootstrapAgent extends BaseAgent {
    private String targetIp;
    private String initialUser;
    private String initialPassword;
    private String publicKeyPath;
    private Session sshSession;
    private ChannelExec sshChannel;

    @Override
    protected void setup() {
        if (!extractArgs()) {
            log.error("BootstrapAgent arguments not provided, stopping.");
            doDelete();
            return;
        }
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

                    log.info("Bootstrap completed successfully for {}", targetIp);
                    sendStatusUpdate("bootstrap:" + targetIp, "SUCCESS");
                    closeConnection();
                    notifyDeploymentService(true);
                    doDelete();

                } catch (Exception e) {
                    log.error("Bootstrap failed for {} due to: {}", targetIp, e.getMessage(), e);
                    closeConnection();
                    sendStatusUpdate("bootstrap:" + targetIp, "FAILED:" + e.getMessage());
                    notifyDeploymentService(false);
                    doDelete();
                }
            }
        });
    }

    private boolean extractArgs() {
        Object[] args = getArguments();
        if (args == null || args.length < 4) {
            return false;
        }
        this.targetIp = (String) args[0];
        this.initialUser = (String) args[1];
        this.initialPassword = (String) args[2];
        this.publicKeyPath = (String) args[3];
        return true;
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

    private void notifyDeploymentService(boolean success) {
        try {
            DeploymentService deploymentService = SpringContextHelper.getBean(DeploymentService.class);
            deploymentService.notifyBootstrapResult(targetIp, success);
        } catch (Exception e) {
            log.warn("Failed to notify deployment service about {} bootstrap: {}", targetIp, e.getMessage());
        }
    }
}
