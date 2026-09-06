package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * A test program to verify that order by is correctly
 * implemented in the query planner.
 */
public class OrderByTest {
   public static void main(String[] args) {
      SimpleDB db = new SimpleDB("studentdb");
      Transaction tx = db.newTx();
      Planner planner = db.planner();

      // Test 1: order by a single field, default (ascending) direction
      System.out.println("Query 1: order by SName (asc, default)");
      String qry1 = "select SId, SName, GradYear from student order by SName";
      runQuery(planner, tx, qry1);

      // Test 2: order by a single field, explicit descending direction
      System.out.println("\nQuery 2: order by GradYear desc");
      String qry2 = "select SId, SName, GradYear from student order by GradYear desc";
      runQuery(planner, tx, qry2);

      // Test 3: order by multiple fields with mixed directions
      System.out.println("\nQuery 3: order by GradYear asc, SName desc");
      String qry3 = "select SId, SName, GradYear from student order by GradYear asc, SName desc";
      runQuery(planner, tx, qry3);

      // Test 4: no order by clause at all -- should NOT add a SortPlan,
      // and rows should come back in unspecified/table order
      System.out.println("\nQuery 4: no order by clause");
      String qry4 = "select SId, SName, GradYear from student";
      runQuery(planner, tx, qry4);

      tx.commit();
   }

   private static void runQuery(Planner planner, Transaction tx, String qry) {
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      while (s.next()) {
         int id = s.getInt("sid");
         String name = s.getString("sname");
         int gradyear = s.getInt("gradyear");
         System.out.println(id + "\t" + name + "\t" + gradyear);
      }
      s.close();
   }
}