package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class SumFn implements AggregationFn {
   public AggregationFn newInstance() { return new SumFn(fldname); }
    public void addToSchema(Schema sch, Schema inputSchema) { sch.addIntField(fieldName()); }
    private String fldname;
    private int sum;

    public SumFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        sum = s.getInt(fldname);
    }

    public void processNext(Scan s) {
        sum += s.getInt(fldname);
    }

    public String fieldName() {
        return "sumof" + fldname;
    }

    public Constant value() {
        return new Constant(sum);
    }
}
