package Scope;

import Symbol.Symbol;
import Type.Type;
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;

public class FunctionSymbol extends BaseScope implements Symbol {
    private Type type;
    private String name;

    public FunctionSymbol(Scope enclosingScope) {
        super(enclosingScope);
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Type getType() {
        return type;
    }

    public String getName() {
        return this.name;
    }

}
