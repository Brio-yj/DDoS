package com.example.common.metrics;

import org.HdrHistogram.Recorder;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RequestMetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(RequestMetricsRecorder.class);

    private final Recorder recorder;
    private final LongAdder intervalRequests = new LongAdder();
    private final LongAdder intervalErrors = new LongAdder();
    private final Duration interval;

    public RequestMetricsRecorder(@Value("${metrics.logging.interval:10000}") long intervalMillis) {
        this.recorder = new Recorder(TimeUnit.MICROSECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(120), 3);
        this.interval = Duration.ofMillis(intervalMillis);
    }

    public void record(long durationMicros, int status) {
        recorder.recordValue(durationMicros);
        intervalRequests.increment();
        if (status >= 400) {
            intervalErrors.increment();
        }
    }

    @Scheduled(fixedRateString = "${metrics.logging.interval:10000}")
    public void logSnapshot() {
        Histogram histogram = recorder.getIntervalHistogram();
        long requests = intervalRequests.sumThenReset();
        long errors = intervalErrors.sumThenReset();
        if (requests == 0) {
            log.info("request-metrics | windowMs={} | rps=0.0 | avgMs=0.0 | p95Ms=0.0 | p99Ms=0.0 | errorRate=0.0", interval.toMillis());
            return;
        }
        double windowSeconds = interval.toMillis() / 1000.0;
        double rps = requests / windowSeconds;
        double avgMs = histogram.getMean() / 1000.0;
        double p95 = histogram.getValueAtPercentile(95.0) / 1000.0;
        double p99 = histogram.getValueAtPercentile(99.0) / 1000.0;
        double errorRate = (errors * 100.0) / requests;
        log.info("request-metrics | windowMs={} | rps={} | avgMs={} | p95Ms={} | p99Ms={} | errorRate={}", interval.toMillis(),
                String.format("%.2f", rps), String.format("%.2f", avgMs),
                String.format("%.2f", p95), String.format("%.2f", p99),
                String.format("%.2f%%", errorRate));
    }
}
