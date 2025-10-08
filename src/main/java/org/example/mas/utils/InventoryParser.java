package org.example.mas.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class InventoryParser {
    public static Inventory parse(String inventoryPath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(inventoryPath));
        Inventory inv = new Inventory();

        String currentGroup = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                currentGroup = line.substring(1, line.length() - 1).split(":")[0]; // игнорируем :children и т.д.
            } else if (currentGroup != null) {
                // Парсим строку вида: hostname ansible_host=IP ...
                String[] parts = line.split("\\s+");
                if (parts.length == 0) continue;

                String host = parts[0];
                Map<String, String> vars = new HashMap<>();
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i];
                    if (part.contains("=")) {
                        String[] kv = part.split("=", 2);
                        vars.put(kv[0], kv[1]);
                    }
                }

                inv.addHost(currentGroup, host, vars);
            }
        }
        return inv;
    }

    public static class Inventory {
        private final Map<String, List<Host>> groups = new HashMap<>();

        public void addHost(String group, String hostname, Map<String, String> vars) {
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(new Host(hostname, vars));
        }

        public List<Host> getGroup(String groupName) {
            return groups.getOrDefault(groupName, Collections.emptyList());
        }

        public Set<String> getGroups() {
            return groups.keySet();
        }
    }

    public static class Host {
        public final String name;
        public final Map<String, String> vars;

        public Host(String name, Map<String, String> vars) {
            this.name = name;
            this.vars = vars;
        }

        public String getHost() {
            return vars.getOrDefault("ansible_host", name);
        }
    }
}
