package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.query.UpdateScan;
import java.util.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

public class HashJoinPlan implements Plan {
    private Plan p1, p2;
    private String fldname1, fldname2;
    private Schema sch = new Schema();
    private Transaction tx;

    public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.tx = tx;
        this.p1 = p1;
        this.p2 = p2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;

        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    public Scan open() {
        int size1 = new MaterializePlan(tx, p1).blocksAccessed();
        int size2 = new MaterializePlan(tx, p2).blocksAccessed();
        boolean buildLeft = size1 <= size2;
        int partitions = Math.max(1, Math.min(Math.min(size1, size2), tx.availableBuffs() - 2));
        List<TempTable> left = partition(p1, fldname1, partitions);
        List<TempTable> right = partition(p2, fldname2, partitions);
        return new HashJoinScan(buildLeft ? left : right, buildLeft ? right : left,
                buildLeft ? p1.schema() : p2.schema(), buildLeft ? p2.schema() : p1.schema(),
                buildLeft ? fldname1 : fldname2, buildLeft ? fldname2 : fldname1, buildLeft);
    }

    private List<TempTable> partition(Plan input, String key, int count) {
        List<TempTable> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) parts.add(new TempTable(tx, input.schema()));
        // Open the child before assigning output buffers; complex children may pin several.
        Scan source = input.open();
        List<UpdateScan> outputs = new ArrayList<>();
        try {
            boolean keepOpen = tx.availableBuffs() > count;
            if (keepOpen)
                for (TempTable part : parts) outputs.add(part.open());
            while (source.next()) {
                int bucket = Math.floorMod(source.getVal(key).hashCode(), count);
                UpdateScan dest = keepOpen ? outputs.get(bucket) : parts.get(bucket).open();
                try {
                    dest.insert();
                    for (String field : input.schema().fields()) dest.setVal(field, source.getVal(field));
                } finally { if (!keepOpen) dest.close(); }
            }
        } finally {
            for (UpdateScan output : outputs) output.close();
            source.close();
        }
        return parts;
    }

    public int blocksAccessed() {
        // One child read, then a write and read of its partitions. Compact-size
        // approximation: ignores partition padding, cache hits and hash skew.
        long blocks = (long) new MaterializePlan(tx, p1).blocksAccessed()
                + new MaterializePlan(tx, p2).blocksAccessed();
        return (int) Math.min(Integer.MAX_VALUE,
                (long) p1.blocksAccessed() + p2.blocksAccessed() + 2 * blocks);
    }

    public int recordsOutput() {
        int maxvals = Math.max(1, Math.max(p1.distinctValues(fldname1), p2.distinctValues(fldname2)));
        return (int) Math.min(Integer.MAX_VALUE, (long) p1.recordsOutput() * p2.recordsOutput() / maxvals);
    }

    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname)) {
            return p1.distinctValues(fldname);
        } else {
            return p2.distinctValues(fldname);
        }
    }

    public Schema schema() {
        return sch;
    }
}
