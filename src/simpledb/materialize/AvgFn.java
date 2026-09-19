package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;

public class AvgFn implements AggregationFn {
    private String fldname;
    private int sum;
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
        return new Constant(sum/count);
    }
}
