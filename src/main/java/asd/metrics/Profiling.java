package asd.metrics;

public class Profiling {
    public static class Span implements AutoCloseable {
        private final String name;
        private final long start;

        Span(String name) {
            this.name = name;
            this.start = System.nanoTime();
        }

        @Override
        public void close() {
            var elapsed = System.nanoTime() - this.start;
            var ms = (double) elapsed / 1_000_000.0;
            Metrics.span(this.name, ms);
        }

    }

    public static Span span(String name) {
        return new Span(name);
    }

}
