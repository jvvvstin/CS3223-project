package simpledb.materialize;

import java.util.*;
import simpledb.plan.*;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

/** Sort the projected tuple, then discard adjacent duplicate tuples. */
public class DistinctPlan implements Plan {
   private final Plan sorted;
   public DistinctPlan(Transaction tx, Plan input) {
      this(tx, input, defaultKeys(input));
   }
   /** The supplied sort key must cover every output field for full-row deduplication. */
   public DistinctPlan(Transaction tx, Plan input, List<OrderBy> order) {
      sorted = new SortPlan(tx, input, new ArrayList<>(order));
   }
   private static List<OrderBy> defaultKeys(Plan input) {
      List<OrderBy> keys = new ArrayList<>();
      for (String field : input.schema().fields())
         keys.add(new OrderBy(field));
      return keys;
   }
   public Scan open() { return new DistinctScan(sorted.open(), schema().fields()); }
   public int blocksAccessed() { return sorted.blocksAccessed(); }
   public int recordsOutput() {
      long count = 1;
      for (String field : schema().fields())
         count = Math.min(sorted.recordsOutput(), count * Math.max(1, sorted.distinctValues(field)));
      return (int) Math.min(count, sorted.recordsOutput());
   }
   public int distinctValues(String field) { return Math.min(recordsOutput(), sorted.distinctValues(field)); }
   public Schema schema() { return sorted.schema(); }
   public String description() { return "Distinct " + schema().fields(); }
   public List<Plan> children() { return Collections.singletonList(sorted); }
}
