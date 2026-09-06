package simpledb.plan;

import simpledb.query.NestedLoopsScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

public class NestedLoopsPlan implements Plan {
    private Plan p1, p2;
    private Predicate joinpred;
    private Schema schema = new Schema();

    public NestedLoopsPlan(Plan p1, Plan p2, Predicate joinpred) {
        this.p1 = p1;
        this.p2 = p2;
        this.joinpred = joinpred;

        schema.addAll(p1.schema());
        schema.addAll(p2.schema());
    }

    public Scan open() {
        Scan s1 = p1.open();
        Scan s2 = p2.open();
        return new NestedLoopsScan(s1, s2, joinpred);
    }

    public int blocksAccessed() {
        return p1.blocksAccessed() +
               p1.recordsOutput() * p2.blocksAccessed();
    }

    public int recordsOutput() {
        return p1.recordsOutput() * p2.recordsOutput() / joinpred.reductionFactor(this);
    }

    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname)) {
            return p1.distinctValues(fldname);
        } else {
            return p2.distinctValues(fldname);
        }
    }

    public Schema schema() {
        return schema;
    }
}
