package simpledb.query;

/**
 * The scan class corresponding to the nested-loops join
 * relational algebra operator.
 * Unlike ProductScan, the join predicate is evaluated inside the
 * scan, so no separate selection operator is needed above it.
 */
public class NestedLoopsScan implements Scan {
   private Scan s1, s2;
   private Predicate pred;

   public NestedLoopsScan(Scan s1, Scan s2, Predicate pred) {
      this.s1 = s1;
      this.s2 = s2;
      this.pred = pred;
      beforeFirst();
   }

   public void beforeFirst() {
      s1.beforeFirst();
      s1.next();
      s2.beforeFirst();
   }

   /**
    * Move to the next combination satisfying the join predicate.
    */
   public boolean next() {
      while (advance())
         if (pred.isSatisfied(this))
            return true;
      return false;
   }

   /** One step of the nested loop; identical to ProductScan.next(). */
   private boolean advance() {
      if (s2.next())
         return true;
      s2.beforeFirst();
      return s2.next() && s1.next();
   }

   public int getInt(String fldname) {
      if (s1.hasField(fldname)) return s1.getInt(fldname);
      else                      return s2.getInt(fldname);
   }

   public String getString(String fldname) {
      if (s1.hasField(fldname)) return s1.getString(fldname);
      else                      return s2.getString(fldname);
   }

   public Constant getVal(String fldname) {
      if (s1.hasField(fldname)) return s1.getVal(fldname);
      else                      return s2.getVal(fldname);
   }

   public boolean hasField(String fldname) {
      return s1.hasField(fldname) || s2.hasField(fldname);
   }

   public void close() {
      s1.close();
      s2.close();
   }
}
