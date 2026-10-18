package simpledb.test;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.sql.Types;
import simpledb.plan.*;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/** Tests the CreateStudentDB dataset using an isolated copy of studentdb. */
public class StudentAggregateTest {
   private static Planner planner;
   private static Transaction tx;
   private static int passed;

   public static void main(String[] args) throws Exception {
      Path source = Paths.get(args.length == 0 ? "studentdb" : args[0]);
      Path copy = Files.createTempDirectory("student-aggregate-test-").resolve("studentdb");
      Files.createDirectory(copy);
      try (Stream<Path> files = Files.list(source)) {
         for (Path file : (Iterable<Path>) files::iterator)
            if (Files.isRegularFile(file))
               Files.copy(file, copy.resolve(file.getFileName()));
      }
      System.out.println("Testing database copy: " + copy);
      SimpleDB db = new SimpleDB(copy.toString());
      planner = db.planner();
      tx = db.newTx();
      try {
         check("select count(sid), sum(sid), avg(sid), min(sid), max(sid) from student", "9|45|5|1|9");
         check("select majorid, count(sid), sum(sid), avg(sid), min(sid), max(sid) from student group by majorid order by majorid", "10|3|13|4|1|9", "20|4|20|5|2|8", "30|2|12|6|5|7");
         check("select majorid, min(sname), max(sname) from student group by majorid order by majorid", "10|joe|max", "20|amy|sue", "30|art|bob");
         check("select gradyear, count(sid) from student group by gradyear order by gradyear", "2019|1", "2020|3", "2021|3", "2022|2");
         check("select majorid, gradyear, count(sid) from student group by majorid, gradyear order by majorid, gradyear", "10|2021|2", "10|2022|1", "20|2019|1", "20|2020|2", "20|2022|1", "30|2020|1", "30|2021|1");
         check("select majorid, count(sid), avg(gradyear) from student where gradyear > 2020 group by majorid order by majorid", "10|3|2021", "20|1|2022", "30|1|2021");
         check("select deptid, count(cid), min(title), max(title) from course group by deptid order by deptid", "10|2|compilers|db systems", "20|2|algebra|calculus", "30|2|acting|elocution");
         check("select prof, count(sectid), min(yearoffered), max(yearoffered) from section group by prof order by prof", "brando|1|2018|2018", "einstein|1|2017|2017", "newton|1|2019|2019", "turing|2|2018|2019");
         check("select studentid, count(eid) from enroll group by studentid order by studentid", "1|2", "2|1", "4|2", "6|1");
         check("select dname, count(sid), min(sname), max(sname) from student, dept where majorid = did group by dname order by dname", "compsci|3|joe|max", "drama|2|art|bob", "math|4|amy|sue");
         check("select sname, count(eid) from student, enroll where sid = studentid group by sname order by sname", "amy|1", "joe|2", "kim|1", "sue|2");
         check("select majorid, count(sid) from student group by majorid order by countofsid desc", "20|4", "10|3", "30|2");
         check("select count(sid) from student where sid = 999");
         check("select majorid, count(sid) from student where sid = 999 group by majorid");
         System.out.println("PASS: " + passed + " queries; each also verified after beforeFirst().");
      } finally { tx.rollback(); }
   }

   private static void check(String sql, String... expected) {
      Plan plan = planner.createQueryPlan(sql, tx);
      Scan scan = plan.open();
      try {
         for (int pass = 0; pass < 2; pass++) {
            scan.beforeFirst();
            List<String> actual = new ArrayList<>();
            while (scan.next()) {
               List<String> row = new ArrayList<>();
               for (String field : plan.schema().fields())
                  row.add(plan.schema().type(field) == Types.INTEGER
                        ? Integer.toString(scan.getInt(field)) : scan.getString(field));
               actual.add(String.join("|", row));
            }
            if (!actual.equals(Arrays.asList(expected)))
               throw new AssertionError(sql + "\nExpected CreateStudentDB data: " + Arrays.toString(expected) + "\nActual: " + actual);
            if (pass == 0) System.out.println(sql + "\n  " + actual);
         }
         passed++;
      } finally { scan.close(); }
   }
}
