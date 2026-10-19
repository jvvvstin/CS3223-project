package simpledb.plan;

import java.util.Iterator;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.tx.Transaction;

/**
 * The basic planner for SQL update statements.
 * @author sciore
 */
public class BasicUpdatePlanner implements UpdatePlanner {
   private MetadataMgr mdm;
   
   /**
    * Creates a new basic update planner.
    * @param mdm the metadata manager, used to find tables
    */
   public BasicUpdatePlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   /**
    * Executes a delete statement by deleting every record of the table
    * that satisfies the statement's predicate.
    * @param data the parsed delete statement
    * @param tx the transaction executing the statement
    * @return the number of records deleted
    */
   public int executeDelete(DeleteData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      p = new SelectPlan(p, data.pred());
      UpdateScan us = (UpdateScan) p.open();
      int count = 0;
      while(us.next()) {
         us.delete();
         count++;
      }
      us.close();
      return count;
   }
   
   /**
    * Executes a modify (update) statement. For every record satisfying the
    * statement's predicate, the target field is set to the value of the
    * new-value expression, evaluated against that record.
    * @param data the parsed modify statement
    * @param tx the transaction executing the statement
    * @return the number of records modified
    */
   public int executeModify(ModifyData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      p = new SelectPlan(p, data.pred());
      UpdateScan us = (UpdateScan) p.open();
      int count = 0;
      while(us.next()) {
         Constant val = data.newValue().evaluate(us);
         us.setVal(data.targetField(), val);
         count++;
      }
      us.close();
      return count;
   }
   
   /**
    * Executes an insert statement by inserting a new record and setting
    * each specified field to its corresponding value.
    * @param data the parsed insert statement
    * @param tx the transaction executing the statement
    * @return the number of records inserted, which is always 1
    */
   public int executeInsert(InsertData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      UpdateScan us = (UpdateScan) p.open();
      us.insert();
      Iterator<Constant> iter = data.vals().iterator();
      for (String fldname : data.fields()) {
         Constant val = iter.next();
         us.setVal(fldname, val);
      }
      us.close();
      return 1;
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
    * definition with the metadata manager.
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
