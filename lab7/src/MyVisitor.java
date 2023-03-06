import Scope.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;

import java.util.ArrayList;
import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyVisitor extends SysYParserBaseVisitor<LLVMValueRef> {

    //创建module
    LLVMModuleRef module = LLVMModuleCreateWithName("module");

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    LLVMBuilderRef builder = LLVMCreateBuilder();

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    LLVMTypeRef i32Type = LLVMInt32Type();

    //创建一个常量,这里是常数0
    LLVMValueRef zero = LLVMConstInt(i32Type, 0, /* signExtend */ 0);

    private Scope currentScope;
    private Scope globalScope;

    private LLVMValueRef currentFunction;

    private Stack<WhileInfo> whileStack = new Stack<>();

    private boolean hasBroken;

    public static final BytePointer error = new BytePointer();

    public MyVisitor() {
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        BaseScope globalScope = new BaseScope(null);
        this.currentScope = globalScope;
        this.globalScope = globalScope;

        super.visitProgram(ctx);

        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        FunctionSymbol functionSymbol = new FunctionSymbol(currentScope);
        this.currentScope = functionSymbol;
        //生成返回值类型
        LLVMTypeRef returnType;
        if (ctx.funcType().INT() != null)
            returnType = i32Type;
        else
            returnType = LLVMVoidType();

        //生成函数参数类型
        int argumentNum = 0;
        if (ctx.funcFParams() != null) {
            argumentNum = ctx.funcFParams().funcFParam().size();
        }
        PointerPointer<Pointer> argumentTypes = new PointerPointer<>(argumentNum);
        for (int i = 0; i < argumentNum; i++) {
            argumentTypes.put(i, i32Type);
        }

        //生成函数类型
        LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, /* argumentCount */ argumentNum, /* isVariadic */ 0);

        //生成函数，即向之前创建的module中添加函数
        LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/ctx.IDENT().getText(), ft);
        this.currentFunction = function;
        functionSymbol.getEnclosingScope().define(ctx.IDENT().getText(), function);
        //通过如下语句在函数中加入基本块，一个函数可以加入多个基本块
        LLVMBasicBlockRef block = LLVMAppendBasicBlock(function, /*blockName:String*/ctx.IDENT().getText() + "Entry");

        //选择要在哪个基本块后追加指令
        LLVMPositionBuilderAtEnd(builder, block);//后续生成的指令将追加在block1的后面

        //构建参数
        for (int i = 0; i < argumentNum; i++) {
            SysYParser.FuncFParamContext param = ctx.funcFParams().funcFParam(i);
            LLVMTypeRef paramType = i32Type;
            String paramName = param.IDENT().getText();
            LLVMValueRef paramMemory = LLVMBuildAlloca(builder, paramType, "memory_" + paramName);
            currentScope.define(paramName, paramMemory);
            LLVMBuildStore(builder, LLVMGetParam(function, i), paramMemory);
        }

        LLVMValueRef result;
        super.visitFuncDef(ctx);
        this.currentScope = this.currentScope.getEnclosingScope();
        this.currentFunction = null;

        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        if (!(ctx.getParent() instanceof SysYParser.FuncDefContext)) {
            BaseScope localScope = new BaseScope(currentScope);
            this.currentScope = localScope;
        }
        super.visitBlock(ctx);
        if (!(ctx.getParent() instanceof SysYParser.FuncDefContext)) { // 若并非函数下的block，需要退回上级作用域
            this.currentScope = this.currentScope.getEnclosingScope();
        } else {
            // 是函数下的block，若函数返回值为void且block内没有返回语句，需要添加
            boolean haveRet = false;
            if (ctx.blockItem(0) != null) {
                for (int i = 0; i < ctx.blockItem().size(); i++) {
                    if (ctx.blockItem(i).stmt() instanceof SysYParser.ReturnStmtContext) {
                        haveRet = true;
                    }
                }
            }
            if (!haveRet) {
                LLVMBuildRet(builder, null);
            }
        }

        return null;
    }

    @Override
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        String name = ctx.IDENT().getText();
        LLVMTypeRef varType;
        if (currentScope == globalScope) {
            // 全局变量
            if (ctx.L_BRACKT(0) == null) {
                // 非数组
                varType = i32Type;
                LLVMValueRef globalVarMemory = LLVMAddGlobal(module, varType, "global_const_" + name);
                globalScope.define(name, globalVarMemory);
                if (ctx.ASSIGN() == null)
                    LLVMSetInitializer(globalVarMemory, zero);
                else
                    LLVMSetInitializer(globalVarMemory, visitConstExp(((SysYParser.ConstExpInitValContext) ctx.constInitVal()).constExp()));
            } else {
                // 数组
                int length = Integer.parseInt(ctx.constExp(0).getText());
                varType = LLVMVectorType(i32Type, length);
                LLVMValueRef globalVarMemory = LLVMAddGlobal(module, varType, "global_const_" + name);
                currentScope.define(name, globalVarMemory);
                LLVMValueRef[] values = new LLVMValueRef[length];
                SysYParser.ConstArrayInitValContext initValues = (SysYParser.ConstArrayInitValContext) ctx.constInitVal();
                if (initValues != null) {
                    for (int i = 0; i < length; i++) {
                        if (initValues.constInitVal(i) != null) {
                            values[i] = visitSpecificExp(((SysYParser.ConstExpInitValContext) initValues.constInitVal(i)).constExp().exp());
                        } else {
                            values[i] = zero;
                        }
                    }
                } else {
                    for (int i = 0; i < length; i++) {
                        values[i] = zero;
                    }
                }
                PointerPointer<LLVMValueRef> pointerPointer = new PointerPointer<>(values);
                LLVMSetInitializer(globalVarMemory, LLVMConstVector(pointerPointer, length));
            }
        } else {
            if (ctx.L_BRACKT(0) == null) {
                // 非数组
                varType = i32Type;
                LLVMValueRef varMemory = LLVMBuildAlloca(builder, varType, "memory_" + name);
                currentScope.define(name, varMemory);
                LLVMBuildStore(builder, visitSpecificExp(((SysYParser.ConstExpInitValContext) ctx.constInitVal()).constExp().exp()), varMemory);
            } else {
                // 数组
                int length = Integer.parseInt(ctx.constExp(0).getText());
                varType = LLVMVectorType(i32Type, length);
                LLVMValueRef varMemory = LLVMBuildAlloca(builder, varType, "memory_" + name);
                currentScope.define(name, varMemory);
                LLVMValueRef[] values = new LLVMValueRef[length];
                SysYParser.ConstArrayInitValContext initValues = (SysYParser.ConstArrayInitValContext) ctx.constInitVal();
                for (int i = 0; i < length; i++) {
                    if (initValues.constInitVal(i) != null) {
                        values[i] = visitSpecificExp(((SysYParser.ConstExpInitValContext) initValues.constInitVal(i)).constExp().exp());
                    } else {
                        values[i] = zero;
                    }
                }
                makeGEP(length, varMemory, values);
            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        String name = ctx.IDENT().getText();
        LLVMTypeRef varType;
        if (currentScope == globalScope) {
            // 全局变量
            if (ctx.L_BRACKT(0) == null) {
                // 非数组
                varType = i32Type;
                LLVMValueRef globalVarMemory = LLVMAddGlobal(module, varType, "global_" + name);
                globalScope.define(name, globalVarMemory);
                if (ctx.ASSIGN() == null)
                    LLVMSetInitializer(globalVarMemory, zero);
                else
                    LLVMSetInitializer(globalVarMemory, visitExpInitVal((SysYParser.ExpInitValContext) ctx.initVal()));
            } else {
                // 数组
                int length = Integer.parseInt(ctx.constExp(0).getText());
                varType = LLVMVectorType(i32Type, length);
                LLVMValueRef globalVarMemory = LLVMAddGlobal(module, varType, "global_" + name);
                currentScope.define(name, globalVarMemory);
                LLVMValueRef[] values = new LLVMValueRef[length];
                SysYParser.ArrayInitValContext initValues = (SysYParser.ArrayInitValContext) ctx.initVal();
                if (initValues != null) {
                    for (int i = 0; i < length; i++) {
                        if (initValues.initVal(i) != null) {
                            values[i] = visitSpecificExp(((SysYParser.ExpInitValContext) initValues.initVal(i)).exp());
                        } else {
                            values[i] = zero;
                        }
                    }
                } else {
                    for (int i = 0; i < length; i++) {
                        values[i] = zero;
                    }
                }
                PointerPointer<LLVMValueRef> pointerPointer = new PointerPointer<>(values);
                LLVMSetInitializer(globalVarMemory, LLVMConstVector(pointerPointer, length));
            }
        } else {
            // 局部变量
            if (ctx.L_BRACKT(0) == null) {
                // 非数组
                varType = i32Type;
                LLVMValueRef varMemory = LLVMBuildAlloca(builder, varType, "memory_" + name);
                currentScope.define(name, varMemory);

                if (ctx.ASSIGN() != null) {
                    // 有初始值
                    LLVMBuildStore(builder, visitSpecificExp(((SysYParser.ExpInitValContext) ctx.initVal()).exp()), varMemory);
                }
            } else {
                // 数组
                int length = Integer.parseInt(ctx.constExp(0).getText());
                varType = LLVMVectorType(i32Type, length);
                LLVMValueRef varMemory = LLVMBuildAlloca(builder, varType, "memory_" + name);
                currentScope.define(name, varMemory);
                LLVMValueRef[] values = new LLVMValueRef[length];
                SysYParser.ArrayInitValContext initValues = (SysYParser.ArrayInitValContext) ctx.initVal();
                for (int i = 0; i < length; i++) {
                    if (initValues.initVal(i) != null) {
                        values[i] = visitSpecificExp(((SysYParser.ExpInitValContext) initValues.initVal(i)).exp());
                    } else {
                        values[i] = zero;
                    }
                }
                makeGEP(length, varMemory, values);
            }
        }

        return null;
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        String name = ctx.IDENT().getText();
        LLVMValueRef valueRef = currentScope.resolve(name);
        if (ctx.L_BRACKT(0) == null) {
            // 非数组
            return valueRef;
        } else {
            LLVMValueRef index = visitSpecificExp(ctx.exp(0));
            LLVMValueRef[] array = {zero, index};
            PointerPointer<LLVMValueRef> indexPtr = new PointerPointer<>(array);
            LLVMValueRef element = LLVMBuildGEP(builder, valueRef, indexPtr, 2, "memory_" + name + "[" + ctx.exp(0).getText() + "]");
            return element;
        }
    }

    @Override
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        return LLVMBuildRet(builder, visitSpecificExp(ctx.exp()));
    }

    @Override
    public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        LLVMBuildStore(builder, visitSpecificExp(ctx.exp()), visitLVal(ctx.lVal()));
        return null;
    }

    @Override
    public LLVMValueRef visitIfStmt(SysYParser.IfStmtContext ctx) {
        LLVMValueRef cond = visitCond(ctx.cond());
        cond = dealWithCondition(cond, zero, "NEQ");
        LLVMBasicBlockRef blockTrue = LLVMAppendBasicBlock(currentFunction, "blockTrue");
        LLVMBasicBlockRef blockFalse = LLVMAppendBasicBlock(currentFunction, "blockFalse");
        LLVMBuildCondBr(builder, cond, blockTrue, blockFalse);
        LLVMBasicBlockRef blockFinally = LLVMAppendBasicBlock(currentFunction, "blockFinally");
        if (ctx.stmt().size() == 1) {
            // 只有if语句
            LLVMPositionBuilderAtEnd(builder, blockTrue);
            visitSpecificStmt(ctx.stmt(0));
            LLVMBuildBr(builder, blockFinally);
            LLVMPositionBuilderAtEnd(builder, blockFalse);
            LLVMBuildBr(builder, blockFinally);
        } else {
            // if-else
            LLVMPositionBuilderAtEnd(builder, blockTrue);
            visitSpecificStmt(ctx.stmt(0));
            LLVMBuildBr(builder, blockFinally);
            LLVMPositionBuilderAtEnd(builder, blockFalse);
            visitSpecificStmt(ctx.stmt(1));
            LLVMBuildBr(builder, blockFinally);
        }
        LLVMPositionBuilderAtEnd(builder, blockFinally);
        return null;
    }

    @Override
    public LLVMValueRef visitWhileStmt(SysYParser.WhileStmtContext ctx) {
        LLVMBasicBlockRef conditionBlock = LLVMAppendBasicBlock(currentFunction, "whileCondition");
        LLVMBuildBr(builder, conditionBlock);
        LLVMPositionBuilderAtEnd(builder, conditionBlock);
        LLVMValueRef cond = visitCond(ctx.cond());
        LLVMValueRef temp = LLVMBuildZExt(builder, cond, i32Type, "temp");
        temp = LLVMBuildICmp(builder, LLVMIntNE, temp, zero, "temp");
        LLVMBasicBlockRef bodyBlock = LLVMAppendBasicBlock(currentFunction, "whileBody");
        LLVMBasicBlockRef entryBlock = LLVMAppendBasicBlock(currentFunction, "entry");
        LLVMBuildCondBr(builder, temp, bodyBlock, entryBlock);
        WhileInfo whileInfo = new WhileInfo(conditionBlock, entryBlock);
        whileStack.push(whileInfo);
        LLVMPositionBuilderAtEnd(builder, bodyBlock);
        visitSpecificStmt(ctx.stmt());
        LLVMBuildBr(builder, conditionBlock);
        LLVMPositionBuilderAtEnd(builder, entryBlock);
        whileStack.pop();
        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx) {
        LLVMBuildBr(builder, whileStack.peek().getEntryBlock());
        return null;
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx) {
        LLVMBuildBr(builder, whileStack.peek().getConditionBlock());
        return null;
    }

    @Override
    public LLVMValueRef visitPlusMinusExp(SysYParser.PlusMinusExpContext ctx) {
        LLVMValueRef op1 = visitSpecificExp(ctx.exp(0));
        LLVMValueRef op2 = visitSpecificExp(ctx.exp(1));
        if (ctx.PLUS() != null) {
            return LLVMBuildAdd(builder, op1, op2, "add");
        } else if (ctx.MINUS() != null) {
            return LLVMBuildSub(builder, op1, op2, "sub");
        } else {
            return null;
        }
    }

    @Override
    public LLVMValueRef visitLValExp(SysYParser.LValExpContext ctx) {
        return LLVMBuildLoad(builder, visitLVal(ctx.lVal()), ctx.lVal().getText());
    }

    @Override
    public LLVMValueRef visitNumberExp(SysYParser.NumberExpContext ctx) {
        return LLVMConstInt(i32Type, Integer.parseInt(parseDec(ctx.number().getText())), 0);
    }

    @Override
    public LLVMValueRef visitMulDivModExp(SysYParser.MulDivModExpContext ctx) {
        LLVMValueRef op1 = visitSpecificExp(ctx.exp(0));
        LLVMValueRef op2 = visitSpecificExp(ctx.exp(1));
        if (ctx.DIV() != null) {
            return LLVMBuildSDiv(builder, op1, op2, "div");
        } else if (ctx.MUL() != null) {
            return LLVMBuildMul(builder, op1, op2, "mul");
        } else if (ctx.MOD() != null) {
            return LLVMBuildSRem(builder, op1, op2, "rem");
        } else {
            return null;
        }
    }

    @Override
    public LLVMValueRef visitUnaryExp(SysYParser.UnaryExpContext ctx) {
        LLVMValueRef op = visitSpecificExp(ctx.exp());
        if (ctx.unaryOp().PLUS() != null) {
            return LLVMBuildAdd(builder, zero, op, "positive");
        } else if (ctx.unaryOp().MINUS() != null) {
            return LLVMBuildSub(builder, zero, op, "negative");
        } else if (ctx.unaryOp().NOT() != null) {
            LLVMValueRef temp;
            temp = LLVMBuildICmp(builder, LLVMIntEQ, zero, op, "temp");
            temp = LLVMBuildZExt(builder, temp, i32Type, "not");
            return temp;
        } else {
            return null;
        }
    }

    @Override
    public LLVMValueRef visitFuncExp(SysYParser.FuncExpContext ctx) {
        String name = ctx.IDENT().getText();
        LLVMValueRef function = globalScope.resolve(name);
        int argumentNum = ctx.funcRParams() == null ? 0 : ctx.funcRParams().param().size();
        PointerPointer<Pointer> arguments = new PointerPointer<>(argumentNum);
        for (int i = 0; i < argumentNum; i++) {
            arguments.put(i, visitParam(ctx.funcRParams().param(i)));
        }
        return LLVMBuildCall(builder, function, arguments, argumentNum, "call");
    }

    @Override
    public LLVMValueRef visitParenExp(SysYParser.ParenExpContext ctx) {
        return visitSpecificExp(ctx.exp());
    }

    @Override
    public LLVMValueRef visitParam(SysYParser.ParamContext ctx) {
        return visitSpecificExp(ctx.exp());
    }

    @Override
    public LLVMValueRef visitCond(SysYParser.CondContext ctx) {
        if (ctx.exp() != null) {
            return visitSpecificExp(ctx.exp());
        } else {
            LLVMValueRef cond0 = visitCond(ctx.cond(0));
            LLVMValueRef cond1 = visitCond(ctx.cond(1));
            // 有多个条件
            if (ctx.LT() != null) {
                return dealWithCondition(cond0, cond1, "LT");
            } else if (ctx.GT() != null) {
                return dealWithCondition(cond0, cond1, "GT");
            } else if (ctx.LE() != null) {
                return dealWithCondition(cond0, cond1, "LE");
            } else if (ctx.GE() != null) {
                return dealWithCondition(cond0, cond1, "GE");
            } else if (ctx.EQ() != null) {
                return dealWithCondition(cond0, cond1, "EQ");
            } else if (ctx.NEQ() != null) {
                return dealWithCondition(cond0, cond1, "NEQ");
            } else if (ctx.AND() != null) {
                return dealWithCondition(cond0, cond1, "AND");
            } else if (ctx.OR() != null) {
                return dealWithCondition(cond0, cond1, "OR");
            }
        }
        return null;
    }

    public void printToFile(String path) {
        LLVMPrintModuleToFile(module, path, error);
    }

    public void printToConsole() {
        LLVMDumpModule(module);
    }

    private LLVMValueRef visitSpecificExp(SysYParser.ExpContext exp) {
        if (exp instanceof SysYParser.ParenExpContext) {
            return visitParenExp((SysYParser.ParenExpContext) exp);
        } else if (exp instanceof SysYParser.LValExpContext) {
            return visitLValExp((SysYParser.LValExpContext) exp);
        } else if (exp instanceof SysYParser.NumberExpContext) {
            return visitNumberExp((SysYParser.NumberExpContext) exp);
        } else if (exp instanceof SysYParser.FuncExpContext) {
            return visitFuncExp((SysYParser.FuncExpContext) exp);
        } else if (exp instanceof SysYParser.UnaryExpContext) {
            return visitUnaryExp((SysYParser.UnaryExpContext) exp);
        } else if (exp instanceof SysYParser.MulDivModExpContext) {
            return visitMulDivModExp((SysYParser.MulDivModExpContext) exp);
        } else if (exp instanceof SysYParser.PlusMinusExpContext) {
            return visitPlusMinusExp((SysYParser.PlusMinusExpContext) exp);
        }
        return null;
    }

    private LLVMValueRef visitSpecificStmt(SysYParser.StmtContext stmt) {
        if (stmt instanceof SysYParser.AssignStmtContext) {
            return visitAssignStmt((SysYParser.AssignStmtContext) stmt);
        } else if (stmt instanceof SysYParser.ExpStmtContext) {
            return visitExpStmt((SysYParser.ExpStmtContext) stmt);
        } else if (stmt instanceof SysYParser.BlockStmtContext) {
            return visitBlockStmt((SysYParser.BlockStmtContext) stmt);
        } else if (stmt instanceof SysYParser.IfStmtContext) {
            return visitIfStmt((SysYParser.IfStmtContext) stmt);
        } else if (stmt instanceof SysYParser.WhileStmtContext) {
            return visitWhileStmt((SysYParser.WhileStmtContext) stmt);
        } else if (stmt instanceof SysYParser.BreakStmtContext) {
            return visitBreakStmt((SysYParser.BreakStmtContext) stmt);
        } else if (stmt instanceof SysYParser.ContinueStmtContext) {
            return visitContinueStmt((SysYParser.ContinueStmtContext) stmt);
        } else if (stmt instanceof SysYParser.ReturnStmtContext) {
            return visitReturnStmt((SysYParser.ReturnStmtContext) stmt);
        }
        return null;
    }

    private void makeGEP(int length, LLVMValueRef memory, LLVMValueRef[] values) {
        for (int i = 0; i < length; i++) {
            LLVMValueRef[] array = {zero, LLVMConstInt(i32Type, i, 0)};
            PointerPointer<LLVMValueRef> indexPtr = new PointerPointer<>(array);
            LLVMValueRef element = LLVMBuildGEP(builder, memory, indexPtr, 2, "Array_" + i);
            LLVMBuildStore(builder, values[i], element);
        }
    }

    public String parseDec(String number) {
        if (number.charAt(0) == '0' && number.length() > 1) {
            if (number.charAt(1) == 'x' || number.charAt(1) == 'X') {
                return "" + Integer.parseInt(number.substring(2), 16);
            } else {
                return "" + Integer.parseInt(number.substring(1), 8);
            }
        }
        return number;
    }

    private LLVMValueRef dealWithCondition(LLVMValueRef cond0, LLVMValueRef cond1, String cmp) {
        cond0 = LLVMBuildZExt(builder, cond0, i32Type, "cond");
        cond1 = LLVMBuildZExt(builder, cond1, i32Type, "cond");
        if (cmp.equals("LT")) {
            return LLVMBuildICmp(builder, LLVMIntSLT, cond0, cond1, "LT");
        } else if (cmp.equals("GT")) {
            return LLVMBuildICmp(builder, LLVMIntSGT, cond0, cond1, "GT");
        } else if (cmp.equals("LE")) {
            return LLVMBuildICmp(builder, LLVMIntSLE, cond0, cond1, "LE");
        } else if (cmp.equals("GE")) {
            return LLVMBuildICmp(builder, LLVMIntSGE, cond0, cond1, "GE");
        } else if (cmp.equals("EQ")) {
            return LLVMBuildICmp(builder, LLVMIntEQ, cond0, cond1, "EQ");
        } else if (cmp.equals("NEQ")) {
            return LLVMBuildICmp(builder, LLVMIntNE, cond0, cond1, "NEQ");
        } else if (cmp.equals("AND")) {
            return LLVMBuildAnd(builder, cond0, cond1, "AND");
        } else if (cmp.equals("OR")) {
            return LLVMBuildOr(builder, cond0, cond1, "OR");
        }
        return null;
    }
}