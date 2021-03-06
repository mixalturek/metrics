package com.avast.metrics.dropwizard;

import com.avast.metrics.TimerPairImpl;
import com.avast.metrics.api.*;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MetricsMonitor implements Monitor {

    protected static final Logger LOGGER = LoggerFactory.getLogger(MetricsMonitor.class);

    public static final String NAME_SEPARATOR = "/";

    protected final MetricRegistry registry;
    protected final List<String> names = new ArrayList<>();
    protected final Naming naming;

    public MetricsMonitor() {
        this(new MetricRegistry(), Naming.defaultNaming());
    }

    public MetricsMonitor(MetricRegistry registry, Naming naming) {
        this.registry = registry;
        this.naming = naming;
    }

    protected MetricsMonitor(MetricsMonitor original, String... names) {
        this.registry = original.registry;
        this.names.addAll(original.names);
        this.names.addAll(Arrays.asList(names));
        this.naming = original.naming;
    }

    @Override
    public MetricsMonitor named(String name) {
        return new MetricsMonitor(this, name);
    }

    @Override
    public MetricsMonitor named(String name1, String name2, String... restOfNames) {
        return new MetricsMonitor(named(name1).named(name2), restOfNames);
    }

    @Override
    public String getName() {
        return constructMetricName(Optional.empty(), "/");
    }

    @Override
    public Meter newMeter(String name) {
        return withMetricName(name, n -> new MetricsMeter(n, registry.meter(n)));
    }

    @Override
    public Counter newCounter(String name) {
        return withMetricName(name, n -> new MetricsCounter(n, registry.counter(n)));
    }

    @Override
    public Timer newTimer(String name) {
        return withMetricName(name, n -> new MetricsTimer(n, registry.timer(n)));
    }

    @Override
    public TimerPair newTimerPair(String name) {
        return new TimerPairImpl(
                newTimer(naming.successTimerName(name)),
                newTimer(naming.failureTimerName(name))
        );
    }

    @Override
    public <T> Gauge<T> newGauge(String name, Supplier<T> gauge) {
        return newGauge(name, false, gauge);
    }

    @Override
    public <T> Gauge<T> newGauge(String name, boolean replaceExisting, Supplier<T> gauge) {
        return withMetricName(name, n -> {
            MetricsGauge.SupplierGauge<T> supplierGauge = new MetricsGauge.SupplierGauge<>(gauge);
            if (replaceExisting) {
                registry.remove(n);
            }
            registry.register(n, supplierGauge);
            return new MetricsGauge<>(n, supplierGauge);
        });
    }

    @Override
    public Histogram newHistogram(String name) {
        return withMetricName(name, n -> new MetricsHistogram(n, registry.histogram(n)));
    }

    @Override
    public void remove(Metric metric) {
        registry.remove(metric.getName());
    }

    protected <T> T withMetricName(String name, Function<String, T> metricCreator) {
        String finalName = constructMetricName(name);

        if (LOGGER.isDebugEnabled() && !registry.getNames().contains(finalName)) {
            String nameForLogging = finalName.replaceAll(Pattern.quote(separator()), "/");
            LOGGER.debug("Creating metric '{}'", nameForLogging);
        }

        return metricCreator.apply(finalName);
    }

    protected String separator() {
        return NAME_SEPARATOR;
    }

    protected String constructMetricName(String finalName) {
        return constructMetricName(Optional.ofNullable(finalName), separator());
    }

    protected String constructMetricName(Optional<String> finalName, String separator) {
        List<String> copy = new ArrayList<>(names);
        finalName.ifPresent(copy::add);
        return copy.stream().collect(Collectors.joining(separator));
    }

    @Override
    public void close() {
        LOGGER.debug("Closing monitor (all metrics will be removed from the underlying registry)");
        registry.removeMatching(MetricFilter.ALL);
    }

}
