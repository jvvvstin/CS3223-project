package simpledb.query;

/**
 * A class to contain the field in order by clause as well as whether its ascending
 */
public class OrderBy {
   private String fldname;
   private boolean asc;

   public OrderBy(String fldname, boolean asc) {
      this.fldname = fldname;
      this.asc = asc;
   }

   public String field()       { return fldname; }
   public boolean isAscending(){ return asc; }

   public String toString() {
      return fldname + (asc ? "" : " desc");
   }
}
