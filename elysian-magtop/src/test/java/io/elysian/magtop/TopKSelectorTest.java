package io.elysian.magtop;

import io.elysian.core.config.TopKConfig;
import io.elysian.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TopKSelectorTest {

    private TopKSelector selector;
    private TopKConfig   cfg;

    @BeforeEach
    void setUp() {
        cfg = new TopKConfig();
        cfg.setK(3);
        cfg.setWeightCpu(0.40);
        cfg.setWeightMemory(0.30);
        cfg.setWeightLocality(0.20);
        cfg.setWeightHistPerf(0.10);
        selector = new TopKSelector(cfg, Collections.emptyMap());
    }

    @Test
    void returnsAtMostKNodes() {
        List<Node> nodes = makeNodes(10);
        Operator op = makeOp("op1", nodes.get(0).getId());
        Placement p = new Placement();
        nodes.forEach(n -> p.assign("op1", n.getId(), 1));

        List<Node> result = selector.findTopK(nodes, op, 3, p);
        assertTrue(result.size() <= 3, "Must return at most k nodes");
    }

    @Test
    void prefersFreeNode() {
        Node crowded = new Node("crowded", "R1", NodeType.CLOUD, 4, 2.0, 16, 10);
        crowded.setCpuUsedFraction(0.95);
        Node free = new Node("free", "R1", NodeType.CLOUD, 4, 2.0, 16, 10);
        free.setCpuUsedFraction(0.10);

        Operator op = makeOp("op1", crowded.getId());
        Placement p = new Placement();
        p.assign("op1", crowded.getId(), 1);

        List<Node> result = selector.findTopK(List.of(crowded, free), op, 1, p);
        assertEquals(1, result.size());
        assertEquals("free", result.get(0).getId(), "Free node should be picked");
    }

    @Test
    void excludesFullyLoadedNodes() {
        Node full = new Node("full", "R1", NodeType.CLOUD, 4, 2.0, 16, 10);
        full.setCpuUsedFraction(0.99);  // only 1% headroom — below 5% threshold

        Operator op = makeOp("op1", full.getId());
        Placement p = new Placement();
        p.assign("op1", full.getId(), 1);

        List<Node> result = selector.findTopK(List.of(full), op, 3, p);
        assertTrue(result.isEmpty(), "Fully loaded node should be excluded");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<Node> makeNodes(int count) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Node n = new Node("n" + i, "R1", NodeType.CLOUD, 8, 2.0, 64, 10);
            n.setCpuUsedFraction(i * 0.05);   // spread from 0 to 45%
            nodes.add(n);
        }
        return nodes;
    }

    private Operator makeOp(String id, String nodeId) {
        return new Operator(id, false, 0, nodeId, 1, List.of(1, 2, 4));
    }
}
