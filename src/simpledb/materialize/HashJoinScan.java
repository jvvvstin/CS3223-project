package simpledb.materialize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.query.UpdateScan;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

public class HashJoinScan implements Scan {
    private Transaction tx;
    private Scan s1, s2;
    private Schema sch1, sch2, sch;
    private String fldname1, fldname2;

    private TempTable result;
    private UpdateScan resultScan;

    public HashJoinScan(Transaction tx, Scan s1, Scan s2, Schema sch1, Schema sch2, String fldname1, String fldname2) {
        this.tx = tx;
        this.s1 = s1;
        this.s2 = s2;
        this.sch1 = sch1;
        this.sch2 = sch2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;

        sch = new Schema();
        sch.addAll(sch1);
        sch.addAll(sch2);

        result = new TempTable(tx, sch);
        resultScan = result.open();

        executeJoin();
        resultScan.close();

        resultScan = result.open();
        beforeFirst();
    }

    private void executeJoin() {
        int numPartitions = Math.max(1, tx.availableBuffs() - 1);

        List<TempTable> parts1 = new ArrayList<>();
        List<TempTable> parts2 = new ArrayList<>();

        for (int i = 0; i < numPartitions; i++) {
            parts1.add(new TempTable(tx, sch1));
            parts2.add(new TempTable(tx, sch2));
        }

        partition(s1, sch1, fldname1, parts1, numPartitions);
        partition(s2, sch2, fldname2, parts2, numPartitions);

        s1.close();
        s2.close();

        for (int i = 0; i < numPartitions; i++) {
            joinPartitions(parts1.get(i), parts2.get(i));
        }
    }

    private void partition(Scan scan,
                           Schema schema,
                           String fldname,
                           List<TempTable> partitions,
                           int numPartitions) {

        List<UpdateScan> scans = new ArrayList<>();

        for (TempTable table : partitions)
            scans.add(table.open());

        scan.beforeFirst();

        while (scan.next()) {
            Constant val = scan.getVal(fldname);

            int partition = Math.floorMod(
                    val.hashCode(),
                    numPartitions
            );

            UpdateScan dest = scans.get(partition);
            dest.insert();

            for (String fld : schema.fields())
                dest.setVal(fld, scan.getVal(fld));
        }

        for (UpdateScan us : scans)
            us.close();
    }

    private void joinPartitions(TempTable part1, TempTable part2) {
        Map<Constant, List<Map<String, Constant>>> hashTable =
                new HashMap<>();

        Scan buildScan = part1.open();

        while (buildScan.next()) {
            Constant key = buildScan.getVal(fldname1);

            Map<String, Constant> record = new HashMap<>();

            for (String fld : sch1.fields())
                record.put(fld, buildScan.getVal(fld));

            hashTable
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(record);
        }

        buildScan.close();

        Scan probeScan = part2.open();

        while (probeScan.next()) {
            Constant key = probeScan.getVal(fldname2);

            List<Map<String, Constant>> matches = hashTable.get(key);

            if (matches == null)
                continue;

            for (Map<String, Constant> record : matches) {
                resultScan.insert();

                for (String fld : sch1.fields())
                    resultScan.setVal(fld, record.get(fld));

                for (String fld : sch2.fields())
                    resultScan.setVal(fld, probeScan.getVal(fld));
            }
        }

        probeScan.close();
    }

    public void beforeFirst() {
        resultScan.beforeFirst();
    }

    public boolean next() {
        return resultScan.next();
    }

    public int getInt(String fldname) {
        return resultScan.getInt(fldname);
    }

    public String getString(String fldname) {
        return resultScan.getString(fldname);
    }

    public Constant getVal(String fldname) {
        return resultScan.getVal(fldname);
    }

    public boolean hasField(String fldname) {
        return resultScan.hasField(fldname);
    }

    public void close() {
        resultScan.close();
    }
}
