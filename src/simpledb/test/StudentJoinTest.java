package simpledb.test;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.sql.Types;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/** Checks actual scan operators and result rows on a disposable studentdb copy. */
public class StudentJoinTest {
   private static SimpleDB db;
   private static Transaction tx;
   private static int passed;

   public static void main(String[] args) throws Exception {
      Path source = Paths.get(args.length == 0 ? "studentdb" : args[0]);
      Path copy = Files.createTempDirectory("student-join-test-").resolve("studentdb");
      Files.createDirectory(copy);
      try (Stream<Path> files = Files.list(source)) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (Files.isRegularFile(file)) Files.copy(file, copy.resolve(file.getFileName()));
      }
      db = new SimpleDB(copy.toString());
      tx = db.newTx();
      try {
         // Buffer-sized runs now make these tiny sorts tie with hash; ties choose merge.
         check("MergeJoinScan", "select title, prof from course, section where cid = courseid",
               "db systems|turing", "db systems|turing", "calculus|newton", "calculus|einstein", "elocution|brando");
         check("MergeJoinScan", "select title, prof from course, section where cid = courseid and deptid = 10 and yearoffered = 2019",
               "db systems|turing");
         check("MergeJoinScan", "select sname, eid from student, enroll where sid = studentid",
               "joe|14", "joe|24", "amy|34", "sue|44", "sue|54", "kim|64");
         check("MergeJoinScan", "select title, prof from course, section where cid = courseid and deptid = 30 and yearoffered = 2018",
               "elocution|brando");
         check("IndexJoinScan", "select sname, dname from student, dept where majorid = did",
               "joe|compsci", "max|compsci", "lee|compsci", "amy|math", "sue|math", "kim|math", "pat|math", "bob|drama", "art|drama");
         check("NestedLoopsScan", "select did, cid from dept, course where did < deptid",
               "10|32", "10|42", "10|52", "10|62", "20|52", "20|62");
         check("MergeJoinScan", "select title, prof from course, section where cid = courseid and cid = 999 and courseid = 999");
         System.out.println("PASS: " + passed + " join queries, actual scan types and duplicate-preserving results verified twice.");
      } finally { tx.rollback(); }
   }

   private static void check(String operator, String sql, String... expected) throws Exception {
      System.out.println("SQL: " + sql);
      Plan p = db.planner().createQueryPlan(sql, tx);
      int available = tx.availableBuffs();
      Scan s = p.open();
      try {
         Set<String> operators = new HashSet<>();
         collectScans(s, operators);
         if (!operators.contains(operator)) throw new AssertionError("Expected " + operator + ", actual " + operators);
         List<String> want = Arrays.asList(expected);
         Collections.sort(want);
         for (int pass = 0; pass < 2; pass++) {
            s.beforeFirst();
            List<String> rows = new ArrayList<>();
            while (s.next()) {
               List<String> cells = new ArrayList<>();
               for (String f : p.schema().fields())
                  cells.add(p.schema().type(f) == Types.INTEGER ? Integer.toString(s.getInt(f)) : s.getString(f));
               rows.add(String.join("|", cells));
            }
            Collections.sort(rows);
            if (!rows.equals(want)) throw new AssertionError("Expected " + want + ", actual " + rows);
         }
         System.out.println("Verified actual " + operator + ": " + want);
      } finally { s.close(); }
      if (tx.availableBuffs() != available) throw new AssertionError("Join leaked pinned buffers");
      passed++;
   }

   // Test-only introspection: inspect the opened iterator, not planner log messages.
   private static void collectScans(Scan scan, Set<String> result) throws Exception {
      result.add(scan.getClass().getSimpleName());
      for (Field field : scan.getClass().getDeclaredFields()) {
         if (Scan.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true);
            Scan child = (Scan) field.get(scan);
            if (child != null) collectScans(child, result);
         }
      }
   }
}
