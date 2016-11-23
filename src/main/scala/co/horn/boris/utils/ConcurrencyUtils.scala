package co.horn.boris.utils

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

class Ref[T](initial: T) {
  def get =
    instance.get()

  def set(update: T) =
    instance.set(update)

  def compareAndSet(expect: T, update: T) =
    instance.compareAndSet(expect, update)

  @tailrec
  final def transformAndGet(cb: T => T): T = {
    val oldValue = get
    val newValue = cb(oldValue)

    if (!compareAndSet(oldValue, newValue))
    // tail-recursive call
      transformAndGet(cb)
    else
      newValue
  }

  private[this] val instance = new AtomicReference(initial)
}

object Ref {
  def apply[T](initial: T) = new Ref(initial)
}