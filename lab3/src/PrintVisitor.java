
import Symbol.BaseSymbol;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;

public class PrintVisitor extends SysYParserBaseVisitor{

    final String[] terminals = new String[] {
            "","CONST[orange]", "INT[orange]", "VOID[orange]", "IF[orange]", "ELSE[orange]", "WHILE[orange]", "BREAK[orange]", "CONTINUE[orange]", "RETURN[orange]",
            "PLUS[blue]", "MINUS[blue]", "MUL[blue]", "DIV[blue]", "MOD[blue]", "ASSIGN[blue]", "EQ[blue]", "NEQ[blue]", "LT[blue]", "GT[blue]",
            "LE[blue]", "GE[blue]", "NOT[blue]", "AND[blue]", "OR[blue]", "", "", "", "",
            "", "", "", "", "IDENT[red]", "INTEGR_CONST[green]",
            "", "", "", "", "", "",
            "", "", ""
    };

    private String rename;
    private Pair<Integer, Integer> location;

    private BaseSymbol baseSymbol;

    public String parseDec(String number){
        if(number.charAt(0) == '0' && number.length() > 1){
            if(number.charAt(1) == 'x' || number.charAt(1) == 'X'){
                return ""+Integer.parseInt(number.substring(2),16);
            } else {
                return ""+Integer.parseInt(number.substring(1),8);
            }
        }
        return number;
    }
    @Override
    public Object visitChildren(RuleNode node) {
        for(int i = 0; i < node.getRuleContext().depth() - 1 ; i++){
            System.err.print("  ");
        }
        String ruleName = SysYParser.ruleNames[node.getRuleContext().getRuleIndex()];
        System.err.println((char) (ruleName.charAt(0)-32) + ruleName.substring(1));
        return super.visitChildren(node);
    }

    @Override
    public Object visitTerminal(TerminalNode node) {
        if(node.getSymbol().getType() == -1 || terminals[node.getSymbol().getType()].equals(""))
            return super.visitTerminal(node);
        int depth = 0;
        ParseTree temp = node;
        while ((temp = temp.getParent())!= null){
            depth++;
        }
        for(int i = 0; i < depth ; i++){
            System.err.print("  ");
        }

        if (node.getSymbol().getType() == 34){
            System.err.println(parseDec(node.getSymbol().getText())+" "+terminals[node.getSymbol().getType()]);
        } else if (node.getSymbol().getType() == 33) {
            int line = node.getSymbol().getLine();
            int column = node.getSymbol().getCharPositionInLine();
            if (baseSymbol.isTheSymbol(new Pair<>(line, column))) {
                System.err.println(rename + " " + terminals[node.getSymbol().getType()]);
            } else {
                System.err.println(node.getSymbol().getText()+" "+terminals[node.getSymbol().getType()]);
            }
        } else {
            System.err.println(node.getSymbol().getText()+" "+terminals[node.getSymbol().getType()]);
        }

        return super.visitTerminal(node);

    }

    public void setNameLineColumn(String name, int line, int column) {
        this.rename = name;
        this.location = new Pair<>(line, column);
        this.baseSymbol = TableVisitor.getBaseSymbol(line, column);
    }



}
