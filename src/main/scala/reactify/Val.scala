package reactify

import reactify.instance.RecursionMode

/**
  * Val, as the name suggests, is like a Scala `val`. This represents an immutable value that is set in the first place
  * and then not modified. However, since the value set may be built from `Observables`, the generated value may change
  * over time as its dependencies are modified. This class is `Observable` and will only fire changes if the underlying
  * value is derived from one or more `Observables`.
  *
  * @tparam T the type of value retained by this `State`
  */
class Val[T](function: () => T,
             distinct: Boolean = true,
             cache: Boolean = true,
             recursion: RecursionMode = RecursionMode.RetainPreviousValue,
             transactional: Boolean = true,
             onUpdate: Boolean = false) extends AbstractState[T](distinct, cache, recursion, transactional, onUpdate) {
  set(function())

  override def map[R](f: (T) => R): Val[R] = Val(f(this()))

  override def toString: String = s"Val($get)"
}

object Val {
  /**
    * Creates a new instance of a `Val[T]`
    */
  def apply[T](value: => T,
               static: Boolean = false,
               distinct: Boolean = true,
               cache: Boolean = true,
               recursion: RecursionMode = RecursionMode.RetainPreviousValue,
               transactional: Boolean = true): Val[T] = {
    val f = if (static) {
      val v: T = value
      () => v
    } else {
      () => value
    }
    new Val[T](f, distinct, cache, recursion, transactional)
  }

  /**
    * Creates a new instance of `Var` mixing in `DirtyState`.
    */
  def dirty[T](value: => T,
               static: Boolean = false,
               distinct: Boolean = true,
               cache: Boolean = true,
               recursion: RecursionMode = RecursionMode.RetainPreviousValue,
               transactional: Boolean = true): Val[T] with DirtyState[T] = {
    val f = if (static) {
      val v: T = value
      () => v
    } else {
      () => value
    }
    new Val[T](f, distinct, cache, recursion, transactional, onUpdate = true) with DirtyState[T]
  }
}