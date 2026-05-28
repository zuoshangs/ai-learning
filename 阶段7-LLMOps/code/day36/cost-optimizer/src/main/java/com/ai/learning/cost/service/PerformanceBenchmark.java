package com.ai.learning.cost.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Performance benchmarking service.
 * Runs concurrent LLM call simulations to measure throughput and latency.
 */
@Service
public class PerformanceBenchmark {

    private static final Logger log = LoggerFactory.getLogger(PerformanceBenchmark.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Run a benchmark with specified concurrency and request count. */
    public BenchmarkResult runBenchmark(int concurrency, int totalRequests, int simulatedLatencyMs) {
        log.info("Starting benchmark: concurrency={}, requests={}, simLatency={}ms",
                concurrency, totalRequests, simulatedLatencyMs);

        long start = System.nanoTime();
        var latencies = new ConcurrentLinkedQueue<Long>();
        var errors = new ConcurrentLinkedQueue<String>();
        var barrier = new CyclicBarrier(concurrency);

        try {
            var futures = new ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < concurrency; i++) {
                int threadId = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        barrier.await(); // All threads start together
                        int requestsPerThread = totalRequests / concurrency;
                        for (int j = 0; j < requestsPerThread; j++) {
                            long reqStart = System.nanoTime();
                            // Simulate LLM call
                            Thread.sleep(simulatedLatencyMs);
                            long reqEnd = System.nanoTime();
                            latencies.add(TimeUnit.NANOSECONDS.toMillis(reqEnd - reqStart));
                        }
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Benchmark timed out after 60s");
        } catch (Exception e) {
            log.error("Benchmark failed: {}", e.getMessage());
        }

        long elapsedNs = System.nanoTime() - start;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNs);
        var sorted = latencies.stream().sorted().collect(Collectors.toList());
        int completed = latencies.size();

        return new BenchmarkResult(
                concurrency, totalRequests, completed, errors.size(),
                elapsedMs,
                sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2),
                sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95)),
                sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.99)),
                sorted.stream().mapToLong(Long::longValue).average().orElse(0),
                elapsedMs > 0 ? (long) ((double) completed / elapsedMs * 1000) : 0,
                simulatedLatencyMs
        );
    }

    /** Predefined benchmark scenarios. */
    public List<BenchmarkResult> runAllScenarios() {
        List<BenchmarkResult> results = new ArrayList<>();
        int[][] scenarios = {
            {1, 10, 200},    // 单线程 10 次, 200ms 延迟
            {5, 25, 200},    // 5 并发 25 次
            {10, 50, 400},   // 10 并发 50 次, 400ms 延迟
            {20, 100, 200},  // 20 并发 100 次
        };
        for (var s : scenarios) {
            results.add(runBenchmark(s[0], s[1], s[2]));
        }
        return results;
    }

    public static class BenchmarkResult {
        private final int concurrency;
        private final int totalRequests;
        private final int completedRequests;
        private final int errorCount;
        private final long elapsedMs;
        private final long p50LatencyMs;
        private final long p95LatencyMs;
        private final long p99LatencyMs;
        private final double avgLatencyMs;
        private final long throughputRps;  // requests per second
        private final int simulatedLatencyMs;

        public BenchmarkResult(int c, int total, int completed, int errors,
                               long elapsed, long p50, long p95, long p99,
                               double avg, long tps, int simLat) {
            this.concurrency = c; this.totalRequests = total; this.completedRequests = completed;
            this.errorCount = errors; this.elapsedMs = elapsed;
            this.p50LatencyMs = p50; this.p95LatencyMs = p95; this.p99LatencyMs = p99;
            this.avgLatencyMs = avg; this.throughputRps = tps; this.simulatedLatencyMs = simLat;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("concurrency", concurrency); m.put("totalRequests", totalRequests);
            m.put("completedRequests", completedRequests); m.put("errorCount", errorCount);
            m.put("elapsedMs", elapsedMs); m.put("simulatedLatencyMs", simulatedLatencyMs);
            m.put("p50LatencyMs", p50LatencyMs); m.put("p95LatencyMs", p95LatencyMs);
            m.put("p99LatencyMs", p99LatencyMs);
            m.put("avgLatencyMs", Math.round(avgLatencyMs * 10.0) / 10.0);
            m.put("throughputRps", throughputRps);
            return m;
        }

        @Override
        public String toString() {
            return String.format(
                "Concurrency=%d | %d req in %dms | p50=%dms p95=%dms p99=%dms | %d rps",
                concurrency, completedRequests, elapsedMs, p50LatencyMs, p95LatencyMs, p99LatencyMs, throughputRps
            );
        }
    }
}
