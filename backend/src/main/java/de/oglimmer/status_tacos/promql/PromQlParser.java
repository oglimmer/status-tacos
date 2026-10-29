/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.promql;

import de.oglimmer.status_tacos.promql.Expr.Aggregation;
import de.oglimmer.status_tacos.promql.Expr.Binary;
import de.oglimmer.status_tacos.promql.Expr.Call;
import de.oglimmer.status_tacos.promql.Expr.MatrixSelector;
import de.oglimmer.status_tacos.promql.Expr.NumberLiteral;
import de.oglimmer.status_tacos.promql.Expr.VectorSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for a subset of PromQL: selectors with label matchers, range selectors in functions,
 * aggregations with by/without, and arithmetic and comparison operators. No offset, no subqueries,
 * no and/or/unless, no on/ignoring/group_left.
 */
public final class PromQlParser {

  static final Set<String> AGGREGATIONS = Set.of("sum", "avg", "min", "max", "count");

  private static final Pattern DURATION_PART = Pattern.compile("(\\d+)(ms|s|m|h|d|w|y)");

  private enum Kind {
    IDENT,
    NUMBER,
    DURATION,
    STRING,
    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    LBRACKET,
    RBRACKET,
    COMMA,
    ASSIGN,
    NEQ,
    RE,
    NRE,
    EQL,
    GTR,
    LSS,
    GTE,
    LTE,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    POW,
    COLON,
    EOF
  }

  private record Token(Kind kind, String text, int pos) {}

  private final List<Token> tokens;
  private int index;

  private PromQlParser(String input) {
    this.tokens = new Lexer(input).tokens();
  }

  public static Expr parse(String input) {
    if (input == null || input.isBlank()) {
      throw new PromQlException("empty query");
    }
    PromQlParser parser = new PromQlParser(input);
    Expr expr = parser.expression();
    parser.expect(Kind.EOF, "end of query");
    return expr;
  }

  /** A duration like "5m", "1h30m" or "250ms", or a number of seconds like "15" or "1.5". */
  public static long parseDurationMillis(String text) {
    String trimmed = text.trim();
    try {
      double seconds = Double.parseDouble(trimmed);
      if (Double.isFinite(seconds) && seconds >= 0) {
        return Math.round(seconds * 1000);
      }
    } catch (NumberFormatException e) {
      // not a number: a duration with units
    }
    Matcher matcher = DURATION_PART.matcher(trimmed);
    long millis = 0;
    int end = 0;
    while (matcher.lookingAt()) {
      long amount = Long.parseLong(matcher.group(1));
      millis +=
          amount
              * switch (matcher.group(2)) {
                case "ms" -> 1L;
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 604_800_000L;
                default -> 31_536_000_000L;
              };
      end = matcher.end();
      matcher.region(end, trimmed.length());
    }
    if (end == 0 || end != trimmed.length()) {
      throw new PromQlException("invalid duration \"" + text + "\"");
    }
    return millis;
  }

  // --- grammar ---

  private Expr expression() {
    return comparison();
  }

  private Expr comparison() {
    Expr lhs = additive();
    while (at(Kind.EQL, Kind.NEQ, Kind.GTR, Kind.LSS, Kind.GTE, Kind.LTE)) {
      String op = next().text();
      boolean returnBool = false;
      if (atIdent("bool")) {
        next();
        returnBool = true;
      }
      lhs = new Binary(op, lhs, additive(), returnBool);
    }
    return lhs;
  }

  private Expr additive() {
    Expr lhs = multiplicative();
    while (at(Kind.ADD, Kind.SUB)) {
      String op = next().text();
      lhs = new Binary(op, lhs, multiplicative(), false);
    }
    return lhs;
  }

  private Expr multiplicative() {
    Expr lhs = unary();
    while (at(Kind.MUL, Kind.DIV, Kind.MOD)) {
      String op = next().text();
      lhs = new Binary(op, lhs, unary(), false);
    }
    return lhs;
  }

  private Expr unary() {
    if (at(Kind.ADD)) {
      next();
      return unary();
    }
    if (at(Kind.SUB)) {
      next();
      Expr operand = unary();
      if (operand instanceof NumberLiteral number) {
        return new NumberLiteral(-number.value());
      }
      return new Binary("*", new NumberLiteral(-1), operand, false);
    }
    return power();
  }

  /** Right-associative, binds tighter than unary minus: -2^2 is -4. */
  private Expr power() {
    Expr base = postfix();
    if (at(Kind.POW)) {
      next();
      return new Binary("^", base, unary(), false);
    }
    return base;
  }

  private Expr postfix() {
    Expr expr = primary();
    if (at(Kind.LBRACKET)) {
      Token open = next();
      if (!(expr instanceof VectorSelector selector)) {
        throw error(open, "a range [...] only works on a metric selector");
      }
      Token duration = expect(Kind.DURATION, "a duration like 5m");
      if (at(Kind.COLON)) {
        throw error(peek(), "subqueries are not supported");
      }
      expect(Kind.RBRACKET, "]");
      expr = new MatrixSelector(selector, parseDurationMillis(duration.text()));
    }
    if (atIdent("offset")) {
      throw error(peek(), "offset is not supported");
    }
    return expr;
  }

  private Expr primary() {
    Token token = peek();
    switch (token.kind()) {
      case NUMBER -> {
        next();
        return new NumberLiteral(Double.parseDouble(token.text()));
      }
      case LPAREN -> {
        next();
        Expr inner = expression();
        expect(Kind.RPAREN, ")");
        return inner;
      }
      case LBRACE -> {
        return new VectorSelector(checkSelector(token, matchers(new ArrayList<>())));
      }
      case IDENT -> {
        return identifier();
      }
      case STRING -> throw error(token, "string literals are not supported");
      default -> throw error(token, "unexpected " + describe(token));
    }
  }

  private Expr identifier() {
    Token ident = next();
    String name = ident.text();
    String lower = name.toLowerCase(Locale.ROOT);
    if (AGGREGATIONS.contains(name) && at(Kind.LPAREN, Kind.IDENT)) {
      return aggregation(name);
    }
    if (at(Kind.LPAREN)) {
      return call(name);
    }
    if (lower.equals("inf")) {
      return new NumberLiteral(Double.POSITIVE_INFINITY);
    }
    if (lower.equals("nan")) {
      return new NumberLiteral(Double.NaN);
    }
    List<LabelMatcher> matchers = new ArrayList<>();
    matchers.add(LabelMatcher.of("__name__", LabelMatcher.Type.EQ, name));
    if (at(Kind.LBRACE)) {
      matchers(matchers);
    }
    return new VectorSelector(checkSelector(ident, matchers));
  }

  private Expr aggregation(String op) {
    List<String> grouping = List.of();
    boolean without = false;
    if (atIdent("by") || atIdent("without")) {
      without = next().text().equals("without");
      grouping = labelList();
    }
    expect(Kind.LPAREN, "(");
    Expr inner = expression();
    expect(Kind.RPAREN, ")");
    if (atIdent("by") || atIdent("without")) {
      if (!grouping.isEmpty() || without) {
        throw error(peek(), "by/without can only be given once");
      }
      without = next().text().equals("without");
      grouping = labelList();
    }
    return new Aggregation(op, inner, grouping, without);
  }

  private Expr call(String name) {
    expect(Kind.LPAREN, "(");
    List<Expr> args = new ArrayList<>();
    if (!at(Kind.RPAREN)) {
      args.add(expression());
      while (at(Kind.COMMA)) {
        next();
        args.add(expression());
      }
    }
    expect(Kind.RPAREN, ")");
    return new Call(name, args);
  }

  private List<String> labelList() {
    expect(Kind.LPAREN, "(");
    List<String> labels = new ArrayList<>();
    while (!at(Kind.RPAREN)) {
      labels.add(expect(Kind.IDENT, "a label name").text());
      if (!at(Kind.COMMA)) {
        break;
      }
      next();
    }
    expect(Kind.RPAREN, ")");
    return labels;
  }

  private List<LabelMatcher> matchers(List<LabelMatcher> matchers) {
    expect(Kind.LBRACE, "{");
    while (!at(Kind.RBRACE)) {
      String label = expect(Kind.IDENT, "a label name").text();
      Token op = next();
      LabelMatcher.Type type =
          switch (op.kind()) {
            case ASSIGN -> LabelMatcher.Type.EQ;
            case NEQ -> LabelMatcher.Type.NEQ;
            case RE -> LabelMatcher.Type.RE;
            case NRE -> LabelMatcher.Type.NRE;
            default -> throw error(op, "expected =, !=, =~ or !~, got " + describe(op));
          };
      String value = expect(Kind.STRING, "a quoted label value").text();
      matchers.add(LabelMatcher.of(label, type, value));
      if (!at(Kind.COMMA)) {
        break;
      }
      next();
    }
    expect(Kind.RBRACE, "}");
    return matchers;
  }

  /** Like Prometheus: a selector must not match every series. */
  private List<LabelMatcher> checkSelector(Token at, List<LabelMatcher> matchers) {
    if (matchers.stream().allMatch(matcher -> matcher.matches(""))) {
      throw error(at, "vector selector must contain at least one non-empty matcher");
    }
    return List.copyOf(matchers);
  }

  // --- token helpers ---

  private Token peek() {
    return tokens.get(index);
  }

  private Token next() {
    Token token = tokens.get(index);
    if (token.kind() != Kind.EOF) {
      index++;
    }
    return token;
  }

  private boolean at(Kind... kinds) {
    Kind current = peek().kind();
    for (Kind kind : kinds) {
      if (current == kind) {
        return true;
      }
    }
    return false;
  }

  private boolean atIdent(String text) {
    return peek().kind() == Kind.IDENT && peek().text().equals(text);
  }

  private Token expect(Kind kind, String what) {
    if (!at(kind)) {
      throw error(peek(), "expected " + what + ", got " + describe(peek()));
    }
    return next();
  }

  private static String describe(Token token) {
    return token.kind() == Kind.EOF ? "end of query" : "\"" + token.text() + "\"";
  }

  private static PromQlException error(Token token, String message) {
    return new PromQlException("parse error at char " + (token.pos() + 1) + ": " + message);
  }

  // --- lexer ---

  private static final class Lexer {

    private final String input;
    private int pos;
    private final List<Token> tokens = new ArrayList<>();

    Lexer(String input) {
      this.input = input;
    }

    List<Token> tokens() {
      while (true) {
        skipSpaceAndComments();
        if (pos >= input.length()) {
          tokens.add(new Token(Kind.EOF, "", pos));
          return tokens;
        }
        tokens.add(token());
      }
    }

    private void skipSpaceAndComments() {
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isWhitespace(c)) {
          pos++;
        } else if (c == '#') {
          while (pos < input.length() && input.charAt(pos) != '\n') {
            pos++;
          }
        } else {
          return;
        }
      }
    }

    private Token token() {
      int start = pos;
      char c = input.charAt(pos);
      if (Character.isLetter(c) || c == '_' || c == ':') {
        while (pos < input.length() && isIdentPart(input.charAt(pos))) {
          pos++;
        }
        return new Token(Kind.IDENT, input.substring(start, pos), start);
      }
      if (Character.isDigit(c) || (c == '.' && pos + 1 < input.length() && isDigitAt(pos + 1))) {
        return number();
      }
      if (c == '"' || c == '\'' || c == '`') {
        return string(c);
      }
      String two = pos + 1 < input.length() ? input.substring(pos, pos + 2) : "";
      Kind twoKind =
          switch (two) {
            case "!=" -> Kind.NEQ;
            case "=~" -> Kind.RE;
            case "!~" -> Kind.NRE;
            case "==" -> Kind.EQL;
            case ">=" -> Kind.GTE;
            case "<=" -> Kind.LTE;
            default -> null;
          };
      if (twoKind != null) {
        pos += 2;
        return new Token(twoKind, two, start);
      }
      pos++;
      Kind kind =
          switch (c) {
            case '{' -> Kind.LBRACE;
            case '}' -> Kind.RBRACE;
            case '(' -> Kind.LPAREN;
            case ')' -> Kind.RPAREN;
            case '[' -> Kind.LBRACKET;
            case ']' -> Kind.RBRACKET;
            case ',' -> Kind.COMMA;
            case '=' -> Kind.ASSIGN;
            case '>' -> Kind.GTR;
            case '<' -> Kind.LSS;
            case '+' -> Kind.ADD;
            case '-' -> Kind.SUB;
            case '*' -> Kind.MUL;
            case '/' -> Kind.DIV;
            case '%' -> Kind.MOD;
            case '^' -> Kind.POW;
            case ':' -> Kind.COLON;
            default ->
                throw new PromQlException(
                    "parse error at char " + (start + 1) + ": unexpected character '" + c + "'");
          };
      return new Token(kind, String.valueOf(c), start);
    }

    /** A number like 1, 1.5 or 1e3, or a duration like 5m or 1h30m. */
    private Token number() {
      int start = pos;
      while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
        pos++;
      }
      boolean integer = true;
      if (pos < input.length() && input.charAt(pos) == '.') {
        integer = false;
        pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
          pos++;
        }
      }
      if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
        int after = pos + 1;
        if (after < input.length() && (input.charAt(after) == '+' || input.charAt(after) == '-')) {
          after++;
        }
        if (after < input.length() && Character.isDigit(input.charAt(after))) {
          integer = false;
          pos = after;
          while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
          }
        }
      }
      if (integer && pos < input.length() && Character.isLetter(input.charAt(pos))) {
        while (pos < input.length() && Character.isLetterOrDigit(input.charAt(pos))) {
          pos++;
        }
        return new Token(Kind.DURATION, input.substring(start, pos), start);
      }
      return new Token(Kind.NUMBER, input.substring(start, pos), start);
    }

    private Token string(char quote) {
      int start = pos;
      pos++;
      StringBuilder value = new StringBuilder();
      while (pos < input.length()) {
        char c = input.charAt(pos++);
        if (c == quote) {
          return new Token(Kind.STRING, value.toString(), start);
        }
        if (c == '\\' && quote != '`' && pos < input.length()) {
          char escaped = input.charAt(pos++);
          value.append(
              switch (escaped) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                default -> escaped;
              });
        } else {
          value.append(c);
        }
      }
      throw new PromQlException("parse error at char " + (start + 1) + ": unterminated string");
    }

    private boolean isDigitAt(int at) {
      return Character.isDigit(input.charAt(at));
    }

    private static boolean isIdentPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == ':';
    }
  }
}
