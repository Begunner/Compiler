import Scope.*;
import Symbol.*;
import Type.*;
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;

public class TableVisitor extends SysYParserBaseVisitor{
    private Scope currentScope;
    private Scope globalScope;
    public boolean hasError = false;

    private static ArrayList<BaseSymbol> baseSymbols = new ArrayList<>();


    @Override
    public Object visitProgram(SysYParser.ProgramContext ctx) {
        GlobalScope globalScope = new GlobalScope(null);
        this.currentScope = globalScope;
        this.globalScope = globalScope;

        super.visitProgram(ctx);

        return null;
    }

    @Override
    public Object visitFuncDef(SysYParser.FuncDefContext ctx) {
        FunctionSymbol functionSymbol = new FunctionSymbol(currentScope);
        this.currentScope = functionSymbol;

        // 将该func加入currentScope的符号表
        functionSymbol.setName(ctx.IDENT().getText());
        FunctionType functionType = new FunctionType();
        if (ctx.funcType().getText().equals("int")) {
            functionType.setReturnType(new IntType());
        } else {
            functionType.setReturnType(new VoidType());
        }
        functionSymbol.setType(functionType);
        if (!functionSymbol.getEnclosingScope().define(functionSymbol)) {
            printError(4, ctx.getStart().getLine());
            this.currentScope = this.currentScope.getEnclosingScope();
            return null;
        }

        super.visitFuncDef(ctx);
        // 退出funcDef，作用域回到上级
        this.currentScope = this.currentScope.getEnclosingScope();

        return null;
    }

    @Override
    public Object visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        // 检查func是否重定义，若是则直接跳过params.此时，currentScope是func
        for (String name : currentScope.getSymbols().keySet()) {
            if (name.equals(((SysYParser.FuncDefContext)ctx.getParent()).IDENT().getText())) {      // 发生重名
                return null;
            }
        }

        super.visitFuncFParams(ctx);

        BaseSymbol paramSymbol;
        for (SysYParser.FuncFParamContext param : ctx.funcFParam()) {
            Type paramType;
            if (param.L_BRACKT(0) == null){
                // 意味着是int类型
                paramType = new IntType();
            } else {
                // 意味着是array类型
                paramType = new ArrayType(new IntType(), param.L_BRACKT().size());
            }
            paramSymbol = new BaseSymbol(param.IDENT().getText(), paramType);
            if (!currentScope.define(paramSymbol)) {
                printError(3, ctx.getStart().getLine());
            } else {
                paramSymbol.addLocation(param.IDENT().getSymbol().getLine(), param.IDENT().getSymbol().getCharPositionInLine());
                ((FunctionType) (((FunctionSymbol)currentScope).getType())).addParam(paramType);
                baseSymbols.add(paramSymbol);
            }

        }

        return null;
    }

    @Override
    public Object visitBlock(SysYParser.BlockContext ctx) {
        // 先要知道父节点是不是funcDef，若是，则检查func是否重定义，若是则直接跳过整个block，若不是则不创建新作用域
        if (ctx.getParent() != null && ctx.getParent() instanceof SysYParser.FuncDefContext) {
            // 此时，currentScope是func
            for (String name : currentScope.getSymbols().keySet()) {
                if (name.equals(((SysYParser.FuncDefContext)ctx.getParent()).IDENT().getText())) {      // 发生重名
                    return null;
                }
            }
            // 作用域仍然为funcSym
        } else {
            // 要创建作用域
            LocalScope localScope = new LocalScope(currentScope);
            this.currentScope = localScope;
        }
        super.visitBlock(ctx);

        if (!(ctx.getParent() instanceof SysYParser.FuncDefContext)) { // 若并非函数下的block，需要退回上级作用域
            this.currentScope = this.currentScope.getEnclosingScope();
        }


        return null;
    }

    @Override
    public Object visitVarDef(SysYParser.VarDefContext ctx) {
        String name = ctx.IDENT().getText();
        Type type;
        if (ctx.L_BRACKT(0) == null) {
            // int
            type = new IntType();
        } else {
            type = new ArrayType(new IntType(), ctx.L_BRACKT().size());
        }
        BaseSymbol baseSymbol = new BaseSymbol(name, type);
        if (!currentScope.define(baseSymbol)) {
            printError(3, ctx.getStart().getLine());
            return null;
        } else {
            baseSymbol.addLocation(ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getSymbol().getCharPositionInLine());
            baseSymbols.add(baseSymbol);
        }

        // 未发生重定义
        if (ctx.initVal() != null && ctx.initVal() instanceof SysYParser.ExpInitValContext) {  // 有赋值号
            Type rightType = visitSpecificExp(((SysYParser.ExpInitValContext) ctx.initVal()).exp());
            if (rightType != null && !typeEqual(rightType, type)) {
                printError(5, ctx.getStart().getLine());
                return null;
            }
        }

        return null;
    }

    @Override
    public Object visitConstDef(SysYParser.ConstDefContext ctx) {
        String name = ctx.IDENT().getText();
        Type type;
        if (ctx.L_BRACKT(0) == null) {
            // int
            type = new IntType();
        } else {
            type = new ArrayType(new IntType(), ctx.L_BRACKT().size());
        }
        BaseSymbol baseSymbol = new BaseSymbol(name, type);
        if (!currentScope.define(baseSymbol)) {
            printError(3, ctx.getStart().getLine());
            return null;
        } else {
            baseSymbol.addLocation(ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getSymbol().getCharPositionInLine());
            baseSymbols.add(baseSymbol);
        }

        // 未发生重定义
        if (ctx.constInitVal() instanceof SysYParser.ConstExpInitValContext) {  // 有赋值号
            Type rightType = visitSpecificExp(((SysYParser.ConstExpInitValContext) ctx.constInitVal()).constExp().exp());
            if (rightType != null && !typeEqual(rightType, type)) {
                printError(5, ctx.getStart().getLine());
                return null;
            }
        }

        return null;
    }

    @Override
    public Type visitLVal(SysYParser.LValContext ctx) {
        super.visitLVal(ctx);
        Symbol symbol = nameInScopes(ctx.IDENT().getText());
        if (symbol == null) {
            printError(1, ctx.getStart().getLine());
            return null;
        }
        // 对非数组使用下标运算符
        if (!(symbol.getType() instanceof ArrayType) && ctx.L_BRACKT().size() > 0) {
            printError(9, ctx.getStart().getLine());
            return null;
        }

        if (symbol.getType() instanceof FunctionType) {
            return symbol.getType();
        }
        ((BaseSymbol)symbol).addLocation(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());

        int dimension = 0;
        if (symbol.getType() instanceof ArrayType) {
            dimension = ((ArrayType) symbol.getType()).getDimension();
        }
        // dim要减去中括号的个数
        dimension -= ctx.L_BRACKT().size();
        if (dimension < 0) {
            printError(9, ctx.getStart().getLine());
            return null;
        }
        if (dimension == 0)
            return new IntType();
        else
            return new ArrayType(new IntType(), dimension);
    }


    @Override
    public Type visitFuncExp(SysYParser.FuncExpContext ctx) {
        String name = ctx.IDENT().getText();
        Symbol symbol = nameInScopes(name);
        // 是否是对已定义的变量用了函数调用符
        if (symbol != null) { // 已定义
            if (!(symbol.getType() instanceof FunctionType)) {
                printError(10, ctx.getStart().getLine());
                return null;
            }
        } else { // 未定义
            printError(2, ctx.getStart().getLine());
            return null;
        }
        // 检查参数不适用
        ArrayList<Type> fParamsType = ((FunctionType)symbol.getType()).getParamsType();
        if (ctx.funcRParams() == null) {
            if (fParamsType.size() != 0) {
                printError(8, ctx.getStart().getLine());
                return null;
            }
        } else {
            ArrayList<Type> paramsType = new ArrayList<>();
            for (SysYParser.ParamContext param : ctx.funcRParams().param()) {
                if (param != null) {
                    paramsType.add(visitParam(param));
                }
            }
            if (fParamsType.size() != paramsType.size()) {
                printError(8, ctx.getStart().getLine());
                return null;
            }
            for (int i = 0; i < paramsType.size(); i++ ) {
                if(!typeEqual(fParamsType.get(i), paramsType.get(i))) {
                    printError(8, ctx.getStart().getLine());
                    return null;
                }
            }
        }
        Type returnType = ((FunctionType)globalScope.resolve(name).getType()).getReturnType();
        return returnType;
    }

    @Override
    public Type visitParam(SysYParser.ParamContext ctx) {
        return visitSpecificExp(ctx.exp());
    }

    @Override
    public Type visitPlusMinusExp(SysYParser.PlusMinusExpContext ctx) {
        Type type0 = visitSpecificExp(ctx.exp(0));
        Type type1 = visitSpecificExp(ctx.exp(1));
        if (type0 == null || type1 == null) {
            return null;
        } else if (!typeEqual(type0, type1) || !(type0 instanceof IntType)) {
            printError(6, ctx.getStart().getLine());
            return null;
        } else {
            return type0;
        }
    }

    @Override
    public Type visitLValExp(SysYParser.LValExpContext ctx) {
        return visitLVal(ctx.lVal());
    }

    @Override
    public Type visitNumberExp(SysYParser.NumberExpContext ctx) {
        return new IntType();
    }

    @Override
    public Type visitMulDivModExp(SysYParser.MulDivModExpContext ctx) {
        Type type0 = visitSpecificExp(ctx.exp(0));
        Type type1 = visitSpecificExp(ctx.exp(1));
        if (type0 == null || type1 == null) {
            return null;
        } else if (!typeEqual(type0, type1) || !(type0 instanceof IntType)) {
            printError(6, ctx.getStart().getLine());
            return null;
        } else {
            return type0;
        }
    }

    @Override
    public Type visitUnaryExp(SysYParser.UnaryExpContext ctx) {
        Type type = visitSpecificExp(ctx.exp());
        if (type == null) {
            return null;
        } else if (!(type instanceof IntType)) {
            printError(6, ctx.getStart().getLine());
            return null;
        } else {
            return type;
        }
    }

    @Override
    public Type visitParenExp(SysYParser.ParenExpContext ctx) {
        return visitSpecificExp(ctx.exp());
    }

    @Override
    public Object visitReturnStmt(SysYParser.ReturnStmtContext ctx) {
        Scope functionSymbol = currentScope;
        while (!(functionSymbol instanceof FunctionSymbol) ) {
            functionSymbol = functionSymbol.getEnclosingScope();
        }
        Type returnType = ((FunctionType)((FunctionSymbol)functionSymbol).getType()).getReturnType();
        if (returnType instanceof VoidType) {
            if (ctx.exp() != null) {
                printError(7, ctx.getStart().getLine());
                return null;
            }
        } else if (returnType instanceof IntType) {
            if (ctx.exp() == null) {
                printError(7, ctx.getStart().getLine());
                return null;
            } else if (visitSpecificExp(ctx.exp()) != null && !(visitSpecificExp(ctx.exp()) instanceof IntType)) {
                printError(7, ctx.getStart().getLine());
                return null;
            }
        }
        return null;
    }

    @Override
    public Object visitAssignStmt(SysYParser.AssignStmtContext ctx) {
        Type leftType = visitLVal(ctx.lVal());
        Type rightType = visitSpecificExp(ctx.exp());
        // 先检测左侧是否为func
        if (leftType instanceof FunctionType) {
            printError(11, ctx.getStart().getLine());
            return null;
        }
        if (leftType != null && rightType != null && !typeEqual(leftType, rightType)) {
            printError(5, ctx.getStart().getLine());
            return null;
        }
        return null;
    }

    @Override
    public Type visitCond(SysYParser.CondContext ctx) {
        if (ctx.cond().size() == 2) {
            Type type0 = visitCond(ctx.cond(0));
            Type type1 = visitCond(ctx.cond(1));
            if (type0 == null || type1 == null) {
                return null;
            } else if (!typeEqual(type0, type1)) {
                printError(6, ctx.getStart().getLine());
                return null;
            } else if (!(type0 instanceof IntType)) {
                printError(6, ctx.getStart().getLine());
                return null;
            }
        } else {
            return visitSpecificExp(ctx.exp());
        }
        return null;
    }

    // 检查该name是否在同级及上级Scope中出现过了
    private Symbol nameInScopes(String name) {
        Scope scope = currentScope;
        while (scope.resolve(name) == null) {
            scope = scope.getEnclosingScope();
            if (scope == null) {
                return null;
            }
        }
        return scope.resolve(name);
    }

    private void printError(int type, int line) {
        hasError = true;
        System.err.println("Error type " + type + " at Line " + line + ":");
    }

    private Type visitSpecificExp(SysYParser.ExpContext exp) {
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

    private boolean typeEqual(Type type0, Type type1) {
        if (type0 instanceof IntType && type1 instanceof IntType) {
            return true;
        } else if (type0 instanceof ArrayType && type1 instanceof ArrayType) {
            if (((ArrayType) type0).getDimension() == ((ArrayType) type1).getDimension())
                return true;
            else
                return false;
        } else if (type0 instanceof VoidType && type1 instanceof VoidType) {
            return true;
        } else if (type0 instanceof FunctionType && type1 instanceof FunctionType) {
            return true;
        } else {
            return false;
        }
    }

    public static BaseSymbol getBaseSymbol(int line, int column) {
        for (BaseSymbol baseSymbol : baseSymbols) {
            if (baseSymbol.isTheSymbol(new Pair<>(line, column))) {
                return baseSymbol;
            }
        }
        return null;
    }



}
