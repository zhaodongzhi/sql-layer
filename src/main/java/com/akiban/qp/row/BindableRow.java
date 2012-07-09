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

package com.akiban.qp.row;

import com.akiban.qp.expression.ExpressionRow;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.expression.Expression;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.texpressions.TEvaluatableExpression;
import com.akiban.server.types3.texpressions.TPreparedExpression;
import com.akiban.util.ArgumentValidation;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class BindableRow {

    // BindableRow class interface

    public static BindableRow of(RowType rowType, List<? extends Expression> expressions) {
        return of(rowType,  expressions, null);
    }

    public static BindableRow of(RowType rowType, List<? extends Expression> expressions, List<? extends TPreparedExpression> pExpressions) {
        Iterator<? extends ValueSource> oldVals;
        Iterator<? extends PValueSource> newVals;
        if (pExpressions != null) {
            assert expressions == null : "can't have both style of expressions";
            ArgumentValidation.isEQ("rowType fields", rowType.nFields(), "expressions.size", pExpressions.size());
            // TODO build constants
//        for (TPreparedExpression expression : expressions) {
//            if (!expression.isConstant())
//                return new BindingExpressions(rowType, expressions);
//        }
            // all expressions are const; put them into a ImmutableRow
            newVals = new PExpressionEvaluator(pExpressions);
            oldVals = null;
        }
        else if (expressions != null) {
            ArgumentValidation.isEQ("rowType fields", rowType.nFields(), "expressions.size", expressions.size());
            for (Expression expression : expressions) {
                if (!expression.isConstant())
                    return new BindingExpressions(rowType, expressions);
            }
            // all expressions are const; put them into a ImmutableRow
            newVals = null;
            oldVals =  new ExpressionEvaluator(expressions);
        }
        else {
            throw new AssertionError("both expression types can't be null");
        }
        ImmutableRow holderRow = new ImmutableRow(rowType, oldVals, newVals);
        return new Delegating(holderRow);
    }

    public static BindableRow of(Row row, boolean usePValues) {
        return new Delegating(strictCopy(row, usePValues));
    }

    // BindableRow instance interface

    public abstract Row bind(QueryContext context);

    private static Row strictCopy(Row input, boolean usePValues) {
        RowCopier oldCopier;
        RowPCopier newCopier;
        if (usePValues) {
            newCopier = new RowPCopier(input);
            oldCopier = null;
        }
        else {
            newCopier = null;
            oldCopier = new RowCopier(input);
        }
        return new ImmutableRow(input.rowType(), oldCopier, newCopier);
    }

    // nested classes

    private static class BindingExpressions extends BindableRow {
        @Override
        public Row bind(QueryContext context) {
            return new ExpressionRow(rowType, context, expressions);
        }

        private BindingExpressions(RowType rowType, List<? extends Expression> expressions) {
            this.rowType = rowType;
            this.expressions = expressions;
            for (Expression expression : expressions) {
                if (expression.needsRow()) {
                    throw new IllegalArgumentException("expression " + expression + " needs a row");
                }
            }
        }

        // object interface


        @Override
        public String toString() {
            return "Bindable" + expressions;
        }

        private final List<? extends Expression> expressions;
        private final RowType rowType;
    }

    private static class RowCopier implements Iterator<ValueSource> {

        @Override
        public boolean hasNext() {
            return i < sourceRow.rowType().nFields();
        }

        @Override
        public ValueSource next() {
            return sourceRow.eval(i++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private RowCopier(Row sourceRow) {
            this.sourceRow = sourceRow;
        }

        private final Row sourceRow;
        private int i = 0;
    }

    private static class RowPCopier implements Iterator<PValueSource>  {

        @Override
        public boolean hasNext() {
            return i < sourceRow.rowType().nFields();
        }

        @Override
        public PValueSource next() {
            return sourceRow.pvalue(i++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private RowPCopier(Row sourceRow) {
            this.sourceRow = sourceRow;
        }

        private final Row sourceRow;
        private int i = 0;
    }

    private static class ExpressionEvaluator implements Iterator<ValueSource> {

        @Override
        public boolean hasNext() {
            return expressions.hasNext();
        }

        @Override
        public ValueSource next() {
            Expression expression = expressions.next();
            assert expression.isConstant() : "not constant: " + expression;
            return expression.evaluation().eval();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private ExpressionEvaluator(Collection<? extends Expression> expressions)
        {
            this.expressions = expressions.iterator();
        }

        private final Iterator<? extends Expression> expressions;
    }

    private static class PExpressionEvaluator implements Iterator<PValueSource> {

        @Override
        public boolean hasNext() {
            return expressions.hasNext();
        }

        @Override
        public PValueSource next() {
            TPreparedExpression expression = expressions.next();
//            assert expression.isConstant() : "not constant: " + expression; TODO
            TEvaluatableExpression eval = expression.build();
            eval.evaluate();
            return eval.resultValue();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private PExpressionEvaluator(Collection<? extends TPreparedExpression> expressions)
        {
            this.expressions = expressions.iterator();
        }

        private final Iterator<? extends TPreparedExpression> expressions;
    }

    private static class Delegating extends BindableRow {
        @Override
        public Row bind(QueryContext context) {
            return row;
        }

        @Override
        public String toString() {
            return String.valueOf(row);
        }

        private Delegating(Row row) {
            this.row = row;
        }

        private final Row row;
    }
}
