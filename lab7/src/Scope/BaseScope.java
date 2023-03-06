package Scope;

import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.LinkedHashMap;
import java.util.Map;

public class BaseScope implements Scope{
    private final Scope enclosingScope;
    private final Map<String, LLVMValueRef> symbols = new LinkedHashMap<>();

    public BaseScope(Scope enclosingScope) {
        this.enclosingScope = enclosingScope;
    }

    @Override
    public Map<String, LLVMValueRef> getSymbols() {
        return this.symbols;
    }

    @Override
    public Scope getEnclosingScope() {
        return this.enclosingScope;
    }

    @Override
    public void define(String name, LLVMValueRef symbol) {
        symbols.put(name, symbol);
    }

    @Override
    public LLVMValueRef resolve(String name) {
        LLVMValueRef symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (enclosingScope != null) {
            return enclosingScope.resolve(name);
        }
        return null;
    }

}
