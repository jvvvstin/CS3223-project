package simpledb.test;
import simpledb.plan.Planner;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class ChangeMajor {
   public static void main(String[] args) {
	   try {
	         // analogous to the driver
	         SimpleDB db = new SimpleDB("studentdb");

	         // analogous to the connection
	         Transaction tx  = db.newTx();
	         Planner planner = db.planner();
	         
	         // analogous to the statement
	         String qry = "update STUDENT "
	               + "set MajorId=30 "
	               + "where SName = 'amy'";
	         planner.executeUpdate(qry, tx);
	         System.out.println("Amy is now a drama major.");
	         tx.commit();
	      }
	      catch(Exception e) {
	         e.printStackTrace();
	      }
   }
}
