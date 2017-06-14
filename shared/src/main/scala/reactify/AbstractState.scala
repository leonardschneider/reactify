package reactify

import java.util.concurrent.atomic.AtomicReference

abstract class AbstractState[T] private(distinct: Boolean,
                                        cache: Boolean,
                                        retainRecursion: Boolean) extends State[T] {
  private var lastValue: T = _
//  private val function = new AtomicReference[() => T]
//  private val previous = new AtomicReference[Option[PreviousFunction[T]]](None)
  val instance = new AtomicReference[List[StateInstance[T]]](Nil)
  private val monitoring = new AtomicReference[Set[Observable[_]]](Set.empty)

//  private val replacement = new ThreadLocal[Option[PreviousFunction[T]]] {
//    override def initialValue(): Option[PreviousFunction[T]] = None
//  }

  private val stack = new ThreadLocal[List[StateInstance[T]]] {
    override def initialValue(): List[StateInstance[T]] = instance.get()
  }

  private val monitor: Listener[Any] = (_: Any) => {
    if (instance.get().nonEmpty) {
      replace(instance.get().head.function, newFunction = false)
    } else {
      println("*** Ignoring empty!")
    }
  }

  private def updateValue(value: T): Unit = {
    if (!distinct || value != lastValue) {
      lastValue = value
      fire(value)
    }
  }

  def this(function: () => T,
           distinct: Boolean = true,
           cache: Boolean = true,
           retainRecursion: Boolean = false) = {
    this(distinct, cache, retainRecursion)
    replace(function, newFunction = true)
  }

  override def observing: Set[Observable[_]] = monitoring.get()

  override def get: T = get(cache)

  def get(cache: Boolean): T = {
    AbstractState.reference(this)
    if (cache) {
      lastValue
    } else {
      val originalStack = stack.get()
      val instance = originalStack.headOption.getOrElse(throw new RuntimeException("Reached top of stack!"))
      stack.set(originalStack.tail)
      try {
        instance.state.getOrElse(instance.function())
      } finally {
        stack.set(originalStack)
      }
    }
  }

  /*def get(cache: Boolean): T = replacement.get() match {
    case Some(p) => {
      replacement.set(p.previous)
      p.function()
    }
    case None => {
      AbstractState.reference(this)
      replacement.set(previous.get())
      try {
        if (cache) {
          lastValue
        } else {
          function.get()()
        }
      } finally {
        replacement.set(None)
      }
    }
  }*/

  protected def set(value: => T): Unit = synchronized {
    replace(() => value, newFunction = true)
  }

  protected def replace(function: () => T, newFunction: Boolean): Unit = synchronized {
    val instance = new StateInstance[T](None, function)
    if (newFunction) {
      if (retainRecursion || this.instance.get().isEmpty) {
        this.instance.set(instance :: this.instance.get())
      } else {
        val previous = new StateInstance(Some(lastValue), this.instance.get().head.function)
        this.instance.set(List(instance, previous))
      }
    }
    this.stack.remove()

//    if (newFunction) {
//      val p = Option(this.function.get()).map { f =>
//        new PreviousFunction[T](f, previous.get())
//      }.getOrElse(new PreviousFunction[T](() => lastValue, previous.get()))
//      previous.set(Some(p))
//    }
    val previousObservables = AbstractState.observables.get()
    AbstractState.observables.set(Set.empty)
    try {
      val value: T = get(cache = false)

      val oldObservables = observing
      var newObservables = AbstractState.observables.get()
      if (newFunction && !newObservables.contains(this)) {
        // No recursive reference, we can clear previous
        this.stack.set(List(instance))
      }
      newObservables -= this

      if (oldObservables != newObservables) {
        // Out with the old
        oldObservables.foreach { ob =>
          if (!newObservables.contains(ob)) {
            ob.asInstanceOf[Observable[Any]].detach(monitor)
          }
        }

        // In with the new
        newObservables.foreach { ob =>
          if (!oldObservables.contains(ob)) {
            ob.asInstanceOf[Observable[Any]].observe(monitor)
          }
        }

        monitoring.set(newObservables)
      }
      updateValue(value)
    } finally {
      AbstractState.observables.set(previousObservables)
    }
  }

  /**
    * Convenience method to pre-evaluate the value instead of as an anonymous function.
    *
    * @param value the value to be set
    */
  protected def setStatic(value: T): Unit = synchronized {
    val v: T = value
    replace(() => v, newFunction = true)
  }

  override def dispose(): Unit = synchronized {
    super.dispose()

    monitoring.get().foreach { ob =>
      ob.asInstanceOf[Observable[Any]].detach(monitor)
    }
    monitoring.set(Set.empty)
  }
}

object AbstractState {
  private val observables = new ThreadLocal[Set[Observable[_]]]

  def reference(observable: Observable[_]): Unit = Option(observables.get()) match {
    case Some(obs) => observables.set(obs + observable)
    case None => // Nothing being updated
  }
}