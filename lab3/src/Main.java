import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;

public class Main
{
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        ParserErrorListener parserErrorListener = new ParserErrorListener();
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        sysYParser.removeErrorListeners();
        sysYParser.addErrorListener(parserErrorListener);

        ParseTree tree = sysYParser.program();
        TableVisitor tableVisitor = new TableVisitor();
        tableVisitor.visit(tree);       // 检错与构建符号表
        PrintVisitor printVisitor = new PrintVisitor();
        printVisitor.setNameLineColumn(args[3], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        if(!tableVisitor.hasError) {
            printVisitor.visit(tree);   // 打印语法树
        }

    }


}
