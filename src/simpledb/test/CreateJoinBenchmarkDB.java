package simpledb.test;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.index.Index;
import simpledb.query.Constant;

/** Creates only a NEW database; never modifies studentdb or an existing directory. */
public class CreateJoinBenchmarkDB {
   public static void main(String[] args) throws Exception {
      if (args.length != 6)
         throw new IllegalArgumentException("Args: newDirectory students enrollments sections courses seed");
      Path directory = Paths.get(args[0]).toAbsolutePath();
      int students = Integer.parseInt(args[1]), enrollments = Integer.parseInt(args[2]);
      int sections = Integer.parseInt(args[3]), courses = Integer.parseInt(args[4]);
      long seed = Long.parseLong(args[5]);
      if (students < 1 || enrollments < 1 || sections < 1 || courses < 1)
         throw new IllegalArgumentException("Counts must be positive");
      if (Files.exists(directory)) throw new IllegalArgumentException("Destination already exists: " + directory);
      Files.createDirectories(directory.getParent());
      SimpleDB db = new SimpleDB(directory.toString());
      Transaction tx = db.newTx();
      boolean committed = false;
      try {
         String[] ddl = {
            "create table bench_student(sid int, sname varchar(10), majorid int, gradyear int)",
            "create table bench_enroll(eid int, studentid int, sectionid int, grade varchar(2))",
            "create table bench_section(sectid int, courseid int, prof varchar(8), yearoffered int)",
            "create table bench_course(cid int, title varchar(20), deptid int)",
            "create table bench_dept(did int, dname varchar(8))",
            "create index bench_sididx on bench_enroll(studentid)"};
         for (String sql : ddl) db.planner().executeUpdate(sql, tx);
         Random random = new Random(seed);
         for (String table : Arrays.asList("bench_student","bench_enroll","bench_section","bench_course","bench_dept")) {
            int count = table.equals("bench_student") ? students : table.equals("bench_enroll") ? enrollments
                  : table.equals("bench_section") ? sections : table.equals("bench_course") ? courses : 10;
            List<Integer> ids = new ArrayList<>();
            for (int id = 1; id <= count; id++) ids.add(id);
            Collections.shuffle(ids, random); // Avoid giving merge join presorted input.
            TableScan scan = new TableScan(tx, table, db.mdMgr().getLayout(table, tx));
            Index index = table.equals("bench_enroll")
                  ? db.mdMgr().getIndexInfo(table, tx).get("studentid").open() : null;
            try {
               for (int id : ids) {
                  scan.insert();
                  if (table.equals("bench_student")) {
                     scan.setInt("sid", id); scan.setString("sname", "s" + id);
                     scan.setInt("majorid", 1 + (id-1)%10); scan.setInt("gradyear", 2020 + id%5);
                  } else if (table.equals("bench_enroll")) {
                     int student = 1 + (id-1)%students;
                     scan.setInt("eid", id); scan.setInt("studentid", student);
                     scan.setInt("sectionid", 1 + (id-1)%sections);
                     scan.setString("grade", new String[]{"A","B+","B","C"}[id%4]);
                     index.insert(new Constant(student), scan.getRid());
                  } else if (table.equals("bench_section")) {
                     scan.setInt("sectid", id); scan.setInt("courseid", 1+(id-1)%courses);
                     scan.setString("prof", "p"+id%30); scan.setInt("yearoffered", 2020+id%5);
                  } else if (table.equals("bench_course")) {
                     scan.setInt("cid", id); scan.setString("title", "course"+id);
                     scan.setInt("deptid", 1+(id-1)%10);
                  } else {
                     scan.setInt("did", id); scan.setString("dname", "dept"+id);
                  }
               }
            } finally { if (index != null) index.close(); scan.close(); }
            System.out.println(table + ": inserted " + count + " rows");
         }
         tx.commit(); committed = true;
      } finally {
         try { if (!committed) tx.rollback(); } finally { db.fileMgr().close(); }
      }
      Files.write(directory.resolve("benchmark-data.txt"),
            ("seed="+seed+"\nstudents="+students+"\nenrollments="+enrollments
             +"\nsections="+sections+"\ncourses="+courses
             +"\nprefix=bench_\nphysical_order=seeded_shuffle\n").getBytes(StandardCharsets.UTF_8));
      System.out.println("Created isolated benchmark database: " + directory);
   }
}
