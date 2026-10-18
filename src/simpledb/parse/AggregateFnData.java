package simpledb.parse;

public class AggregateFnData {
    private String fn;
    private String fldname;

    public  AggregateFnData(String fn, String fldname) {
        this.fn = fn;
        this.fldname = fldname;
    }

    public String function() {
        return fn;
    }

    public String fieldName() {
        return fldname;
    }

    public String outputFieldName() {
        return fn + "of" + fldname;
    }

    public String toString() {
        return fn.toUpperCase() + "(" + fldname + ")";
    }
}
