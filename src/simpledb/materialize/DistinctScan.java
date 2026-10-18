package simpledb.materialize;

import java.util.*;
import simpledb.query.*;

/** Requires input sorted by every output field. */
public class DistinctScan implements Scan {
   private final Scan input;
   private final List<String> fields;
   private List<Constant> previous;
   public DistinctScan(Scan input, List<String> fields) {
      this.input = input;
      this.fields = new ArrayList<>(fields);
      beforeFirst();
   }
   public void beforeFirst() { input.beforeFirst(); previous = null; }
   public boolean next() {
      while (input.next()) {
         List<Constant> row = new ArrayList<>();
         for (String field : fields) row.add(input.getVal(field));
         if (!row.equals(previous)) { previous = row; return true; }
      }
      return false;
   }
   public Constant getVal(String field) { return input.getVal(field); }
   public int getInt(String field) { return input.getInt(field); }
   public String getString(String field) { return input.getString(field); }
   public boolean hasField(String field) { return input.hasField(field); }
   public void close() { input.close(); }
}
