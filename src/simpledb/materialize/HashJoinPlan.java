package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.query.UpdateScan;
import java.util.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/**
 * The Plan class for the partition-based hash join.
 * Both inputs are split on their join field into the same number of
 * temporary tables, using the same hash function, so a record of p1 can
 * only match records of p2 in the partition with the same index.
 * HashJoinScan then joins the partitions pair by pair in memory.
 */
public class HashJoinPlan implements Plan {
   public String description() { return "HashJoin [" + fldname1 + " = " + fldname2 + "]"; }
   public java.util.List<Plan> children() { return java.util.Arrays.asList(p1, p2); }
    private Plan p1, p2;
    private String fldname1, fldname2;
    private Schema sch = new Schema();
    private Transaction tx;

    /**
     * Creates a hash join of the two inputs on p1.fldname1 = p2.fldname2.
     * @param tx the calling transaction
     * @param p1 the left input
     * @param p2 the right input
     * @param fldname1 the join field of p1
     * @param fldname2 the join field of p2
     */
    public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        this.tx = tx;
        this.p1 = p1;
        this.p2 = p2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;

        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    /**
     * Partitions both inputs, then returns a scan that joins them partition by partition.
     * The smaller input, in blocks, is the build side, because each of its
     * partitions is held in an in-memory hash table.
     * There are never more partitions than the smaller input has blocks, and two
     * buffers are left free for reading the input.
     * Partitions are not split further, so a build partition is assumed to fit in memory.
     */
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

    /**
     * Copies every record of the input into one of count temporary tables,
     * chosen by the hash of its join field.
     * If there are enough free buffers, all partitions stay open for the whole pass.
     * Otherwise each record's partition is opened and closed around its insert,
     * which is slower but needs only one buffer at a time.
     */
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

    /**
     * The same estimate as any equality join:
     * R(p1)*R(p2) / max(V(p1,fldname1), V(p2,fldname2)).
     */
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
