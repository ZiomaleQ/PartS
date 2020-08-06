import java.util.List;

public abstract class Stmt {
 public interface Visitor<R> {
    R visitBlockStmt(Block stmt);
    R visitClassStmt(Class stmt);
    R visitExpressionStmt(Expression stmt);
    R visitFunctionStmt(Function stmt);
    R visitIfStmt(If stmt);
    R visitReturnStmt(Return stmt);
    R visitWhileStmt(While stmt);
    R visitLetStmt(Let stmt);
  }
 public static class Block extends Stmt {
    Block(List<Stmt> statements) {
      this.statements = statements;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockStmt(this);
    }

   public final List<Stmt> statements;
  }
 public static class Class extends Stmt {
    Class(Token name, Expr.Variable superclass, List<Stmt.Function> methods) {
      this.name = name;
      this.superclass = superclass;
      this.methods = methods;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitClassStmt(this);
    }

   public final Token name;
   public final Expr.Variable superclass;
   public final List<Stmt.Function> methods;
  }
 public static class Expression extends Stmt {
    Expression(Expr expression) {
      this.expression = expression;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

   public final Expr expression;
  }
 public static class Function extends Stmt {
    Function(Token name, List<Token> params, List<Stmt> body) {
      this.name = name;
      this.params = params;
      this.body = body;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionStmt(this);
    }

   public final Token name;
   public final List<Token> params;
   public final List<Stmt> body;
  }
 public static class If extends Stmt {
    If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfStmt(this);
    }

   public final Expr condition;
   public final Stmt thenBranch;
   public final Stmt elseBranch;
  }
 public static class Return extends Stmt {
    Return(Token keyword, Expr value) {
      this.keyword = keyword;
      this.value = value;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnStmt(this);
    }

   public final Token keyword;
   public final Expr value;
  }
 public static class While extends Stmt {
    While(Expr condition, Stmt body, Boolean loop) {
      this.condition = condition;
      this.body = body;
      this.loop = loop;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitWhileStmt(this);
    }

   public final Expr condition;
   public final Stmt body;
   public final Boolean loop;
  }
 public static class Let extends Stmt {
    Let(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

   public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLetStmt(this);
    }

   public final Token name;
   public final Expr initializer;
  }

 public abstract <R> R accept(Visitor<R> visitor);
}
