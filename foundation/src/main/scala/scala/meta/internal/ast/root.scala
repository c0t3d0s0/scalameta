package scala.meta
package internal
package ast

import scala.language.experimental.macros
import scala.annotation.StaticAnnotation
import scala.reflect.macros.whitebox.Context
import scala.collection.mutable.ListBuffer
import scala.meta.internal.ast.{Reflection => AstReflection}

class root extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro RootMacros.impl
}

class RootMacros(val c: Context) extends AstReflection {
  lazy val u: c.universe.type = c.universe
  lazy val mirror = c.mirror
  import c.universe._
  import Flag._
  lazy val Tree = tq"_root_.scala.meta.Tree"
  lazy val Datum = tq"_root_.scala.Any"
  lazy val Data = tq"_root_.scala.collection.immutable.Seq[$Datum]"
  lazy val Flags = tq"_root_.scala.meta.internal.flags.`package`.Flags"
  lazy val TYPECHECKED = q"_root_.scala.meta.internal.flags.`package`.TYPECHECKED"
  lazy val ZERO = q"_root_.scala.meta.internal.flags.`package`.ZERO"
  lazy val Tokens = tq"_root_.scala.meta.tokens.Tokens"
  lazy val Environment = tq"_root_.scala.meta.internal.semantic.Environment"
  lazy val Denotation = tq"_root_.scala.meta.internal.semantic.Denotation"
  lazy val Typing = tq"_root_.scala.meta.internal.semantic.Typing"
  lazy val Ffi = tq"_root_.scala.meta.internal.ffi.Ffi"
  lazy val AdtInternal = q"_root_.org.scalameta.adt.Internal"
  lazy val AstInternal = q"_root_.scala.meta.internal.ast.internal"
  lazy val SemanticInternal = q"_root_.scala.meta.internal.semantic"
  lazy val FfiInternal = q"_root_.scala.meta.internal.ffi"
  lazy val ArrayClassMethod = q"_root_.scala.meta.internal.ast.Helpers.arrayClass"
  def impl(annottees: Tree*): Tree = {
    def transform(cdef: ClassDef, mdef: ModuleDef): List[ImplDef] = {
      val q"${mods @ Modifiers(flags, privateWithin, anns)} trait $name[..$tparams] extends { ..$earlydefns } with ..$parents { $self => ..$stats }" = cdef
      val q"$mmods object $mname extends { ..$mearlydefns } with ..$mparents { $mself => ..$mstats }" = mdef
      val stats1 = ListBuffer[Tree]() ++ stats
      val mstats1 = ListBuffer[Tree]() ++ mstats

      // NOTE: sealedness is turned off because we can't have @ast hierarchy sealed anymore
      // hopefully, in the future we'll find a way to restore sealedness
      if (mods.hasFlag(SEALED)) c.abort(cdef.pos, "@root traits cannot be sealed")
      if (mods.hasFlag(FINAL)) c.abort(cdef.pos, "@root traits cannot be final")
      val flags1 = flags // TODO: flags | SEALED
      val needsThisType = stats.collect{ case TypeDef(_, TypeName("ThisType"), _, _) => () }.isEmpty
      if (needsThisType) stats1 += q"type ThisType <: $name"
      stats1 += q"def privateTag: _root_.scala.Int"
      mstats1 += q"$AstInternal.hierarchyCheck[$name]"
      val anns1 = anns :+ q"new $AdtInternal.root" :+ q"new $AstInternal.root"
      val parents1 = parents :+ tq"$AstInternal.Ast" :+ tq"_root_.scala.Product" :+ tq"_root_.scala.Serializable"

      // TODO: think of better ways to hide this from the public API
      val q"..$infrastructure" = q"""
        // ======= INTERNAL APIS =======
        // Can't be called by regular users, can be called by hosts.

        // private[meta] def env: Environment // Generated by @branch and @ast only for subclasses of Term and Name
        private[meta] def internalEnv: _root_.scala.Option[$Environment] = this match {
          case tree: _root_.scala.meta.Term => _root_.scala.Some(tree.env)
          case tree: _root_.scala.meta.Name => _root_.scala.Some(tree.env)
          case _ => _root_.scala.None
        }
        // private[meta] def withEnv(env: Environment): ThisType // Generated by @branch and @ast only for subclasses of Term and Name

        // private[meta] def denot: Denotation // Generated by @branch and @ast only for subclasses of Name
        private[meta] def internalDenot: _root_.scala.Option[$Denotation] = this match {
          case tree: _root_.scala.meta.Name => _root_.scala.Some(tree.denot)
          case _ => _root_.scala.None
        }
        // private[meta] def typing: Typing // Generated by @branch and @ast only for subclasses of Term and Term.Param
        private[meta] def internalTyping: _root_.scala.Option[$Typing] = this match {
          case tree: _root_.scala.meta.Term => _root_.scala.Some(tree.typing)
          case tree: _root_.scala.meta.Term.Param => _root_.scala.Some(tree.typing)
          case _ => _root_.scala.None
        }
        // private[meta] def withAttrs(denot: Denotation, typing: Typing): ThisType // Generated by @branch and @ast only for subclasses of Name, Term, Term.Param
        private[meta] def inheritAttrs(tree: Tree): ThisType = {
          val helper = scala.meta.internal.semantic.`package`.XtensionAttributedTree(this)
          helper.inheritAttrs(tree.asInstanceOf[_root_.scala.meta.Tree]).asInstanceOf[ThisType]
        }

        // ======= PRIVATE APIS =======
        // Can't be called by regular users, must not be called by hosts.
        // Only available to the scala.meta tree framework.

        protected def privateFlags: $Flags
        protected def privateWithFlags(flags: $Flags): ThisType
        protected def privatePrototype: ThisType
        protected def privateParent: $Tree
        protected def privateTokens: $Tokens
        protected def privateEnv: $Environment
        protected def privateDenot: $Denotation
        protected def privateTyping: $Typing
        protected def privateFfi: $Ffi
        private[meta] def privateCopy(
          flags: $Flags = $ZERO,
          prototype: $Tree = this,
          parent: $Tree = privateParent,
          tokens: $Tokens = privateTokens,
          env: $Environment = privateEnv,
          denot: $Denotation = privateDenot,
          typing: $Typing = privateTyping,
          ffi: $Ffi = privateFfi): ThisType

        protected def isEnvEmpty: _root_.scala.Boolean = this.privateEnv == null || this.privateEnv == _root_.scala.meta.internal.semantic.Environment.Zero
        protected def isDenotEmpty: _root_.scala.Boolean = this.privateDenot == null || this.privateDenot == _root_.scala.meta.internal.semantic.Denotation.Zero
        protected def isTypingEmpty: _root_.scala.Boolean = this.privateTyping == null || this.privateTyping == _root_.scala.meta.internal.semantic.Denotation.Zero
        private[meta] def isUnattributed: _root_.scala.Boolean = this match {
          case tree: Term.Name => isEnvEmpty && isDenotEmpty && isTypingEmpty
          case tree: Ctor.Name => isEnvEmpty && isDenotEmpty && isTypingEmpty
          case tree: Term.Param => isTypingEmpty
          case tree: Term => isEnvEmpty && isTypingEmpty
          case tree: Name => isEnvEmpty && isDenotEmpty
          case _ => true
        }
        private[meta] def isPartiallyAttributed: _root_.scala.Boolean = this match {
          case tree: Term.Name => !isUnattributed && !isAttributed
          case tree: Ctor.Name => !isUnattributed && !isAttributed
          case tree: Term.Param => !isUnattributed && !isAttributed
          case tree: Term => !isUnattributed && !isAttributed
          case tree: Name => !isUnattributed && !isAttributed
          case _ => false
        }
        private[meta] def isAttributed: _root_.scala.Boolean = this.isTypechecked
      """
      stats1 ++= infrastructure

      // TODO: This is a really weird workaround for a compiler crash.
      // If I put xxxTypechecked methods as instance methods on Tree (by adding them to stats1),
      // then compilation of SyntheticSuite.scala in tests crashes with a StackOverflowError.
      // If I had a bit more time, I'd debug this, but Scala World is really close.
      mstats1 += q"""
        implicit class XtensionTypecheckableTree[T <: _root_.scala.meta.Tree](tree: T) {
          private[meta] def isTypechecked: Boolean = (tree.privateFlags & $TYPECHECKED) == $TYPECHECKED
          private[meta] def setTypechecked: T = tree.privateWithFlags(tree.privateFlags | $TYPECHECKED).asInstanceOf[T]
          private[meta] def resetTypechecked: T = tree.privateWithFlags(tree.privateFlags & ~$TYPECHECKED).asInstanceOf[T]
          private[meta] def withTypechecked(value: _root_.scala.Boolean): T = if (value) tree.setTypechecked else tree.resetTypechecked
        }
      """

      val qmods = Modifiers(NoFlags, TypeName("meta"), List(q"new _root_.scala.meta.internal.ast.ast"))
      val qname = TypeName("Quasi")
      val qparents = List(tq"_root_.scala.meta.internal.ast.Quasi")
      var qstats = List(q"def pt: _root_.java.lang.Class[_] = $ArrayClassMethod(_root_.scala.Predef.classOf[$name], this.rank)")
      qstats :+= q"protected def privateEnv: $Environment = null"
      qstats :+= q"protected def privateDenot: $Denotation = null"
      qstats :+= q"protected def privateTyping: $Typing = null"
      qstats :+= q"protected def privateFfi: $Ffi = null"
      mstats1 += q"$qmods class $qname(rank: _root_.scala.Int, tree: _root_.scala.Any) extends ..$qparents { ..$qstats }"

      val cdef1 = q"${Modifiers(flags1, privateWithin, anns1)} trait $name[..$tparams] extends { ..$earlydefns } with ..$parents1 { $self => ..$stats1 }"
      val mdef1 = q"$mmods object $mname extends { ..$mearlydefns } with ..$mparents { $mself => ..$mstats1 }"
      List(cdef1, mdef1)
    }
    val expanded = annottees match {
      case (cdef @ ClassDef(mods, _, _, _)) :: (mdef: ModuleDef) :: rest if mods.hasFlag(TRAIT) => transform(cdef, mdef) ++ rest
      case (cdef @ ClassDef(mods, _, _, _)) :: rest if mods.hasFlag(TRAIT) => transform(cdef, q"object ${cdef.name.toTermName}") ++ rest
      case annottee :: rest => c.abort(annottee.pos, "only traits can be @root")
    }
    q"{ ..$expanded; () }"
  }
}