package org.example.mas.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mas.models.ClusterNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerraformService {

    private final NodeService nodeService;

    @Value("${mas.paths.terraform:terraform}")
    private String terraformDir;

    @Value("${mas.paths.inventory:inventory.ini}")
    private String inventoryOutput;

    public Path generateInventory() {
        List<ClusterNode> nodes = nodeService.getNodes();
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Сначала добавьте хотя бы один узел.");
        }


        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        log.info("Текущая рабочая директория: {}", currentDir);
        log.info("Конфигурация terraform директории: {}", terraformDir);

        Path terraformPath = Paths.get(terraformDir).toAbsolutePath().normalize();
        log.info("Абсолютный путь к terraform директории: {}", terraformPath);


        if (!Files.exists(terraformPath) || !Files.isDirectory(terraformPath)) {
            throw new IllegalStateException("Terraform директория не существует: " + terraformPath);
        }

        // Проверяем наличие main.tf
        Path mainTf = terraformPath.resolve("main.tf");
        if (!Files.exists(mainTf)) {
            throw new IllegalStateException("Файл main.tf не найден в директории: " + terraformPath);
        }

        log.info("Файл main.tf найден: {}", mainTf);


        try {
            log.info("Содержимое terraform директории:");
            Files.list(terraformPath).forEach(path ->
                log.info("  - {} ({})", path.getFileName(),
                    Files.isDirectory(path) ? "директория" : "файл")
            );
        } catch (IOException e) {
            log.warn("Не удалось прочитать содержимое terraform директории: {}", e.getMessage());
        }

        Path inventoryYaml = terraformPath.resolve("inventory.yaml");
        writeInventoryYaml(inventoryYaml, nodes);

        runTerraformCommand(terraformPath, "init", "-input=false");
        runTerraformCommand(terraformPath, "apply", "-auto-approve");

        Path output = Paths.get(inventoryOutput).toAbsolutePath();
        if (!Files.exists(output)) {
            log.error("Файл inventory.ini не найден по пути: {}", output);
            log.error("Terraform работал в директории: {}", terraformPath);
            throw new IllegalStateException("Terraform не создал inventory по адресу " + output +
                ". Проверьте, что terraform apply выполнился успешно и создал файл в scripts/inventory.ini");
        }

        log.info("Inventory.ini обновлён: {}", output);
        return output;
    }

    private void writeInventoryYaml(Path destination, List<ClusterNode> nodes) {
        try {
            Files.createDirectories(destination.getParent());
            StringBuilder yaml = new StringBuilder("nodes:\n");
            for (ClusterNode node : nodes) {
                yaml.append("  - inventory_name: ").append(node.getInventoryName()).append("\n")
                    .append("    ip_address: ").append(node.getIpAddress()).append("\n")
                    .append("    group: ").append(node.getRole()).append("\n")
                    .append("    ssh_user: ").append(node.getSshUser()).append("\n");
                String sshKeyPath = (node.getSshKeyPath() != null && !node.getSshKeyPath().isBlank())
                    ? node.getSshKeyPath()
                    : "";
                yaml.append("    ssh_key_path: ").append(sshKeyPath).append("\n");
            }
            Files.writeString(destination, yaml.toString(), StandardCharsets.UTF_8);
            log.debug("inventory.yaml обновлён ({} узлов)", nodes.size());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось записать inventory.yaml", e);
        }
    }

    private void runTerraformCommand(Path terraformPath, String... args) {
        try {
            log.info("Выполнение terraform команды в директории: {}", terraformPath);

            List<String> command = new ArrayList<>();
            command.add("terraform");
            command.add("-chdir=" + terraformPath.toString());
            for (String arg : args) {
                command.add(arg);
            }

            log.info("Команда terraform: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("Terraform output: {}", line);
                    output.append(line).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Terraform команда зависла: " + String.join(" ", args));
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException("Terraform завершился с ошибкой: " + output);
            }

            log.info("Terraform {} завершён", String.join(" ", args));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Terraform команда прервана", e);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось выполнить terraform " + String.join(" ", args), e);
        }
    }
}

