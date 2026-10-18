package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class MinFn implements AggregationFn {
   public AggregationFn newInstance() { return new MinFn(fldname); }
    public void addToSchema(Schema sch, Schema inputSchema) {
        sch.addField(fieldName(), inputSchema.type(fldname), inputSchema.length(fldname));
    }
    public String fldname;
    private Constant val;

    public MinFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        val = s.getVal(fldname);
    }

    public void processNext(Scan s) {
        Constant newVal = s.getVal(fldname);

        if (newVal.compareTo(val) < 0) {
            val = newVal;
        }
    }

    public String fieldName() {
        return "minof" + fldname;
    }

    public Constant value() {
        return val;
    }
}
