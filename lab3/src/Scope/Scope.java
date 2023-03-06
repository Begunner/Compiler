package Scope;

import Symbol.Symbol;

import java.util.Map;

public interface Scope {
    public Map<String, Symbol> getSymbols();

    public Scope getEnclosingScope();

    public boolean define(Symbol symbol);

    public Symbol resolve(String name);

}
