package simpledb.opt;

/** Explicit method restrictions for repeatable join experiments. */
public enum JoinMethod {
   NESTED_LOOP, HASH, MERGE, INDEX, MULTIBUFFER
}
