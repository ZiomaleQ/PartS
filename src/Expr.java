import java.util.List;

public abstract class Expr {
 public interface Visitor<R> {
    R visitAssignExpr(Assign expr);
    R visitBinaryExpr(Binary expr);
    R visitCallExpr(Call expr);
    R visitGetExpr(Get expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitSetExpr(Set expr);
    R visitSuperExpr(Super expr);
    R visitThisExpr(This expr);
    R visitUnaryExpr(Unary expr);
    R visitVariableExpr(Variable expr);
  }
 public static class Assign extends Expr {
    Assign(Token name, Expr value) {
      this.name = name;
      this.value = value;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitAssignExpr(this);
    }

   public final Token name;
   public final Expr value;
  }
 public static class Binary extends Expr {
    Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }

   public final Expr left;
   public final Token operator;
   public final Expr right;
  }
 public static class Call extends Expr {
    Call(Expr callee, List<Expr> arguments) {
      this.callee = callee;
      this.arguments = arguments;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitCallExpr(this);
    }

   public final Expr callee;
   public final List<Expr> arguments;
  }
 public static class Get extends Expr {
    Get(Expr object, Token name) {
      this.object = object;
      this.name = name;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGetExpr(this);
    }

   public final Expr object;
   public final Token name;
  }
 public static class Grouping extends Expr {
    Grouping(Expr expression) {
      this.expression = expression;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }

   public final Expr expression;
  }
 public static class Literal extends Expr {
    Literal(Object value) {
      this.value = value;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }

   public final Object value;
  }
 public static class Set extends Expr {
    Set(Expr object, Token name, Expr value) {
      this.object = object;
      this.name = name;
      this.value = value;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSetExpr(this);
    }

   public final Expr object;
   public final Token name;
   public final Expr value;
  }
 public static class Super extends Expr {
    Super(Token keyword, Token method) {
      this.keyword = keyword;
      this.method = method;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSuperExpr(this);
    }

   public final Token keyword;
   public final Token method;
  }
 public static class This extends Expr {
    This(Token keyword) {
      this.keyword = keyword;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitThisExpr(this);
    }

   public final Token keyword;
  }
 public static class Unary extends Expr {
    Unary(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }

   public final Token operator;
   public final Expr right;
  }
 public static class Variable extends Expr {
    Variable(Token name) {
      this.name = name;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitVariableExpr(this);
    }

   public final Token name;
  }

 public abstract <R> R accept(Visitor<R> visitor);
}
