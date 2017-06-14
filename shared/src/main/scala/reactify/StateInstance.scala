package reactify

class StateInstance[T](val state: Option[T], val function: () => T)