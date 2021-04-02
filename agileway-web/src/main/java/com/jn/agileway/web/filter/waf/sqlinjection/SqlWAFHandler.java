package com.jn.agileway.web.filter.waf.sqlinjection;

import com.jn.agileway.web.filter.waf.AbstractWAFHandler;

/**
 * 替换掉特殊字符
 */
public abstract class SqlWAFHandler extends AbstractWAFHandler {

    @Override
    public abstract String apply(String value);

    @Override
    public String getAttackName() {
        return "SQL-Inject";
    }

    @Override
    protected boolean isAttack(String value) {
        return false;
    }
}