package simpledb.materialize;

import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * The <i>avg</i> aggregation function.
 * It applies only to integer fields, and its result is an integer:
 * the average rounded to the nearest whole number, since SimpleDB
 * has no decimal type. The running sum is a long so that summing
 * many large values cannot overflow before the division.
 */
public class AvgFn implements AggregationFn {
   public AggregationFn newInstance() { return new AvgFn(fldname); }
   public void addToSchema(Schema sch, Schema inputSchema) { sch.addIntField(fieldName()); }
   private String fldname;
   private long sum;
   private int count;

   /**
    * Create an average aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public AvgFn(String fldname) {
      this.fldname = fldname;
   }

   /**
    * Start a new sum and count from the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = s.getInt(fldname);
      count = 1;
   }

   /**
    * Add the current record to the sum and count.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getInt(fldname);
      count++;
   }

   /**
    * Return the field's name, prepended by "avgof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "avgof" + fldname;
   }

   /**
    * Return the sum divided by the count, rounded to the nearest integer.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return new Constant((int) Math.round((double) sum / count));
   }
}
