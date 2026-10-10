package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class AvgFn implements AggregationFn {
    public void addToSchema(Schema sch, Schema inputSchema) { sch.addIntField(fieldName()); }
    private String fldname;
    private long sum;
    private int count;

    public AvgFn(String fldname) {
        this.fldname = fldname;
    }

    @Override
    public void processFirst(Scan s) {
        sum = s.getInt(fldname);
        count = 1;
    }

    @Override
    public void processNext(Scan s) {
        sum += s.getInt(fldname);
        count++;
    }

    public String fieldName() {
        return "avgof" + fldname;
    }

    public Constant value() {
        return new Constant((int) Math.round((double) sum / count));
    }
}
