package simpledb.test;
import java.util.Scanner;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class FindMajors {
	public static void main(String[] args) {
		System.out.print("Enter a department name: ");
	    Scanner sc = new Scanner(System.in);
	    String major = sc.next();
	    sc.close();
	    System.out.println("Here are the " + major + " majors");
	    System.out.println("Name\tGradYear");
	    
	    try {
	    	// analogous to the driver
	        SimpleDB db = new SimpleDB("studentdb");

	        // analogous to the connection
	        Transaction tx  = db.newTx();
	        Planner planner = db.planner();
	         
	        // analogous to the statement
	        String qry = "select sname, gradyear "
	              + "from student, dept "
	              + "where did = majorid "
	              + "and dname = '" + major + "'";
	        Plan p = planner.createQueryPlan(qry, tx);
	         
	        // analogous to the result set
	        Scan s = p.open();
	         
	        while (s.next()) {
	           String sname = s.getString("sname"); //SimpleDB stores field names
	           int gradyear = s.getInt("gradyear"); //in lower case
	           System.out.println(sname + "\t" + gradyear);
	        }
	        s.close();
	        tx.commit();
        }
	    catch(Exception e) {
	    	e.printStackTrace();
    	}
   }
}