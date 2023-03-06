package Scope;

import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.LinkedList;
import java.util.Map;

public interface Scope {
    public Map<String, LLVMValueRef> getSymbols();

    public Scope getEnclosingScope();

    public void define(String name, LLVMValueRef symbol);

    public LLVMValueRef resolve(String name);

}
