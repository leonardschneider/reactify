package reactify

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Observable, as the name suggests, observes values being fired against it. This is the core functionality of Reactify
  * and provides the infrastructure used by Channel, Val, Var, Prop, and Dep.
  *
  * @tparam T the type of value this Observable will receive
  */
trait Observable[T] {
  private[reactify] var observers = List.empty[Listener[T]]

  /**
    * Attaches a function to listen to values fired against this Observable.
    *
    * @param f function listener
    * @return the supplied function. This reference is useful for detaching the function later
    */
  def attach(f: T => Unit,
             priority: Double = Listener.Priority.Normal): Listener[T] = {
    observe(Listener[T](f, priority))
  }

  /**
    * Direct attachment of a listener.
    *
    * @param listener the listener to attach
    * @return the same listener supplied
    */
  def observe(listener: Listener[T]): Listener[T] = synchronized {
    observers = (observers ::: List(listener)).sorted
    listener
  }

  /**
    * Works like `attach`, but doesn't receive the fired value.
    *
    * @param f function to invoke on fire
    * @return listener
    */
  def on(f: => Unit, priority: Double = Listener.Priority.Normal): Listener[T] = attach(_ => f, priority)

  /**
    * Detaches a function from listening to this Observable.
    *
    * @param listener function listener that was previously attached
    */
  def detach(listener: Listener[T]): Unit = synchronized {
    observers = observers.filterNot(_ eq listener)
  }

  /**
    * Invokes the listener only one time and then detaches itself. If supplied, the condition filters the scenarios in
    * which the listener will be invoked.
    *
    * @param f the function listener
    * @param condition the condition under which the listener will be invoked. Defaults to always return true.
    */
  def once(f: T => Unit,
           condition: T => Boolean = (_: T) => true,
           priority: Double = Listener.Priority.Normal): Listener[T] = {
    var listener: Listener[T] = null
    listener = Listener[T]((value: T) => if (condition(value)) {
      detach(listener)
      f(value)
    }, priority)
    observe(listener)
  }

  /**
    * Returns a Future[T] that represents the value of the next firing of this Observable.
    *
    * @param condition the condition under which the listener will be invoked. Defaults to always return true.
    */
  def future(condition: T => Boolean = (t: T) => true): Future[T] = {
    val promise = Promise[T]
    once(promise.success, condition)
    promise.future
  }

  /**
    * Works similarly to `attach`, but also references the previous value that was fired. This is useful when you need
    * to handle changes, not just new values.
    *
    * @param listener the ChangeListener
    * @return the listener attached. This can be passed to `detach` to remove this listener
    */
  def changes(listener: ChangeListener[T]): Listener[T] = attach(ChangeListener.createFunction(listener, None))

  /**
    * Maps the Observable to another type.
    *
    * @param f function to handle the mapping from T to R
    * @tparam R the type of the new Observable
    * @return Observable[R]
    */
  def map[R](f: T => R): Observable[R] = {
    val channel = Channel[R]
    attach(t => channel := f(t))
    channel
  }

  def collect[R](f: PartialFunction[T, R]): Observable[R] = {
    val channel = Channel[R]
    val lifted = f.lift
    attach { t =>
      lifted(t).foreach(v => channel.set(v))
    }
    channel
  }

  protected[reactify] def fire(value: T, `type`: InvocationType): Unit = Invocation().wrap {
    fireRecursive(value, `type`, Invocation(), observers)
  }

  final protected def fireRecursive(value: T, `type`: InvocationType, invocation: Invocation, observers: List[Listener[T]]): Unit = {
    if (!invocation.isStopped) {
      observers.headOption.foreach { listener =>
        listener(value, `type`)
        fireRecursive(value, `type`, invocation, observers.tail)
      }
    }
  }

  /**
    * Clears all attached observers from this Observable.
    */
  def clear(): Unit = synchronized {
    observers = List.empty
  }

  /**
    * Cleans up all cross references in preparation for releasing for GC.
    */
  def dispose(): Unit = {
    clear()
  }

  def and(that: Observable[T]): Observable[T] = Observable.wrap(this, that)
}

object Observable {
  def wrap[T](observables: Observable[T]*): Observable[T] = new WrappedObservable[T](observables.toList)
  def apply[T](init: (T => Unit) => Unit): Observable[T] = new Observable[T] {
    init(fire(_, InvocationType.Direct))
  }
  def apply[T](future: Future[T]): Observable[T] = apply(fire => future.foreach(fire))
}