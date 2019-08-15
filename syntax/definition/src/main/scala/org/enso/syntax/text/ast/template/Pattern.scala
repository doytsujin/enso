package org.enso.syntax.text.ast.template

import org.enso.data.Shifted
import org.enso.syntax.text.AST
import org.enso.syntax.text.AST.SAST
import org.enso.syntax.text.ast.Repr
import scala.reflect.ClassTag

/////////////////
//// Pattern ////
/////////////////

sealed trait Pattern {
  import Pattern._
  def >>(that: Pattern): Seq  = Seq(this, that)
  def |(that: Pattern):  Or   = Or(this, that)
  def many:              Many = Many(this)
}
object Pattern {
  sealed trait Of[T]                        extends Pattern
  case class Nothing()                      extends Of[Unit]
  case class Tok(tok: AST)                  extends Of[SAST]
  case class Many(pat: Pattern)             extends Of[List[Match_]]
  case class Seq(p1: Pattern, p2: Pattern)  extends Of[(Match_, Match_)]
  case class Build(pat: Pattern)            extends Of[SAST]
  case class Not(pat: Pattern)              extends Of[Unit]
  case class Or(p1: Pattern, p2: Pattern)   extends Of[Match_]
  case class End()                          extends Of[Unit]
  case class Tag(tag: String, pat: Pattern) extends Of[Match_]
  case class Err(msg: String, pat: Pattern) extends Of[SAST]
  case class Cls[T <: AST]()(implicit val tag: ClassTag[T])
      extends Of[Shifted[T]]

  object Seq {
    def apply(p1: Pattern, p2: Pattern, ps: Pattern*): Pattern =
      ps.headOption match {
        case None     => Seq(p1, p2)
        case Some(p3) => Seq(Seq(p1, p2), p3, ps.tail: _*)
      }
  }

  //// Conversions ////

  implicit def fromAST(ast: AST): Pattern = Tok(ast)

  //// Smart Constructors ////

  object Opt {
    def apply(pat: Pattern) = pat | Nothing()
    def unapply(t: Pattern): Option[Pattern] = t match {
      case Or(pat, Nothing()) => Some(pat)
      case _                  => None
    }
  }

  object Any {
    def apply(): Pattern = Cls[AST]()
    def unapply(t: Pattern)(implicit astCls: ClassTag[AST]): Boolean =
      t match {
        case t @ Cls() => t.tag == astCls
        case _         => false
      }
  }

  object NotThen {
    def apply(not: Pattern, pat: Pattern): Pattern = Not(not) >> pat
    def unapply(t: Pattern): Option[(Pattern, Pattern)] = t match {
      case Seq(Not(p1), p2) => Some((p1, p2))
      case _                => None
    }
  }

  object AnyBut {
    def apply(pat: Pattern): Pattern = NotThen(pat, Any())
    def unapply(t: Pattern): Option[Pattern] = t match {
      case NotThen(pat, Any()) => Some(pat)
      case _                   => None
    }
  }

  // FIXME: check unapply
  object Many1 {
    def apply(pat: Pattern): Seq = Seq(pat, Many(pat))
    def unapply(p: Seq): Option[Pattern] = p match {
      case Seq(p1, Many(p2)) => if (p == p1) Some(p) else None
    }
  }

  object AnyTill {
    def apply(pat: Pattern): Many = Many(AnyBut(pat))
  }

  object Expr {
    def apply() = Build(Many1(Any()))
    def unapply(t: Pattern): Boolean = t match {
      case Build(Many1(Any())) => true
      case _                   => false
    }
  }

  object SepList {
    def apply(pat: Pattern, div: Pattern): Seq = pat >> (div >> pat).many
    def apply(pat: Pattern, div: Pattern, err: String): Seq = {
      val seg = pat | Err(err, AnyTill(div))
      SepList(seg, div)
    }
  }

  object RestOfStream {
    def apply(): Many = Many(Any())
  }

  ///////////////
  //// Match ////
  ///////////////

  type Match_ = Match[_]
  case class Match[T: Repr.Of](pat: Of[T], el: T) extends Repr.Provider {
    val repr = Repr.of(el)

    def toStream: AST.Stream = this match {
      case Match.Nothing() => List()
      case Match.Tok(t)    => List(t)
      case Match.Many(t)   => t.flatMap(_.toStream)
      case Match.Seq(l, r) => l.toStream ++ r.toStream
      case Match.Build(t)  => List(t)
      case Match.Not()     => List()
      case Match.Or(t)     => t.toStream
      case Match.Cls(t)    => List(t)
      case Match.Tag(t)    => t.toStream
      case Match.Err(t)    => List(t)
      case Match.End()     => List()
    }

    def isValid: Boolean = this match {
      case Match.Nothing() => true
      case Match.Tok(_)    => true
      case Match.Many(t)   => t.forall(_.isValid)
      case Match.Seq(l, r) => l.isValid && r.isValid
      case Match.Build(_)  => true
      case Match.Not()     => true
      case Match.Or(t)     => t.isValid
      case Match.Cls(_)    => true
      case Match.Tag(t)    => t.isValid
      case Match.Err(_)    => false
      case Match.End()     => true
    }
  }

  object Match {
    object Nothing {
      def apply() = Match(Pattern.Nothing(), ())
      def unapply(t: Match_): Boolean = t match {
        case Match(_: Pattern.Nothing, t) => true
        case _                            => false
      }
    }

    //// Smart Constructors ////

    object Seq {
      def unapply(t: Match_): Option[(Match_, Match_)] = t match {
        case Match(_: Pattern.Seq, t) => Some(t)
        case _                        => None
      }
    }

    object Many {
      def unapply(t: Match_): Option[List[Match_]] = t match {
        case Match(_: Pattern.Many, t) => Some(t)
        case _                         => None
      }
    }

    object Tok {
      def unapply(t: Match_): Option[SAST] = t match {
        case Match(_: Pattern.Tok, t) => Some(t)
        case _                        => None
      }
    }

    object Build {
      def unapply(t: Match_): Option[SAST] = t match {
        case Match(_: Pattern.Build, t) => Some(t)
        case _                          => None
      }
    }

    object Not {
      def unapply(t: Match_): Boolean = t match {
        case Match(_: Pattern.Not, t) => true
        case _                        => false
      }
    }

    object Or {
      def unapply(t: Match_): Option[Match_] = t match {
        case Match(_: Pattern.Or, t) => Some(t)
        case _                       => None
      }
    }

    object Cls {
      def unapply(t: Match_): Option[SAST] = t match {
        case Match(_: Pattern.Cls[_], t) => Some(t)
        case _                           => None
      }
    }

    object Tag {
      def unapply(t: Match_): Option[Match_] = t match {
        case Match(_: Pattern.Tag, t) => Some(t)
        case _                        => None
      }
    }

    object Err {
      def unapply(t: Match_): Option[SAST] = t match {
        case Match(_: Pattern.Err, t) => Some(t)
        case _                        => None
      }
    }

    object End {
      def unapply(t: Match_): Boolean = t match {
        case Match(_: Pattern.End, _) => true
        case _                        => false
      }
    }

  }
}