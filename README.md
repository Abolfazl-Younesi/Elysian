# ELySian — Joint Operator Placement & Auto-Scaling

> **Artifact:** ~8 000 lines of Java implementing the ELySian system as described in  
> *"ELySian: Joint Operator Placement and Scaling through Multi-Agent Game Theory, Dynamic Functional Decomposition, and Multi-Tier Adaptive Materialization"*

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Quick-Start (Docker Compose)](#quick-start)
5. [Configuration Reference](#configuration-reference)
6. [Module Descriptions](#module-descriptions)
7. [Running Tests](#running-tests)
8. [Deploying on Kubernetes](#deploying-on-kubernetes)
9. [Workload Traces](#workload-traces)
10. [Contributing](#contributing)

---

## Overview

ELySian jointly optimises **operator placement** and **auto-scaling** in Apache Flink pipelines running on heterogeneous edge–cloud clusters. Three synergistic innovations:

| Component | Purpose |
|---|---|
| **MAGTOP** | Multi-Agent Game-Theoretic Operator Placement — decentralised best-response with Nash convergence |
| **DFD** | Dynamic Functional Decomposition — splits hot operators into independently-placed micro-operators |
| **MTAM** | Multi-Tier Adaptive Materialization — lightweight LSTM predicts access patterns and assigns state to HOT/WARM/COLD tiers |
| **ReFS** | Resource Frequency Scaling — closed-loop per-node CPU frequency/voltage controller |
| **Coord** | Distributed Coordination Protocol — ZooKeeper-backed locking with exponential back-off |

---

## Architecture

```
elysian-java/
├── elysian-core/          # Config POJOs + domain model (Node, Operator, Placement, StateTier)
├── elysian-magtop/        # MAGTOP loop, TopKSelector, UtilityCalculator, InitialPlacement
├── elysian-dfd/           # DFD profiling and operator decomposition
├── elysian-mtam/          # LstmPredictor, StateTierAssigner
├── elysian-refs/          # RefsController, WorkloadCharacteriser, FrequencyVoltageUpdater
├── elysian-coordination/  # DistributedLockManager, CoordinatedMigrationExecutor
├── elysian-flink/         # Flink JobManager plugin and metrics collector
├── elysian-app/           # ElysianMain entry point
├── traces/                # Benchmark workload CSVs
├── k8s/                   # Kubernetes manifests
└── docker-compose.yml     # Local dev stack
```

---

## Prerequisites

| Dependency | Version |
|---|---|
| JDK | 21+ |
| Gradle (wrapper) | 8.7 |
| Docker + Compose | 24+ |
| Apache Flink | 1.20.0 |
| Apache Kafka | 3.9.0 |
| ZooKeeper | 3.8.3 |
| Kubernetes (optional) | 1.29+ |

---

## Quick-Start

### 1. Clone and build

```bash
git clone https://github.com/<org>/elysian.git
cd elysian/elysian-java
./gradlew build
```

### 2. Start the local stack

```bash
docker compose up -d
# Flink UI: http://localhost:8081
```

### 3. Run ELySian

```bash
./gradlew :elysian-app:run
# Override config:
java -Delysian.config=/path/to/my.yaml -jar elysian-app/build/libs/elysian-app-1.0.0.jar
```

### 4. Inject a workload trace

```bash
# Replay Stream-ETL small-fluctuation trace
java -cp elysian-app/build/libs/elysian-app-1.0.0.jar \
     io.elysian.tools.TraceInjector \
     traces/stream_etl/small_fluctuation.csv
```

---

## Configuration Reference

All parameters live in **`elysian.yaml`** at the project root. Mount it via `-Delysian.config=` or as a Kubernetes ConfigMap.

### MAGTOP

| Key | Default | Description |
|---|---|---|
| `magtop.alpha` | `0.35` | Resource-utility weight (RU) |
| `magtop.beta` | `0.30` | Latency-utility weight (LU) |
| `magtop.gamma` | `0.20` | Migration-cost weight (MC) |
| `magtop.theta` | `0.15` | Communication-affinity weight (CA) |
| `magtop.epsilon` | `0.05` | Min utility gain to trigger migration |
| `magtop.maxIterations` | `50` | Convergence guard |
| `magtop.topK.k` | `3` | Candidate nodes per operator |

### MTAM / LSTM

| Key | Default | Description |
|---|---|---|
| `mtam.hotThresholdAccessPerSec` | `100` | HOT tier threshold |
| `mtam.warmThresholdAccessPerSec` | `10` | WARM tier threshold |
| `mtam.lstm.hiddenUnits` | `32` | LSTM hidden units per layer |
| `mtam.lstm.layers` | `2` | Number of LSTM layers |
| `mtam.lstm.windowSize` | `100` | Sliding window (access events) |
| `mtam.lstm.confidenceThreshold` | `0.5` | Below → EWMA fallback |

### ReFS

| Key | Default | Description |
|---|---|---|
| `refs.targetUtilization` | `0.70` | u_target for frequency control |
| `refs.gamma` | `0.10` | Frequency adjustment gain |
| `refs.delta` | `0.02` | Hysteresis fraction of f_max |
| `refs.monitoringIntervalMs` | `1000` | Control loop period |

### Coordination

| Key | Default | Description |
|---|---|---|
| `coordination.maxRetries` | `5` | Migration retry budget |
| `coordination.initialBackoffMs` | `100` | Initial back-off time |
| `coordination.zookeeperConnect` | `localhost:2181` | ZK connect string; use `local` for in-JVM |

---

## Module Descriptions

### `elysian-core`
Domain model (`Node`, `Operator`, `Placement`, `StateTier`) and all config POJOs. Zero dependencies on other ELySian modules.

### `elysian-magtop`
- **`TopKSelector`** — O(m) min-heap, composite scoring (CPU 40%, Mem 30%, Locality 20%, Hist 10%)
- **`UtilityCalculator`** — RU + LU − MC + CA with all four Paper formulas
- **`InitialPlacementStrategy`** — Kahn topological-sort greedy initialiser
- **`MagtopPlanner`** — outer MAGTOP loop with convergence check and weight self-tuning

### `elysian-mtam`
- **`LstmPredictor`** — pure-Java 2-layer LSTM (32 hidden units), Xavier init, EWMA fallback
- **`StateTierAssigner`** — sliding-window + LSTM → HOT/WARM/COLD with cost–benefit gating

### `elysian-refs`
- **`RefsController`** — background thread running the closed-loop frequency controller
- **`FrequencyVoltageUpdater`** — UFV: log-scaled Δf, hysteresis gate, square-root voltage mapping
- **`LoadFactorCalculator`** — L_i = (u_i / Σu_j) × m

### `elysian-coordination`
- **`DistributedLockManager`** — in-JVM `ReentrantLock` (swap ZooKeeper impl for production)
- **`CoordinatedMigrationExecutor`** — lock → conflict-check → reserve → broadcast → execute → rollback
- **`MigrationCheckpointStore`** — pre-migration snapshot for rollback

### `elysian-dfd`
- **`DfdManager`** — profiles operators, detects hot-path CPU fraction, splits into `_hot` + `_cold` micro-operators

---

## Running Tests

```bash
./gradlew test           # all unit tests
./gradlew test --info    # with verbose output
```

Key test classes:

| Test | What it covers |
|---|---|
| `TopKSelectorTest` | Size bound, free-node preference, fully-loaded exclusion |
| `FrequencyVoltageUpdaterTest` | f/V clamping, hysteresis, idle fallback |
| `LstmPredictorTest` | EWMA fallback, non-negative output, feature correctness |
| `LstmPredictorTest#stateTierAssignerHotClassification` | HOT tier assignment at high access rate |

---

## Deploying on Kubernetes

```bash
kubectl create namespace elysian
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
```

To change any parameter, edit `k8s/configmap.yaml` and re-apply — no image rebuild needed.

---

## Workload Traces

`traces/` contains synthetic benchmark traces matching the Grid'5000 evaluation (§5):

| Directory | Experiments | Format |
|---|---|---|
| `stream_etl/` | small_fluctuation, large_burst | `timestamp_ms, event_count, source_id` |
| `fraud_detection/` | small_fluctuation, large_burst | same |
| `voipstream/` | stateless, stateful | same |
| `smartgrid/` | stateless, stateful | same |

**Format:** CSV, 10 minutes, 1-second granularity, 600 rows per file.

Replay with the built-in injector:
```bash
java -cp elysian-app.jar io.elysian.tools.TraceInjector <trace-file.csv> [--topic <kafka-topic>]
```

---

## Contributing

1. Fork and create a feature branch: `git checkout -b feature/my-feature`
2. Write tests for new behaviour
3. Run `./gradlew build` — all tests must pass
4. Open a pull request with a description referencing the relevant algorithm section

Code style is enforced by Checkstyle (`config/checkstyle/checkstyle.xml`).

---

*ELySian is implemented in Java 21 with approximately 8 000 lines of source code (excluding tests).*
