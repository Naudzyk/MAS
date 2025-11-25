package org.example.mas.Service;

import lombok.extern.slf4j.Slf4j;
import org.example.mas.models.ClusterNode;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class NodeService {

    private final CopyOnWriteArrayList<ClusterNode> nodes = new CopyOnWriteArrayList<>();

    public void replaceNodes(List<ClusterNode> newNodes) {
        nodes.clear();
        if (!CollectionUtils.isEmpty(newNodes)) {
            newNodes.stream()
                    .filter(this::isValidNode)
                    .forEach(nodes::add);
        }
        log.info("Stored {} nodes for inventory generation", nodes.size());
    }

    public List<ClusterNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    private boolean isValidNode(ClusterNode node) {
        return node != null
            && node.getInventoryName() != null
            && node.getIpAddress() != null
            && node.getRole() != null
            && node.getSshUser() != null;
    }
}

