# ELySian Workload Traces

Synthetic benchmark traces matching the Grid'5000 evaluation (§5 of the paper).
Each file is a 10-minute, 1-second-granularity CSV.

## Format
```
timestamp_ms,event_count,source_id
0,10053,src-01
1000,9987,src-01
...
```

| Column | Type | Description |
|---|---|---|
| `timestamp_ms` | int | Wall-clock offset from trace start (ms) |
| `event_count` | int | Number of stream events in this 1-second window |
| `source_id` | string | Kafka partition / data-source identifier |

## Files

| Directory | File | Scenario |
|---|---|---|
| `stream_etl/` | `small_fluctuation.csv` | ±10% periodic variation around 10 000 evt/s |
| `stream_etl/` | `large_burst.csv` | 10× burst at t=180–240 s |
| `fraud_detection/` | `small_fluctuation.csv` | ±10% variation around 5 000 evt/s |
| `fraud_detection/` | `large_burst.csv` | 8× burst at t=200–260 s |
| `voipstream/` | `stateless.csv` | High-rate (20 000 evt/s) slight sinusoidal variation |
| `voipstream/` | `stateful.csv` | Same baseline with higher variance |
| `smartgrid/` | `stateless.csv` | Irregular random bursts around 8 000 evt/s |
| `smartgrid/` | `stateful.csv` | Same with ±20% random swing |

## Replaying

```bash
# Requires Kafka running on localhost:9092
java -cp elysian-app.jar io.elysian.tools.TraceInjector \
     traces/stream_etl/small_fluctuation.csv \
     --topic elysian-input \
     --broker localhost:9092
```

The injector reads timestamps and sleeps accordingly, reproducing real-time inter-arrival rates.
