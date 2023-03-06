package Scope;

import Symbol.Symbol;

import java.util.LinkedHashMap;
import java.util.Map;

public class BaseScope implements Scope{
    private final Scope enclosingScope;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public BaseScope(Scope enclosingScope) {
        this.enclosingScope = enclosingScope;
    }
    @Override
    public Map<String, Symbol> getSymbols() {
        return this.symbols;
    }

    @Override
    public Scope getEnclosingScope() {
        return this.enclosingScope;
    }

    @Override
    public boolean define(Symbol symbol) {
        for (String name : symbols.keySet()) {
            if (name.equals(symbol.getName())) {        // 在同一作用域内，发生了重定义
                return false;
            }
        }
        symbols.put(symbol.getName(), symbol);
        return true;
    }

    @Override
    public Symbol resolve(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (enclosingScope != null) {
            return enclosingScope.resolve(name);
        }
        return null;
    }

}
