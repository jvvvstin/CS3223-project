package simpledb.query;

public class NestedLoopsScan implements Scan {
    private Scan s1, s2;
    private Predicate joinpred;

    public NestedLoopsScan(Scan s1, Scan s2, Predicate joinpred) {
        this.s1 = s1;
        this.s2 = s2;
        this.joinpred = joinpred;
        beforeFirst();
    }

    public void beforeFirst() {
        s1.beforeFirst();
        s1.next();
        s2.beforeFirst();
    }

    /**
     * Move the scan to the next record satisfying the join predicate.
     */
    public boolean next() {
        while (true) {
            // Try the next record from the RHS
            if (s2.next()) {
                if (joinpred.isSatisfied(this)) {
                    return true;
                }
            } else {
                // RHS exhausted, so move to the next LHS record.
                if (!s1.next()) {
                    return false;
                }

                // Restart RHS from the beginning
                s2.beforeFirst();
            }
        }
    }

    public int getInt(String fldname) {
        if (s1.hasField(fldname)) {
            return s1.getInt(fldname);
        } else  {
            return s2.getInt(fldname);
        }
    }

    public String getString(String fldname) {
        if (s1.hasField(fldname)) {
            return s1.getString(fldname);
        }  else  {
            return s2.getString(fldname);
        }
    }

    public Constant getVal(String fldname) {
        if (s1.hasField(fldname)) {
            return s1.getVal(fldname);
        }  else  {
            return s2.getVal(fldname);
        }
    }

    public boolean hasField(String fldname) {
        return s1.hasField(fldname) || s2.hasField(fldname);
    }

    public void close() {
        s1.close();
        s2.close();
    }
}
