package simpledb.materialize;

import simpledb.query.*;

public class MinFn implements AggregationFn {
   private String fldname;
   private Constant val;

   public MinFn(String fldname) {
      this.fldname = fldname;
   }

   public void processFirst(Scan s) {
      val = s.getVal(fldname);
   }

   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) < 0)
         val = newval;
   }

   public String fieldName() {
      return "minof" + fldname;
   }

   public String sourceField() {
      return fldname;
   }

   public Constant value() {
      return val;
   }
}