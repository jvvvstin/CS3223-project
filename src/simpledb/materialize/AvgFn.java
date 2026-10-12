package simpledb.materialize;

import simpledb.query.*;

public class AvgFn implements AggregationFn {
   private String fldname;
   private long sum;
   private int count;

   public AvgFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      sum = s.getVal(fldname).asInt();
      count = 1;
   }

   public void processNext(Scan s) {
      sum += s.getVal(fldname).asInt();
      count++;
   }

   public String fieldName() {
      return "avgof" + fldname;
   }

   public Constant value() {
      return new Constant((int) (sum / count));
   }
}