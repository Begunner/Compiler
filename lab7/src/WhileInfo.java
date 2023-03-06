import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;

public class WhileInfo {
    private final LLVMBasicBlockRef conditionBlock;
    private final LLVMBasicBlockRef entryBlock;

    public WhileInfo(LLVMBasicBlockRef conditionBlock, LLVMBasicBlockRef entryBlock) {
        this.conditionBlock = conditionBlock;
        this.entryBlock = entryBlock;
    }

    public LLVMBasicBlockRef getConditionBlock() {
        return conditionBlock;
    }

    public LLVMBasicBlockRef getEntryBlock() {
        return entryBlock;
    }
}
