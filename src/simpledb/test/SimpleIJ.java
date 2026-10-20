package simpledb.test;
import java.sql.*;
import java.util.Scanner;
import simpledb.jdbc.embedded.EmbeddedMetaData;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * A command-line SQL client that runs against an embedded studentdb.
 * It reads one statement per line: SELECT runs a query and prints the
 * result table, EXPLAIN prints the query plan without running it,
 * EXIT quits, and anything else is run as an update statement.
 * Each statement runs in its own transaction, which is rolled back
 * if the statement fails.
 */
public class SimpleIJ {
   public static void main(String[] args) {
      Scanner sc = new Scanner(System.in);
      
      // analogous to the driver
      SimpleDB db = new SimpleDB("studentdb");
      
      System.out.println("SQL> ");

      try {
         while (sc.hasNextLine()) {
            // process one line of input
            String cmd = sc.nextLine().trim();
            if (cmd.isEmpty()) continue;
            String keyword = cmd.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (keyword.equals("exit"))
               break;
            else if (keyword.equals("explain"))
               doExplain(db, cmd);
            else if (keyword.equals("select"))
               doQuery(db, cmd);
            else
               doUpdate(db, cmd);
            System.out.print("\nSQL> ");
         }
      }
      catch (Exception e) {
         e.printStackTrace();
      }
      sc.close();
   }

   /** Runs a query and prints its result as a table, one column per output field. */
   private static void doQuery(SimpleDB db, String cmd) {
      Transaction tx = db.newTx();
      Scan s = null;
	   try {
		    // analogous to the connection
	        Planner planner = db.planner();
	         
	        // analogous to the statement
	        Plan p = planner.createQueryPlan(cmd, tx);

	        // analogous to the result set
	        s = p.open();
	        Schema schema = p.schema();
	        EmbeddedMetaData md = new EmbeddedMetaData(schema);
	        int numcols = md.getColumnCount();
	        int totalwidth = 0;
	        
	        // print header
	        for (int i = 1; i <= numcols; i++) {
	        	String fldname = md.getColumnName(i);
	        	int width = md.getColumnDisplaySize(i);
	        	totalwidth += width;
	        	String fmt = "%" + width + "s";
	        	System.out.format(fmt, fldname);
	        }
	        System.out.println();
	        for (int i = 0; i < totalwidth; i++) {
	        	System.out.print("-");
	        }
	        System.out.println();
	        
	        // print records
			while(s.next()) {
				for (int i = 1; i <= numcols; i++) {
					String fldname = md.getColumnName(i);
					int fldtype = md.getColumnType(i);
					String fmt = "%" + md.getColumnDisplaySize(i);
					if (fldtype == Types.INTEGER) {
						int ival = s.getInt(fldname);
						System.out.format(fmt + "d", ival);
					}
					else {
						String sval = s.getString(fldname);
						System.out.format(fmt + "s", sval);
			        }
				}
				System.out.println();
			}
			s.close();
            s = null;
			tx.commit();
       }
	   catch(Exception e) {
		   if (s != null) s.close();
           tx.rollback();
           System.out.println("SQL Exception: " + e.getMessage());
	   }
   }

   /** Prints the plan tree for an EXPLAIN statement; the query itself is not run. */
   private static void doExplain(SimpleDB db, String cmd) {
      Transaction tx = db.newTx();
      try {
         System.out.print(db.planner().explainQuery(cmd, tx));
         tx.commit();
      } catch (Exception e) {
         tx.rollback();
         System.out.println("SQL Exception: " + e.getMessage());
      }
   }

   /** Runs an insert, delete, update or create statement and prints how many records it affected. */
   private static void doUpdate(SimpleDB db, String cmd) {
      Transaction tx = db.newTx();
      try {
	      Planner planner = db.planner();
	      int howmany = planner.executeUpdate(cmd, tx);
	      System.out.println(howmany + " records processed");
	      tx.commit();
      }
      catch (Exception e) {
         tx.rollback();
         System.out.println("SQL Exception: " + e.getMessage());
      }
   }
}
