package simpledb.plan;

import simpledb.query.NestedLoopsScan;
import simpledb.query.Predicate;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The Plan class for the tuple-based nested-loops join.
 * For each record of p1 (the outer input), all of p2 (the inner input)
 * is scanned and the join predicate is tested on every pair.
 * The planner uses it only when no index, hash or merge join applies,
 * which is the case when the join predicate is not an equality.
 */
public class NestedLoopsPlan implements Plan {
   public String description() { return "NestedLoopsJoin [" + pred + "]"; }
   public java.util.List<Plan> children() { return java.util.Arrays.asList(p1, p2); }
   private Plan p1, p2;
   private Predicate pred;
   private Schema schema = new Schema();

   /**
    * Creates a nested-loops join of the two inputs.
    * @param p1 the outer input
    * @param p2 the inner input, re-read once per outer record
    * @param pred the join predicate
    */
   public NestedLoopsPlan(Plan p1, Plan p2, Predicate pred) {
      this.p1 = p1;
      this.p2 = p2;
      this.pred = pred;
      schema.addAll(p1.schema());
      schema.addAll(p2.schema());
   }

   public Scan open() {
      Scan s1 = p1.open();
      Scan s2 = p2.open();
      return new NestedLoopsScan(s1, s2, pred);
   }

   /**
    * B(nestedloops(p1,p2)) = B(p1) + R(p1)*B(p2)
    * The inner scan is re-read once per outer record.
    */
   public int blocksAccessed() {
      return p1.blocksAccessed() + (p1.recordsOutput() * p2.blocksAccessed());
   }

   /**
    * R(nestedloops(p1,p2)) = R(p1)*R(p2) / reduction factor of the predicate
    */
   public int recordsOutput() {
      return (p1.recordsOutput() * p2.recordsOutput()) / pred.reductionFactor(this);
   }

   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   public Schema schema() {
      return schema;
   }
}
