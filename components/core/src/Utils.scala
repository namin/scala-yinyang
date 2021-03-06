package ch.epfl.yinyang

import ch.epfl.yinyang._
import ch.epfl.yinyang.transformers._
import scala.reflect.macros.blackbox.Context
import language.experimental.macros
import scala.util.matching.Regex

trait MacroModule {
  type Ctx <: Context
  val c: Ctx
}

trait DataDefs extends MacroModule {
  import c.universe._
  case class DSLFeature(tpe: Option[Type], name: String, targs: List[Tree], args: List[List[Type]])
}

/**
 * Common utilities for the Yin-Yang project.
 */
trait TransformationUtils extends MacroModule {
  import c.universe._
  import internal.decorators._

  /* These two should be unified */
  def method(recOpt: Option[Tree], methName: String, args: List[List[Tree]], targs: List[Tree] = Nil): Tree = {
    val calleeName = TermName(methName)
    val callee = recOpt match {
      case Some(rec) => Select(rec, calleeName)
      case None      => Ident(calleeName)
    }
    val calleeAndTargs: Tree = typeApply(targs)(callee)
    args.foldLeft(calleeAndTargs) { Apply(_, _) }
  }

  private[yinyang] def symbolId(symbol: Symbol): Int =
    symbol.asInstanceOf[scala.reflect.internal.Symbols#Symbol].id

  private[yinyang] def symbolId(tree: Tree): Int = symbolId(tree.symbol)

  def typeApply(targs: List[Tree])(select: Tree) = if (targs.nonEmpty)
    TypeApply(select, targs)
  else
    select

  def makeConstructor(classname: String, arguments: List[Tree]): Tree =
    Apply(Select(newClass(classname), termNames.CONSTRUCTOR), arguments)

  def newClass(classname: String) =
    New(Ident(TypeName(classname)))

  def copy(orig: Tree)(nev: Tree): Tree = {
    nev.setSymbol(orig.symbol)
    nev.setPos(orig.pos)
    nev
  }

  def deepDealias(tpe: Type): Type = {
    val dealiased = tpe.dealias.map(_.dealias)
    if (dealiased == tpe) tpe
    else deepDealias(dealiased)
  }

  def log(s: => String, level: Int = 0) = if (debugLevel > level) println(s)

  def debugLevel: Int

  // TODO (VJ) Removed UnstageBlock from all transformers as these unrelated features.
  object UnstageBlock {
    def unapply(tree: Tree): Option[Tree] = tree match {
      case q"unstage($body)" => Some(body)
      case q"ch.epfl.yinyang.api.`package`.unstage[$t]($body)" => Some(body)
      case _ => None
    }
  }

  /*
   * Utility methods for logging.
   */
  def className: String = ???
  lazy val typeRegex = new Regex("(" + className.replace("$", "\\$") + """\.this\.)(\w*)""")
  lazy val typetagRegex = new Regex("""(scala\.reflect\.runtime\.[a-zA-Z`]*\.universe\.typeTag\[)(\w*)\]""")
  def code(tree: Tree, shortenDSLNames: Boolean = true): String = {
    var short = showCode(tree)
    if (shortenDSLNames) {
      typeRegex findAllIn short foreach { m =>
        val typeRegex(start, typ) = m
        short = short.replace(start + typ, typ.toUpperCase())
      }
      typetagRegex findAllIn short foreach { m =>
        val typetagRegex(start, typ) = m
        short = short.replace(start + typ + "]", "TYPETAG[" + typ.toUpperCase() + "]")
      }
    }
    short
  }
}
