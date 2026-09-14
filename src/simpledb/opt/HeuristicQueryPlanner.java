package simpledb.opt;

import java.util.*;
import simpledb.materialize.*;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.AggregateFnData;
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


      Plan p = currentplan;

      if (data.hasGroupBy() || data.hasAggregates()) {
          List<AggregationFn> aggfns = makeAggFns(data.aggregates());

          p = new GroupByPlan(tx, p, data.groupFields(), aggfns);
      }

      // Step 5.  Add a sort plan, if there is an order by clause
      if (data.hasSortFields())
          p = new SortPlan(tx, p, data.sortFields());

      // Step 6.  Project on the field names and return
      p = new ProjectPlan(p, data.fields());

      return p;
   }
   
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

   private List<AggregationFn> makeAggFns(List<AggregateFnData> aggdata) {
       List<AggregationFn> aggfns = new ArrayList<>();

       for (AggregateFnData agg : aggdata) {
           String fn = agg.function();
           String fldname = agg.fieldName();

           switch (fn) {
               case "count":
                   aggfns.add(new CountFn(fldname));
                   break;

               case "sum":
                   aggfns.add(new SumFn(fldname));
                   break;

               case "avg":
                   aggfns.add(new AvgFn(fldname));
                   break;

               case "min":
                   aggfns.add(new MinFn(fldname));
                   break;

               case "max":
                   aggfns.add(new MaxFn(fldname));
                   break;

               default:
                   throw new RuntimeException("Unknown aggregate function: " + fn);
           }
       }

       return aggfns;
   }

   public void setPlanner(Planner p) {
      // for use in planning views, which
      // for simplicity this code doesn't do.
   }
}
