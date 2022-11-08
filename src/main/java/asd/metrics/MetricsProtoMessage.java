package asd.metrics;

import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public abstract class MetricsProtoMessage extends ProtoMessage {

    public MetricsProtoMessage(short id) {
        super(id);
    }

    public MetricsMessage serializeToMetric() {
        return null;
    }
}
