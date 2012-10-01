/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.sql.optimizer.plan;

/** A statement that modifies the database.
 */
public class BaseUpdateStatement extends BasePlanWithInput
{
    public enum StatementType {
        DELETE,
        INSERT,
        UPDATE
    }
    
    private TableNode targetTable;
    private TableSource table;
    private final StatementType type;
    
    protected BaseUpdateStatement(PlanNode query, StatementType type, TableNode targetTable,
                                    TableSource table) {
        super(query);
        this.type = type;
        this.targetTable = targetTable;
        this.table = table;
    }

    public TableNode getTargetTable() {
        return targetTable;
    }


    public TableSource getTable() { 
        return table;
    }

    public StatementType getType() {
        return type;
    }

    @Override
    public String summaryString() {
        StringBuilder str = new StringBuilder(super.summaryString());
        str.append('(');
        fillSummaryString(str);
        //if (requireStepIsolation)
        //    str.append(", HALLOWEEN");
        str.append(')');
        return str.toString();
    }

    protected void fillSummaryString(StringBuilder str) {
        str.append(getTargetTable());
    }

    @Override
    protected void deepCopy(DuplicateMap map) {
        super.deepCopy(map);
        targetTable = map.duplicate(targetTable);
    }
}
