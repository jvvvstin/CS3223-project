package simpledb.parse;

public class PredParser {
   private Lexer lex;

   /**
    * Creates a predicate recognizer for the specified string.
    * @param s the string containing the predicate
    */
   public PredParser(String s) {
      lex = new Lexer(s);
   }

   /**
    * Consumes a field name.
    * @return the field name
    * @throws BadSyntaxException if the next token is not an identifier
    */
   public String field() {
      return lex.eatId();
   }

   /**
    * Consumes a constant, which is either a string or an integer.
    * @throws BadSyntaxException if the next token is neither
    */
   public void constant() {
      if (lex.matchStringConstant())
         lex.eatStringConstant();
      else
         lex.eatIntConstant();
   }

   /**
    * Consumes an expression, which is either a field name or a constant.
    * @throws BadSyntaxException if the next token is neither
    */
   public void expression() {
      if (lex.matchId())
         field();
      else 
         constant();
   }

   /**
    * Consumes a term: an expression, a comparison operator, and an
    * expression. The operators accepted are those supported by the Lexer
    * (=, <, >, <=, >=, <> and !=).
    * @throws BadSyntaxException if the tokens do not form a term
    */
   public void term() {
      expression();
      lex.eatOpr();
      expression();
   }

   /**
    * Consumes a predicate: one or more terms joined by "and".
    * @throws BadSyntaxException if the tokens do not form a predicate
    */
   public void predicate() {
      term();
      if (lex.matchKeyword("and")) {
         lex.eatKeyword("and");
         predicate();
      }
   }
}

