package simpledb.plan;

import simpledb.query.Scan;
import simpledb.record.*;

/**
 * The interface implemented by each query plan.
 * There is a Plan class for each relational algebra operator.
 * @author Edward Sciore
 *
 */
public interface Plan {
   default String description() { return getClass().getSimpleName(); }
   default java.util.List<Plan> children() { return java.util.Collections.emptyList(); }
   default String explain() {
      StringBuilder text = new StringBuilder();
      appendExplanation(text, "");
      return text.toString();
   }
   default void appendExplanation(StringBuilder text, String indent) {
      text.append(indent).append(description()).append(" [estimated rows=")
          .append(recordsOutput()).append(", estimated blocks=")
          .append(blocksAccessed()).append("]\n");
      for (Plan child : children()) child.appendExplanation(text, indent + "  ");
   }
   
   /**
    * Opens a scan corresponding to this plan.
    * The scan will be positioned before its first record.
    * @return a scan
    */
   public Scan   open();
   
   /**
    * Returns an estimate of the number of block accesses
    * that will occur when the scan is read to completion.
    * @return the estimated number of block accesses
    */
   public int    blocksAccessed();
   
   /**
    * Returns an estimate of the number of records
    * in the query's output table.
    * @return the estimated number of output records
    */
   public int    recordsOutput();
   
   /**
    * Returns an estimate of the number of distinct values
    * for the specified field in the query's output table.
    * @param fldname the name of a field
    * @return the estimated number of distinct field values in the output
    */
   public int    distinctValues(String fldname);
   
   /**
    * Returns the schema of the query.
    * @return the query's schema
    */
   public Schema schema();
}
