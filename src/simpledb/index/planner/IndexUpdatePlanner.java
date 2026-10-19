package simpledb.index.planner;

import java.util.*;
import simpledb.index.Index;
import simpledb.metadata.*;
import simpledb.parse.*;
import simpledb.plan.*;
import simpledb.query.*;
import simpledb.record.*;
import simpledb.tx.Transaction;

/**
 * A modification of the basic update planner.
 * It dispatches each update statement to the corresponding
 * index planner.
 * @author Edward Sciore
 */
public class IndexUpdatePlanner implements UpdatePlanner {
   private MetadataMgr mdm;
   
   /**
    * Creates a new index update planner.
    * @param mdm the metadata manager, used to find tables and their indexes
    */
   public IndexUpdatePlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Executes an insert statement. The record is first inserted into the
    * table, then each specified field is set and, if that field has an
    * index, a matching index record is added.
    * @param data the parsed insert statement
    * @param tx the transaction executing the statement
    * @return the number of records inserted, which is always 1
    */
   public int executeInsert(InsertData data, Transaction tx) {
      String tblname = data.tableName();
      Plan p = new TablePlan(tx, tblname, mdm);
      
      // first, insert the record
      UpdateScan s = (UpdateScan) p.open();
      s.insert();
      RID rid = s.getRid();
      
      // then modify each field, inserting an index record if appropriate
      Map<String,IndexInfo> indexes = mdm.getIndexInfo(tblname, tx);
      Iterator<Constant> valIter = data.vals().iterator();
      for (String fldname : data.fields()) {
         Constant val = valIter.next();
         s.setVal(fldname, val);
         
         IndexInfo ii = indexes.get(fldname);
         if (ii != null) {
            Index idx = ii.open();
            idx.insert(val, rid);
            idx.close();
         }
      }
      s.close();
      return 1;
   }
   
   /**
    * Executes a delete statement. For each record satisfying the
    * statement's predicate, its entry is removed from every index on the
    * table, and then the record itself is deleted.
    * @param data the parsed delete statement
    * @param tx the transaction executing the statement
    * @return the number of records deleted
    */
   public int executeDelete(DeleteData data, Transaction tx) {
      String tblname = data.tableName();
      Plan p = new TablePlan(tx, tblname, mdm);
      p = new SelectPlan(p, data.pred());
      Map<String,IndexInfo> indexes = mdm.getIndexInfo(tblname, tx);
      
      UpdateScan s = (UpdateScan) p.open();
      int count = 0;
      while(s.next()) {
         // first, delete the record's RID from every index
         RID rid = s.getRid();
         for (String fldname : indexes.keySet()) {
            Constant val = s.getVal(fldname);
            Index idx = indexes.get(fldname).open();
            idx.delete(val, rid);
            idx.close();
         }
         // then delete the record
         s.delete();
         count++;
      }
      s.close();
      return count;
   }
   
   /**
    * Executes a modify (update) statement. For each record satisfying the
    * statement's predicate, the target field is set to its new value and,
    * if the target field has an index, the index entry is replaced
    * (old entry deleted, new entry inserted). Indexes on other fields are
    * not affected.
    * @param data the parsed modify statement
    * @param tx the transaction executing the statement
    * @return the number of records modified
    */
   public int executeModify(ModifyData data, Transaction tx) {
      String tblname = data.tableName();
      String fldname = data.targetField();
      Plan p = new TablePlan(tx, tblname, mdm);
      p = new SelectPlan(p, data.pred());
      
      IndexInfo ii = mdm.getIndexInfo(tblname, tx).get(fldname);
      Index idx = (ii == null) ? null : ii.open();
      
      UpdateScan s = (UpdateScan) p.open();
      int count = 0;
      while(s.next()) {
         // first, update the record
         Constant newval = data.newValue().evaluate(s);
         Constant oldval = s.getVal(fldname);
         s.setVal(data.targetField(), newval);
         
         // then update the appropriate index, if it exists
         if (idx != null) {
            RID rid = s.getRid();
            idx.delete(oldval, rid);
            idx.insert(newval, rid);
         }
         count++;
      }
      if (idx != null) idx.close();
      s.close();
      return count;
   }

   /**
    * Executes a create table statement by registering the new table and
    * its schema with the metadata manager.
    * @param data the parsed create table statement
    * @param tx the transaction executing the statement
    * @return 0, as no records are affected
    */
   public int executeCreateTable(CreateTableData data, Transaction tx) {
      mdm.createTable(data.tableName(), data.newSchema(), tx);
      return 0;
   }
   
   /**
    * Executes a create view statement by storing the view's name and its
    * definition (the query text) with the metadata manager.
    * The definition is regenerated from the parsed query, so queries that
    * use aggregates, group by or distinct are stored in that form.
    * @param data the parsed create view statement
    * @param tx the transaction executing the statement
    * @return 0, as no records are affected
    */
   public int executeCreateView(CreateViewData data, Transaction tx) {
      mdm.createView(data.viewName(), data.viewDef(), tx);
      return 0;
   }
   
   /**
    * Executes a create index statement by registering the index, with its
    * type (hash or btree), with the metadata manager.
    * @param data the parsed create index statement
    * @param tx the transaction executing the statement
    * @return 0, as no records are affected
    */
   public int executeCreateIndex(CreateIndexData data, Transaction tx) {
      mdm.createIndex(data.indexName(), data.tableName(), data.fieldName(), data.indexType(), tx);
      return 0;
   }
}
