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
            if (cmd.startsWith("exit"))
               break;
            else if (cmd.startsWith("select"))
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

   private static void doQuery(SimpleDB db, String cmd) {
	   try {
		    // analogous to the connection
	        Transaction tx  = db.newTx();
	        Planner planner = db.planner();
	         
	        // analogous to the statement
	        Plan p = planner.createQueryPlan(cmd, tx);

	        // analogous to the result set
	        Scan s = p.open();
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
			tx.commit();
       }
	   catch(Exception e) {
		   e.printStackTrace();
	   }
   }

   private static void doUpdate(SimpleDB db, String cmd) {
      try {
	      Transaction tx  = db.newTx();
	      Planner planner = db.planner();
	      int howmany = planner.executeUpdate(cmd, tx);
	      System.out.println(howmany + " records processed");
	      tx.commit();
      }
      catch (Exception e) {
         System.out.println("SQL Exception: " + e.getMessage());
      }
   }
}