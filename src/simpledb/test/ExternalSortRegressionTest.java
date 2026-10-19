package simpledb.test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import simpledb.materialize.*;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/** Isolated multi-pass sorting, k-way replay and duplicate-join regressions. */
public class ExternalSortRegressionTest {
   public static void main(String[] args) throws Exception {
      Path dir=Files.createTempDirectory("external-sort-regression-");
      SimpleDB db=new SimpleDB(dir.toString(),400,8);
      Transaction tx=db.newTx();
      try {
         int buffers=tx.availableBuffs();
         Plan input=input(tx,"k","id",4000,100);
         SortPlan sorted=new SortPlan(tx,input,Arrays.asList(new OrderBy("k",false),new OrderBy("id")));
         // 33 records/block, 7 blocks/run: 18 initial runs, one 7-way pass.
         if (sorted.blocksAccessed()!=610) throw new AssertionError("Sort cost: "+sorted.blocksAccessed());
         long before=files(dir);
         Scan scan=sorted.open();
         try {
            for (int pass=0; pass<2; pass++) {
               scan.beforeFirst();
               int count=0, prevKey=100, prevId=-1;
               while (scan.next()) {
                  int k=scan.getInt("k"), id=scan.getInt("id");
                  if (k>prevKey || (k==prevKey && id<=prevId)) throw new AssertionError("Sort order");
                  prevKey=k; prevId=id; count++;
               }
               if (count!=4000 || scan.next()) throw new AssertionError("Sort rows/EOF");
            }
         } finally { scan.close(); }
         long created=files(dir)-before;
         if (created>25) throw new AssertionError("Too many sort runs: "+created);
         System.out.println("4000-row sort: "+created+" temporary files (bounded-run regression).");
         replay(tx);
         Plan left=input(tx,"lk","li",600,100), right=input(tx,"rk","ri",1200,100);
         Plan join=new MergeJoinPlan(tx,left,right,"lk","rk");
         checkJoin(join);
         checkJoin(new HashJoinPlan(tx,left,right,"lk","rk"));
         Plan three=new MergeJoinPlan(tx,join,input(tx,"tk","ti",100,100),"lk","tk");
         checkJoin(new MergeJoinPlan(tx,three,input(tx,"uk","ui",100,100),"lk","uk"));
         Scan empty=new SortPlan(tx,input(tx,"e","ei",0,1),Arrays.asList(new OrderBy("e"))).open();
         try { if (empty.next()) throw new AssertionError("Empty sort"); empty.beforeFirst();
            if (empty.next()) throw new AssertionError("Empty rewind"); }
         finally { empty.close(); }
         if (tx.availableBuffs()!=buffers) throw new AssertionError("Buffer leak");
         System.out.println("PASS: multi-pass sorting, 4-way replay, costs, duplicate joins and empty input.");
      } finally { tx.rollback(); }
   }
   private static void checkJoin(Plan join) {
      Scan scan=join.open();
      try {
         for (int pass=0; pass<2; pass++) {
            scan.beforeFirst(); Set<String> pairs=new HashSet<>(); int count=0;
            while (scan.next()) {
               if (scan.getInt("lk")!=scan.getInt("rk")) throw new AssertionError("Unequal join keys");
               if (!pairs.add(scan.getInt("li")+":"+scan.getInt("ri"))) throw new AssertionError("Repeated pair");
               count++;
            }
            if (count!=7200) throw new AssertionError("Join count: "+count);
         }
      } finally { scan.close(); }
   }
   private static void replay(Transaction tx) {
      Schema sch=new Schema(); sch.addIntField("k");
      List<TempTable> runs=new ArrayList<>();
      for (int[] values:new int[][]{{1,3,7},{2,3,6},{3,5},{}}) {
         TempTable t=new TempTable(tx,sch); UpdateScan s=t.open();
         try { for (int v:values) { s.insert(); s.setInt("k",v); } }
         finally { s.close(); } runs.add(t);
      }
      SortScan scan=new SortScan(runs,new RecordComparator(Arrays.asList(new OrderBy("k"))));
      try {
         scan.next(); scan.next(); scan.savePosition();
         List<Integer> expected=Arrays.asList(3,3,3,5,6,7);
         for (int pass=0; pass<3; pass++) {
            if (scan.getInt("k")!=2) throw new AssertionError("Wrong restored run");
            List<Integer> tail=new ArrayList<>(); while(scan.next()) tail.add(scan.getInt("k"));
            if (!tail.equals(expected)) throw new AssertionError("Replay: "+tail);
            scan.restorePosition();
         }
      } finally { scan.close(); }
   }
   private static Plan input(Transaction tx,String key,String id,int count,int distinct) {
      Schema sch=new Schema(); sch.addIntField(key); sch.addIntField(id);
      TempTable table=new TempTable(tx,sch); UpdateScan scan=table.open();
      List<Integer> ids=new ArrayList<>(); for(int i=0;i<count;i++) ids.add(i);
      Collections.shuffle(ids,new Random(42));
      try { for(int i:ids) { scan.insert(); scan.setInt(key,i%distinct); scan.setInt(id,i); } }
      finally { scan.close(); }
      return new Plan() {
         public Scan open() { return table.open(); }
         public Schema schema() { return sch; }
         public int recordsOutput() { return count; }
         public int distinctValues(String f) { return f.equals(key)?Math.min(count,distinct):count; }
         public int blocksAccessed() { return (count+32)/33; }
      };
   }
   private static long files(Path dir) throws Exception {
      try(Stream<Path> files=Files.list(dir)) { return files.count(); }
   }
}
