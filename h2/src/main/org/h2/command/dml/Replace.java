///*
// * Copyright 2004-2018 H2 Group. Multiple-Licensed under the MPL 2.0,
// * and the EPL 1.0 (http://h2database.com/html/license.html).
// * Initial Developer: H2 Group
// */
//package org.h2.command.dml;
//
//import java.util.ArrayList;
//
//import org.h2.api.ErrorCode;
//import org.h2.api.Trigger;
//import org.h2.command.Command;
//import org.h2.command.CommandInterface;
//import org.h2.command.Prepared;
//import org.h2.engine.Mode;
//import org.h2.engine.Right;
//import org.h2.engine.Session;
//import org.h2.engine.UndoLogRecord;
//import org.h2.expression.Expression;
//import org.h2.expression.Parameter;
//import org.h2.index.Index;
//import org.h2.message.DbException;
//import org.h2.result.ResultInterface;
//import org.h2.result.Row;
//import org.h2.table.Column;
//import org.h2.table.Table;
//import org.h2.util.StatementBuilder;
//import org.h2.util.Utils;
//import org.h2.value.Value;
//
///**
// * This class represents the MySQL-compatibility REPLACE statement
// */
////类似MERGE
//public class Replace extends Prepared {
//
//    private Table table;
//    private Column[] columns;
//    private Column[] keys;
//    private final ArrayList<Expression[]> list = Utils.newSmallArrayList();
//    private Query query;
//    private Prepared update;
//
//    public Replace(Session session) {
//        super(session);
//    }
//
//    @Override
//    public void setCommand(Command command) {
//        super.setCommand(command);
//        if (query != null) {
//            query.setCommand(command);
//        }
//    }
//
//    public void setTable(Table table) {
//        this.table = table;
//    }
//
//    public void setColumns(Column[] columns) {
//        this.columns = columns;
//    }
//
//    public void setKeys(Column[] keys) { //Parser中没有使用
//        this.keys = keys;
//    }
//
//    public void setQuery(Query query) {
//        this.query = query;
//    }
//
//    /**
//     * Add a row to this replace statement.
//     *
//     * @param expr the list of values
//     */
//    public void addRow(Expression[] expr) {
//        list.add(expr);
//    }
//
//    @Override
//    public int update() {
//        int count = 0;
//        session.getUser().checkRight(table, Right.INSERT);
//        session.getUser().checkRight(table, Right.UPDATE);
//        setCurrentRowNumber(0);
//        Mode mode = session.getDatabase().getMode();
//        if (!list.isEmpty()) {
//            for (int x = 0, size = list.size(); x < size; x++) {
//                setCurrentRowNumber(x + 1);
//                Expression[] expr = list.get(x);
//                Row newRow = table.getTemplateRow();
//                for (int i = 0, len = columns.length; i < len; i++) {
//                    Column c = columns[i];
//                    int index = c.getColumnId();
//                    Expression e = expr[i];
//                    if (e != null) {
//                        // e can be null (DEFAULT)
//                        try {
//                            Value v = c.convert(e.getValue(session), mode);
//                            newRow.setValue(index, v);
//                        } catch (DbException ex) {
//                            throw setRow(ex, count, getSQL(expr));
//                        }
//                    }
//                }
//                count += replace(newRow);
//            }
//        } else {
//            ResultInterface rows = query.query(0);
//            table.fire(session, Trigger.UPDATE | Trigger.INSERT, true);
//            table.lock(session, true, false);
//            while (rows.next()) {
//                Value[] r = rows.currentRow();
//                Row newRow = table.getTemplateRow();
//                setCurrentRowNumber(count);
//                for (int j = 0; j < columns.length; j++) {
//                    Column c = columns[j];
//                    int index = c.getColumnId();
//                    try {
//                        Value v = c.convert(r[j], mode);
//                        newRow.setValue(index, v);
//                    } catch (DbException ex) {
//                        throw setRow(ex, count, getSQL(r));
//                    }
//                }
//                count += replace(newRow);
//            }
//            rows.close();
//            table.fire(session, Trigger.UPDATE | Trigger.INSERT, false);
//        }
//        return count;
//    }
//
//    /**
//     * Updates an existing row or inserts a new one.
//     *
//     * @param row row to replace
//     * @return 1 if row was inserted, 2 if row was updated
//     */
//    private int replace(Row row) {
//        int count = update(row);
//        if (count == 0) {
//            try {
//                table.validateConvertUpdateSequence(session, row);
//                boolean done = table.fireBeforeRow(session, null, row);
//                if (!done) {
//                    table.lock(session, true, false);
//                    table.addRow(session, row);
//                    session.log(table, UndoLogRecord.INSERT, row);
//                    table.fireAfterRow(session, null, row, false);
//                }
//                return 1;
//            } catch (DbException e) {
//                if (e.getErrorCode() == ErrorCode.DUPLICATE_KEY_1) {
//                    // possibly a concurrent replace or insert
//                    Index index = (Index) e.getSource();
//                    if (index != null) {
//                        // verify the index columns match the key
//                        Column[] indexColumns = index.getColumns();
//                        boolean indexMatchesKeys = false;
//                        if (indexColumns.length <= keys.length) {
//                            for (int i = 0; i < indexColumns.length; i++) {
//                                if (indexColumns[i] != keys[i]) {
//                                    indexMatchesKeys = false;
//                                    break;
//                                }
//                            }
//                        }
//                        if (indexMatchesKeys) {
//                            throw DbException.get(ErrorCode.CONCURRENT_UPDATE_1, table.getName());
//                        }
//                    }
//                }
//                throw e;
//            }
//        } else if (count == 1) {
//            return 2;
//        }
//        throw DbException.get(ErrorCode.DUPLICATE_KEY_1, table.getSQL());
//    }
//
//    private int update(Row row) {
//        // if there is no valid primary key,
//        // the statement degenerates to an INSERT
//        if (update == null) {
//            return 0;
//        }
//        ArrayList<Parameter> k = update.getParameters();
//        for (int i = 0; i < columns.length; i++) {
//            Column col = columns[i];
//            Value v = row.getValue(col.getColumnId());
//            Parameter p = k.get(i);
//            p.setValue(v);
//        }
//        for (int i = 0; i < keys.length; i++) {
//            Column col = keys[i];
//            Value v = row.getValue(col.getColumnId());
//            if (v == null) {
//                throw DbException.get(ErrorCode.COLUMN_CONTAINS_NULL_VALUES_1, col.getSQL());
//            }
//            Parameter p = k.get(columns.length + i);
//            p.setValue(v);
//        }
//        return update.update();
//    }
//
//    @Override
//    public String getPlanSQL() {
//        StatementBuilder buff = new StatementBuilder("REPLACE INTO ");
//        buff.append(table.getSQL()).append('(');
//        for (Column c : columns) {
//            buff.appendExceptFirst(", ");
//            buff.append(c.getSQL());
//        }
//        buff.append(')');
//        buff.append('\n');
//        if (!list.isEmpty()) {
//            buff.append("VALUES ");
//            int row = 0;
//            for (Expression[] expr : list) {
//                if (row++ > 0) {
//                    buff.append(", ");
//                }
//                buff.append('(');
//                buff.resetCount();
//                for (Expression e : expr) {
//                    buff.appendExceptFirst(", ");
//                    if (e == null) {
//                        buff.append("DEFAULT");
//                    } else {
//                        buff.append(e.getSQL());
//                    }
//                }
//                buff.append(')');
//            }
//        } else {
//            buff.append(query.getPlanSQL());
//        }
//        return buff.toString();
//    }
//
//    @Override
//    public void prepare() {
//        if (columns == null) {
//            if (!list.isEmpty() && list.get(0).length == 0) {
//                // special case where table is used as a sequence
//                columns = new Column[0];
//            } else {
//                columns = table.getColumns();
//            }
//        }
//        if (!list.isEmpty()) {
//            for (Expression[] expr : list) {
//                if (expr.length != columns.length) {
//                    throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH);
//                }
//                for (int i = 0; i < expr.length; i++) {
//                    Expression e = expr[i];
//                    if (e != null) {
//                        expr[i] = e.optimize(session);
//                    }
//                }
//            }
//        } else {
//            query.prepare();
//            if (query.getColumnCount() != columns.length) {
//                throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH);
//            }
//        }
//        if (keys == null) {
//            Index idx = table.getPrimaryKey();
//            if (idx == null) {
//                throw DbException.get(ErrorCode.CONSTRAINT_NOT_FOUND_1, "PRIMARY KEY");
//            }
//            keys = idx.getColumns();
//        }
//        // if there is no valid primary key, the statement degenerates to an
//        // INSERT
//        for (Column key : keys) {
//            boolean found = false;
//            for (Column column : columns) {
//                if (column.getColumnId() == key.getColumnId()) {
//                    found = true;
//                    break;
//                }
//            }
//            if (!found) {
//                return;
//            }
//        }
//        StatementBuilder buff = new StatementBuilder("UPDATE ");
//        buff.append(table.getSQL()).append(" SET ");
//        for (Column c : columns) {
//            buff.appendExceptFirst(", ");
//            buff.append(c.getSQL()).append("=?");
//        }
//        buff.append(" WHERE ");
//        buff.resetCount();
//        for (Column c : keys) {
//            buff.appendExceptFirst(" AND ");
//            buff.append(c.getSQL()).append("=?");
//        }
//        String sql = buff.toString();
//        update = session.prepare(sql);
//    }
//
//    @Override
//    public boolean isTransactional() {
//        return true;
//    }
//
//    @Override
//    public ResultInterface queryMeta() {
//        return null;
//    }
//
//    @Override
//    public int getType() {
//        return CommandInterface.REPLACE;
//    }
//
//    @Override
//    public boolean isCacheable() {
//        return true;
//    }
//
//}
