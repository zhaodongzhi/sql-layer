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

package com.akiban.server.service.externaldata;

import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;

import java.util.ArrayList;
import java.util.List;

public abstract class GenericRowTracker implements RowTracker {
    private final List<RowType> openTypes = new ArrayList<>(3);
    private RowType curRowType;
    private int curDepth;

    protected void setDepth(int depth) {
        curDepth = depth;
    }

    @Override
    public void reset() {
        curRowType = null;
        curDepth = 0;
        openTypes.clear();
    }

    @Override
    public int getMinDepth() {
        return 0;
    }

    @Override
    public int getMaxDepth() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void beginRow(Row row) {
        curRowType = row.rowType();
    }

    @Override
    public int getRowDepth() {
        return curDepth;
    }

    @Override
    public boolean isSameRowType() {
        return (getRowDepth() < openTypes.size()) &&
               (curRowType == openTypes.get(getRowDepth()));
    }

    @Override
    public void pushRowType() {
        openTypes.add(curRowType);
    }
}
