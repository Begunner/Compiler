package Scope;

import org.antlr.v4.runtime.misc.Pair;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;

public class FunctionSymbol extends BaseScope {
    private LLVMValueRef function;

    public FunctionSymbol(Scope enclosingScope) {
        super(enclosingScope);
    }

    public LLVMValueRef getFunction() {
        return this.function;
    }

}
