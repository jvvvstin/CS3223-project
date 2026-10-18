package simpledb.plan;

import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.parse.*;
import simpledb.record.Schema;

/**
 * The object that executes SQL statements.
 * @author Edward Sciore
 */
public class Planner {
   /** Build and describe the plan without opening any execution scans. */
   public String explainQuery(String sql, Transaction tx) {
      QueryData data = new Parser(sql).explainQuery();
      verifyQuery(data, tx);
      return qplanner.createPlan(data, tx).explain();
   }
   private QueryPlanner qplanner;
   private UpdatePlanner uplanner;
   private MetadataMgr mdm;
   
   public Planner(QueryPlanner qplanner, UpdatePlanner uplanner, MetadataMgr mdm) {
      this.qplanner = qplanner;
      this.uplanner = uplanner;
      this.mdm = mdm;
   }
   
   /**
    * Creates a plan for an SQL select statement, using the supplied planner.
    * @param qry the SQL query string
    * @param tx the transaction
    * @return the scan corresponding to the query plan
    */
   public Plan createQueryPlan(String qry, Transaction tx) {
      Parser parser = new Parser(qry);
      QueryData data = parser.query();
      verifyQuery(data, tx);
      return qplanner.createPlan(data, tx);
   }
   
   /**
    * Executes an SQL insert, delete, modify, or
    * create statement.
    * The method dispatches to the appropriate method of the
    * supplied update planner,
    * depending on what the parser returns.
    * @param cmd the SQL update string
    * @param tx the transaction
    * @return an integer denoting the number of affected records
    */
   public int executeUpdate(String cmd, Transaction tx) {
      Parser parser = new Parser(cmd);
      Object data = parser.updateCmd();
      verifyUpdate(data, tx);
      if (data instanceof InsertData)
         return uplanner.executeInsert((InsertData)data, tx);
      else if (data instanceof DeleteData)
         return uplanner.executeDelete((DeleteData)data, tx);
      else if (data instanceof ModifyData)
         return uplanner.executeModify((ModifyData)data, tx);
      else if (data instanceof CreateTableData)
         return uplanner.executeCreateTable((CreateTableData)data, tx);
      else if (data instanceof CreateViewData)
         return uplanner.executeCreateView((CreateViewData)data, tx);
      else if (data instanceof CreateIndexData)
         return uplanner.executeCreateIndex((CreateIndexData)data, tx);
      else
         return 0;
   }
 
   /**
    * Verifies that every table named in the from clause exists.
    * A name that denotes a view is accepted here; the tables it
    * mentions are verified when the view definition is planned.
    * Field names are verified later, against the schema of the
    * plan that is constructed from these tables.
    * @param data the parsed query
    * @param tx the transaction
    */
   private void verifyQuery(QueryData data, Transaction tx) {
      for (String tblname : data.tables())
         if (mdm.getViewDef(tblname, tx) == null)
            tableSchema(tblname, tx);
   }

   /**
    * Verifies that the table an update statement names exists,
    * and that every field it names belongs to that table.
    * A create table or create view statement names nothing that
    * has to exist already, and so is not verified.
    * @param data the parsed update statement
    * @param tx the transaction
    */
   private void verifyUpdate(Object data, Transaction tx) {
      if (data instanceof InsertData) {
         InsertData insdata = (InsertData) data;
         Schema sch = tableSchema(insdata.tableName(), tx);
         for (String fldname : insdata.fields())
            verifyField(sch, insdata.tableName(), fldname);
         if (insdata.fields().size() != insdata.vals().size())
            throw new BadSyntaxException("Value count does not match field count");
      }
      else if (data instanceof DeleteData)
         tableSchema(((DeleteData) data).tableName(), tx);
      else if (data instanceof ModifyData) {
         ModifyData moddata = (ModifyData) data;
         Schema sch = tableSchema(moddata.tableName(), tx);
         verifyField(sch, moddata.tableName(), moddata.targetField());
      }
      else if (data instanceof CreateIndexData) {
         CreateIndexData idxdata = (CreateIndexData) data;
         Schema sch = tableSchema(idxdata.tableName(), tx);
         verifyField(sch, idxdata.tableName(), idxdata.fieldName());
      }
   }

   /**
    * Returns the schema of the specified table.
    * The metadata manager returns an empty schema for a table
    * that was never created, which is how a missing table is
    * detected here instead of in the record manager.
    * @param tblname the name of the table
    * @param tx the transaction
    * @return the table's schema
    */
   private Schema tableSchema(String tblname, Transaction tx) {
      Schema sch = mdm.getLayout(tblname, tx).schema();
      if (sch.fields().isEmpty())
         throw new BadSyntaxException("Unknown table: " + tblname);
      return sch;
   }

   private void verifyField(Schema sch, String tblname, String fldname) {
      if (!sch.hasField(fldname))
         throw new BadSyntaxException("Unknown field in table " + tblname + ": " + fldname);
   }
}
