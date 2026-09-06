package simpledb.plan;

import simpledb.query.NestedLoopsScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The Plan class corresponding to the nested loops
 * join relational algebra operator.
 */
public class NestedLoopsPlan implements Plan {
    private Plan p1, p2;
    private Predicate joinpred;
    private Schema schema = new Schema();

    /**
     * Creates a new nested loops join node.
     * @param p1
     * @param p2
     * @param joinpred
     */
    public NestedLoopsPlan(Plan p1, Plan p2, Predicate joinpred) {
        this.p1 = p1;
        this.p2 = p2;
        this.joinpred = joinpred;

        schema.addAll(p1.schema());
        schema.addAll(p2.schema());
    }

    /**
     * Creates a nested loops join scan.
     */
    public Scan open() {
        Scan s1 = p1.open();
        Scan s2 = p2.open();
        return new NestedLoopsScan(s1, s2, joinpred);
    }

    /**
     * Estimates the number of block accesses.
     * @return the estimate number of block accesses
     */
    public int blocksAccessed() {
        return p1.blocksAccessed() +
               p1.recordsOutput() * p2.blocksAccessed();
    }

    /**
     * Estimates the number of output records.
     * @return the estimate number of output records
     */
    public int recordsOutput() {
        return p1.recordsOutput() * p2.recordsOutput() / joinpred.reductionFactor(this);
    }

    /**
     * Estimates the number of distinct values.
     * @param fldname the name of a field
     * @return the estimate number of distinct values.
     */
    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname)) {
            return p1.distinctValues(fldname);
        } else {
            return p2.distinctValues(fldname);
        }
    }

    /**
     * Returns the schema of the join.
     * @return the schema of the join
     */
    public Schema schema() {
        return schema;
    }
}
