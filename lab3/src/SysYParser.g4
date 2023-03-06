parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

program : compUnit ;

compUnit : (funcDef | decl)+ EOF ;

decl : constDecl | varDecl ;

constDecl : CONST bType constDef ( COMMA constDef )* SEMICOLON ;

bType : INT ;

constDef : IDENT ( L_BRACKT constExp R_BRACKT )* ASSIGN constInitVal ;

constInitVal : constExp #constExpInitVal
    | L_BRACE (constInitVal ( COMMA constInitVal )* )? R_BRACE  #constArrayInitVal
     ;

varDecl : bType varDef ( COMMA varDef )* SEMICOLON ;

varDef : IDENT ( L_BRACKT constExp R_BRACKT )*
    | IDENT ( L_BRACKT constExp R_BRACKT )* ASSIGN initVal ;

initVal : exp   #expInitVal
    | L_BRACE (initVal ( COMMA initVal )* )? R_BRACE    #arrayInitVal
    ;

funcDef : funcType IDENT L_PAREN (funcFParams)? R_PAREN block ;

funcType : VOID | INT ;

funcFParams : funcFParam ( COMMA funcFParam )* ;

funcFParam : bType IDENT (L_BRACKT R_BRACKT ( L_BRACKT exp R_BRACKT )* )? ;

block : L_BRACE ( blockItem )* R_BRACE ;

blockItem : decl | stmt ;

stmt : lVal ASSIGN exp SEMICOLON                        #assignStmt
    | ( exp )? SEMICOLON                                #expStmt
    | block                                             #blockStmt
    | IF L_PAREN cond R_PAREN stmt ( ELSE stmt )?       #ifStmt
    | WHILE L_PAREN cond R_PAREN stmt                   #whileStmt
    | BREAK SEMICOLON                                   #breakStmt
    | CONTINUE SEMICOLON                                #continueStmt
    | RETURN ( exp )? SEMICOLON                         #returnStmt
    ;

exp
   : L_PAREN exp R_PAREN                    #parenExp
   | lVal                                   #lValExp
   | number                                 #numberExp
   | IDENT L_PAREN funcRParams? R_PAREN     #funcExp
   | unaryOp exp                            #unaryExp
   | exp (MUL | DIV | MOD) exp              #mulDivModExp
   | exp (PLUS | MINUS) exp                 #plusMinusExp
   ;

cond
   : exp
   | cond (LT | GT | LE | GE) cond
   | cond (EQ | NEQ) cond
   | cond AND cond
   | cond OR cond
   ;

lVal
   : IDENT (L_BRACKT exp R_BRACKT)*
   ;

number
   : INTEGR_CONST
   ;

unaryOp
   : PLUS
   | MINUS
   | NOT
   ;

funcRParams
   : param (COMMA param)*
   ;

param
   : exp
   ;

constExp
   : exp
   ;