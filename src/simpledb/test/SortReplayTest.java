package simpledb.test;

import java.nio.file.Files;
import java.util.*;
import simpledb.materialize.*;
import simpledb.query.UpdateScan;
import simpledb.query.OrderBy;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/** Regression for duplicate-key merge replay across two final sort runs. */
public class SortReplayTest {
   public static void main(String[] args) throws Exception {
      SimpleDB db = new SimpleDB(Files.createTempDirectory("sort-replay-").toString(), 400, 8);
      Transaction tx = db.newTx();
      try {
         Schema schema = new Schema();
         schema.addIntField("k");
         TempTable a = run(tx, schema, 1, 3, 5);
         TempTable b = run(tx, schema, 2, 3, 4);
         SortScan scan = new SortScan(Arrays.asList(a, b),
               new RecordComparator(Arrays.asList(new OrderBy("k", true))));
         try {
            scan.next(); scan.next(); // 2 comes from the second run.
            scan.savePosition();
            List<Integer> first = tail(scan);
            scan.restorePosition();
            if (scan.getInt("k") != 2 || !tail(scan).equals(first))
               throw new AssertionError("Replay after exhaustion changed sorted rows");
            scan.beforeFirst();
            while (scan.next() && scan.getInt("k") != 5) { }
            scan.savePosition(); // Second run already exhausted.
            scan.next(); scan.restorePosition();
            if (scan.getInt("k") != 5 || scan.next())
               throw new AssertionError("Exhausted-run state not restored");
         } finally { scan.close(); }
         System.out.println("PASS: sort replay restores run identity and exhaustion state.");
      } finally { tx.rollback(); db.fileMgr().close(); }
   }
   private static List<Integer> tail(SortScan scan) {
      List<Integer> result = new ArrayList<>();
      while (scan.next()) result.add(scan.getInt("k"));
      return result;
   }
   private static TempTable run(Transaction tx, Schema schema, int... keys) {
      TempTable table = new TempTable(tx, schema);
      UpdateScan scan = table.open();
      try { for (int key : keys) { scan.insert(); scan.setInt("k", key); } }
      finally { scan.close(); }
      return table;
   }
}
