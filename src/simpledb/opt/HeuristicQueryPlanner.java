package simpledb.opt;

import java.util.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.QueryData;
import simpledb.plan.*;
import simpledb.tx.Transaction;

/**
 * A query planner that optimizes using a heuristic-based algorithm.
 * @author Edward Sciore
 */
public class HeuristicQueryPlanner implements QueryPlanner {
   private Collection<TablePlanner> tableplanners = new ArrayList<>();
   private MetadataMgr mdm;
   
   public HeuristicQueryPlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Creates an optimized left-deep query plan using the following
    * heuristics.
    * H1. Choose the smallest table (considering selection predicates)
    * to be first in the join order.
    * H2. Add the table to the join order which
    * results in the smallest output.
    * When a table can be joined to the current plan, the join method
    * (index, hash or merge join) is chosen by its TablePlanner. A table
    * with no applicable join predicate is added as a product.
    * Once all tables have been placed, the joined plan is handed to
    * QueryFinisher, which adds the remaining operators required by the query.
    * @param data the parsed query
    * @param tx the transaction executing the query
    * @return the query plan
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      
      // Step 1:  Create a TablePlanner object for each mentioned table
      for (String tblname : data.tables()) {
         TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm);
         tableplanners.add(tp);
      }
      
      // Step 2:  Choose the lowest-size plan to begin the join order
      Plan currentplan = getLowestSelectPlan();
      
      // Step 3:  Repeatedly add a plan to the join order
      while (!tableplanners.isEmpty()) {
         Plan p = getLowestJoinPlan(currentplan);
         if (p != null)
            currentplan = p;
         else  // no applicable join
            currentplan = getLowestProductPlan(currentplan);
      }


      return QueryFinisher.finish(currentplan, data, tx);
   }
   
   /**
    * Chooses the table that produces the fewest records after applying
    * its selection predicates, to start the join order. The chosen table
    * is removed from the list of tables still to be placed.
    * @return the select plan for the chosen table
    */
   private Plan getLowestSelectPlan() {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeSelectPlan();
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }
   
   /**
    * Finds, among the remaining tables, the one whose join with the
    * current plan produces the fewest records. Tables with no applicable
    * join predicate are ignored. The chosen table is removed from the list
    * of tables still to be placed.
    * @param current the plan built so far
    * @return the best join plan, or null if no remaining table can be joined
    */
   private Plan getLowestJoinPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeJoinPlan(current);
         if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
            besttp = tp;
            bestplan = plan;
         }
      }
      if (bestplan != null)
         tableplanners.remove(besttp);
      return bestplan;
   }
   
   /**
    * Finds, among the remaining tables, the one whose product with the
    * current plan produces the fewest records. This is used when no
    * remaining table can be joined. The chosen table is removed from the
    * list of tables still to be placed.
    * @param current the plan built so far
    * @return the best product plan
    */
   private Plan getLowestProductPlan(Plan current) {
      TablePlanner besttp = null;
      Plan bestplan = null;
      for (TablePlanner tp : tableplanners) {
         Plan plan = tp.makeProductPlan(current);
         if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
            besttp = tp;
            bestplan = plan;
         }
      }
      tableplanners.remove(besttp);
      return bestplan;
   }

   /**
    * Does nothing. It exists so that this planner satisfies the
    * QueryPlanner interface, and would be used to plan views, which this
    * planner does not do.
    * @param p the planner that would be used for view definitions
    */
   public void setPlanner(Planner p) {
      // for use in planning views, which
      // for simplicity this code doesn't do.
   }
}
